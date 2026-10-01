package fan.superai.v13

import fan.superai.engine.EngineConfig
import fan.superai.engine.FanEngine
import fan.superai.engine.PythonBridge
import fan.superai.v13.db.PredictionRecordEntity
import fan.superai.v13.db.ReplayStateEntity
import fan.superai.v13.db.V13Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.zip.CRC32

class V13Status(val phase: String = "", val progress: Float = 0f, val message: String = "", val error: String? = null)

/** Araştırma (RESEARCH/REPLAY) modu sonucu. Canlı duruma ASLA yazılmaz. */
class ResearchOutcome(
    val result: ReplayResult, val summary: ReplaySummary, val counterfactual: List<Counterfactual.CfHistory>,
    val lastCounterfactual: List<CfResult>, val fingerprint: Long, val durationMs: Long, val insights: V13Insights
)

class V13Env(
    val engine: FanEngine, val bridge: PythonBridge?, val cfg: EngineConfig, val records: () -> List<FanRecord>
)

/**
 * v1.3 çalışma zamanı: Room kalıcılığı + FanBrain + Python/Kotlin girdilerinin toplanması.
 * [research] dışındaki tüm çağrılar motor iş parçacığından yapılır.
 */
class V13Runtime(private val store: V13Store, private val clock: () -> Long = { System.currentTimeMillis() / 1000 }) {
    val alphabet = Alphabet()
    @Volatile var brain = FanBrain(); private set

    private val _final = MutableStateFlow<FinalPrediction?>(null)
    val final: StateFlow<FinalPrediction?> = _final
    private val _insights = MutableStateFlow<V13Insights?>(null)
    val insights: StateFlow<V13Insights?> = _insights
    private val _status = MutableStateFlow(V13Status())
    val status: StateFlow<V13Status> = _status
    private val _research = MutableStateFlow<ResearchOutcome?>(null)
    val researchResult: StateFlow<ResearchOutcome?> = _research
    private val _researchBusy = MutableStateFlow<String?>(null)
    val researchBusy: StateFlow<String?> = _researchBusy
    @Volatile private var dirty = false

    class Plan(val kind: String, val brain: FanBrain?, val startAt: Int, val reason: String) {
        val needsPythonMembers: Boolean get() = kind != "RESUME"
    }

    val store0: V13Store get() = store

    // ------------------------------------------------------------------ açılış
    /** Kayıtlı durumu inceler: devam / kuyruk yakalama / tam replay. Python replay'inden ÖNCE çağrılır. */
    fun prepare(records: List<FanRecord>, forceFull: Boolean = false): Plan {
        try { store.syncRecords(records) } catch (e: Exception) { FanLog.event(FanLog.ERROR, "${ErrorCodes.DB} kayıtlar eşitlenemedi: ${e.message}") }
        if (forceFull) return Plan("FULL", null, 0, "manuel Full Replay")
        val parts = try { store.loadParts() } catch (e: Exception) {
            FanLog.event(FanLog.ERROR, "${ErrorCodes.DB} durum okunamadı: ${e.message}"); return Plan("FULL", null, 0, "veritabanı okunamadı")
        }
        if (parts.isEmpty()) return Plan("FULL", null, 0, "ilk kurulum")
        return try {
            val b = FanBrain()
            b.importParts(parts)
            val n = b.count
            val ok = n <= records.size && (n == 0 || (b.mem.lastRecordId == records[n - 1].recordId &&
                b.mem.sym(Axis.NUMBER, 1) == alphabet.idx(records[n - 1].number)))
            when {
                !ok -> Plan("FULL", null, 0, "kayıtlı durum veriyle uyuşmuyor")
                n == records.size -> Plan("RESUME", b, n, "durum güncel")
                else -> Plan("CATCHUP", b, n, "${records.size - n} yeni kayıt")
            }
        } catch (e: Exception) {
            FanLog.event(FanLog.ERROR, "${ErrorCodes.STATE_LOAD} ${e.message}")
            Plan("FULL", null, 0, "durum bozuk")
        }
    }

    private fun status(phase: String, p: Float, msg: String) { _status.value = V13Status(phase, p, msg) }

    /** Python replay + Kotlin yeniden kurma BİTTİKTEN sonra çağrılır. */
    fun finish(plan: Plan, env: V13Env, clearHistory: Boolean = false) {
        val records = env.records()
        try {
            val b = plan.brain ?: FanBrain()
            if (plan.kind == "FULL") {
                store.clearStates()
                if (clearHistory) store.clearPredictions() else store.clearReplayPredictions()
            }
            brain = b
            val data = syncPython(records, env.bridge, plan.startAt, plan.needsPythonMembers)
            if (plan.kind != "RESUME" && records.size > plan.startAt) {
                status("REPLAY", 0f, "${plan.kind}: ${records.size - plan.startAt} kayıt işleniyor…")
                val rows = ArrayList<PredictionRecordEntity>()
                val sink = object : ReplaySink {
                    override fun onStep(step: ReplayStep, pred: FinalPrediction, brain: FanBrain) { rows += store.entityFor(V13Store.REPLAY, pred, step.ev) }
                    override fun onFinish(result: ReplayResult, brain: FanBrain) {}
                }
                val t0 = System.currentTimeMillis()
                val res = ReplayEngine.run(records, b, env.cfg, data, startAt = plan.startAt, sink = sink,
                    progress = { i, n -> status("REPLAY", if (n == 0) 0f else i.toFloat() / n, "$i / $n") })
                store.db.runInTransaction {
                    store.appendPredictions(rows)
                    store.saveReplay(ReplayStateEntity(mode = plan.kind, startedAt = t0, finishedAt = System.currentTimeMillis(),
                        processed = res.processed, total = res.total, status = if (res.errors.isEmpty()) "OK" else "UYARI",
                        leakChecks = res.leakChecks, leakViolations = res.leakViolations,
                        summary = "${plan.reason}; tahmin=${rows.size}; python=${data != null}"))
                }
            }
            dirty = false
            if (b.open == null) predictLive(env) else persistAll()
            publish()
            _status.value = V13Status("READY", 1f, "v1.3 hazır (${plan.kind}: ${plan.reason})")
        } catch (e: Throwable) {
            fail("finish", e)
        }
    }

    private fun syncPython(records: List<FanRecord>, bridge: PythonBridge?, startAt: Int, needData: Boolean): PythonReplayData? {
        if (bridge == null) return null
        val n = records.size
        val vals = IntArray(n) { alphabet.idx(records[it].number) }
        val times = LongArray(n) { records[it].timestamp }
        val bs = IntArray(n) { alphabet.bsOf(records[it].number) }
        val oe = IntArray(n) { alphabet.oeOf(records[it].number) }
        var side: PySideCapture? = try { bridge.sideSync(bs, oe, times) } catch (e: Throwable) {
            FanLog.event(FanLog.ERROR, "${ErrorCodes.PYTHON} yan meclis: ${e.message}"); null
        }
        if (!needData) return null
        try {
            var cap = bridge.lastCapture
            if (cap == null || cap.start > startAt || cap.start + cap.steps.size < n) cap = bridge.sandboxReplay(vals, times)
            if (side != null && side.start > startAt) side = bridge.sideSync(bs, oe, times, sandbox = true)
            val names = try { bridge.memberInfo()?.let { it.ids.zip(it.names).toMap() } ?: emptyMap() } catch (e: Throwable) { emptyMap() }
            bridge.lastCapture = null
            return PyAdapter.replayData(n, cap, side, names)
        } catch (e: Throwable) {
            FanLog.event(FanLog.ERROR, "${ErrorCodes.PYTHON} üye dağılımları alınamadı: ${e.message}")
            return null
        }
    }

    // ------------------------------------------------------------------ tahmin
    private fun liveInputs(env: V13Env): List<ModelOutput> {
        val inputs = ArrayList<ModelOutput>()
        for (v in env.engine.kotlin.memberViews()) inputs += ModelOutput("k_${v.id}", "Kotlin · ${v.name}", Group.KOTLIN, number = v.pred)
        val br = env.bridge
        if (br != null) {
            try {
                inputs += PyAdapter.numberOutputs(br.memberInfo())
                inputs += PyAdapter.sideOutputs(br.sideNext())
            } catch (e: Throwable) { FanLog.event(FanLog.ERROR, "${ErrorCodes.PYTHON} canlı girdi: ${e.message}") }
        }
        return inputs
    }

    private fun predictLive(env: V13Env) {
        val p = brain.predict(liveInputs(env), clock())
        store.db.runInTransaction {
            store.savePrediction(V13Store.LIVE, p, true)
            persistAll()
        }
    }

    private fun persistAll() { store.saveParts(brain.exportParts(), brain.count) }

    private fun publish() {
        _final.value = brain.open?.prediction
        _insights.value = try { V13Insights.build(brain) } catch (e: Throwable) { _insights.value }
    }

    private fun fail(where: String, e: Throwable) {
        dirty = true
        FanLog.event(FanLog.ERROR, "v13/$where: ${e.javaClass.simpleName}: ${e.message}")
        _status.value = V13Status("ERROR", 0f, "", "v1.3 $where: ${e.message}")
    }

    // ------------------------------------------------------------------ canlı akış
    /** Yeni gerçek sonuç: kilitli tahmin değerlendirilir, öğrenilir, kalıcılaştırılır, yeni tahmin KİLİTLENİR. */
    fun onNewRecord(rec: FanRecord, env: V13Env) {
        try {
            if (dirty) { resync(env); return }
            val bridge = env.bridge
            try { bridge?.sideStep(alphabet.bsOf(rec.number), alphabet.oeOf(rec.number), rec.timestamp) }
            catch (e: Throwable) { FanLog.event(FanLog.ERROR, "${ErrorCodes.PYTHON} side_step: ${e.message}") }
            val before = brain.count
            val ev = brain.onActual(rec)
            store.db.runInTransaction {
                store.appendRecord(rec)
                store.resolve(V13Store.LIVE, ev)
                brain.lastUndoSnapshot()?.let { if (it.first == before) store.saveUndo(it.first, it.second) }
            }
            predictLive(env)
            publish()
            _status.value = V13Status("READY", 1f, "#${rec.recordId} işlendi")
        } catch (e: Throwable) { fail("onNewRecord", e) }
    }

    /** DEL: durumu [newCount] kayda döndürür; tahmin yeniden ÜRETİLMEZ, kilitli olan geri gelir. */
    fun onUndo(newCount: Int, env: V13Env) {
        try {
            if (dirty) { resync(env); return }
            var ok = brain.undoTo(newCount)
            if (!ok) {
                val snap = store.loadUndo(newCount)
                if (snap != null) ok = try { brain.restore(snap); true } catch (e: Exception) { false }
            }
            if (!ok) { resync(env); return }
            val recs = env.records()
            store.db.runInTransaction {
                store.rollbackTo(newCount)
                store.syncRecords(recs)
            }
            val br = env.bridge
            if (br != null && br.sideUndoTo(newCount) == null) {
                try {
                    br.sideSync(IntArray(recs.size) { alphabet.bsOf(recs[it].number) }, IntArray(recs.size) { alphabet.oeOf(recs[it].number) },
                        LongArray(recs.size) { recs[it].timestamp })
                } catch (e: Throwable) { FanLog.event(FanLog.ERROR, "${ErrorCodes.PYTHON} side_undo: ${e.message}") }
            }
            if (brain.open == null) predictLive(env) else store.db.runInTransaction {
                store.savePrediction(V13Store.LIVE, brain.open!!.prediction, true); persistAll()
            }
            publish()
            _status.value = V13Status("READY", 1f, "geri alındı → $newCount")
        } catch (e: Throwable) { fail("onUndo", e) }
    }

    /** Durum bozulduğunda ya da manuel olarak: güvenli tam yeniden kurulum. */
    fun resync(env: V13Env) {
        FanLog.event(FanLog.REPLAY_STARTED, "resync (tam yeniden kurma)")
        env.bridge?.lastCapture = null
        finish(prepare(env.records(), forceFull = true), env)
    }

    // ------------------------------------------------------------------ RESEARCH (sandbox)
    /** Canlı duruma dokunmaz: ayrı brain, ayrı Kotlin meclisi, Python sandbox. */
    fun research(records: List<FanRecord>, cfg: EngineConfig, bridge: PythonBridge?): ResearchOutcome? {
        if (_researchBusy.value != null) return _research.value
        _researchBusy.value = "Replay hazırlanıyor…"
        try {
            val t0 = System.currentTimeMillis()
            val n = records.size
            val vals = IntArray(n) { alphabet.idx(records[it].number) }
            val times = LongArray(n) { records[it].timestamp }
            var data: PythonReplayData? = null
            if (bridge != null) {
                try {
                    _researchBusy.value = "Python sandbox oynatılıyor…"
                    val cap = bridge.sandboxReplay(vals, times)
                    val side = bridge.sideSync(IntArray(n) { alphabet.bsOf(records[it].number) },
                        IntArray(n) { alphabet.oeOf(records[it].number) }, times, sandbox = true)
                    val names = try { bridge.memberInfo()?.let { it.ids.zip(it.names).toMap() } ?: emptyMap() } catch (e: Throwable) { emptyMap() }
                    data = PyAdapter.replayData(n, cap, side, names)
                } catch (e: Throwable) { FanLog.event(FanLog.ERROR, "${ErrorCodes.PYTHON} sandbox: ${e.message}") }
            }
            val b = FanBrain(); b.mode = Mode.REPLAY
            val res = ReplayEngine.run(records, b, cfg, data, keepContext = true,
                progress = { i, total -> _researchBusy.value = "Replay $i / $total" })
            _researchBusy.value = "İstatistikler (bootstrap/permutation)…"
            val summary = ReplayStats.summarize(res)
            val cf = listOf(Scenario("Normal ensemble"), Scenario("Python yok", mapOf(Group.PYTHON to 0.0)),
                Scenario("Kotlin yok", mapOf(Group.KOTLIN to 0.0)), Scenario("Eşit ağırlık", equal = true)).map { Counterfactual.overReplay(res, it) }
            val lastCf = res.steps.lastOrNull()?.ctx?.let { Counterfactual.runAll(alphabet, it) } ?: emptyList()
            val crc = CRC32(); for (s in res.steps) crc.update(s.lockId.toByteArray())
            val out = ResearchOutcome(res, summary, cf, lastCf, crc.value, System.currentTimeMillis() - t0, V13Insights.build(b))
            try {
                store.saveReplay(ReplayStateEntity(mode = "RESEARCH", startedAt = t0, finishedAt = System.currentTimeMillis(),
                    processed = res.processed, total = res.total, status = if (res.errors.isEmpty() && res.leakViolations == 0L) "OK" else "UYARI",
                    leakChecks = res.leakChecks, leakViolations = res.leakViolations,
                    summary = "parmak izi=${java.lang.Long.toHexString(out.fingerprint)}; python=${data != null}"))
            } catch (e: Exception) { FanLog.event(FanLog.ERROR, "${ErrorCodes.DB} replay kaydı: ${e.message}") }
            _research.value = out
            return out
        } catch (e: Throwable) {
            FanLog.event(FanLog.ERROR, "${ErrorCodes.REPLAY} ${e.message}")
            return null
        } finally { _researchBusy.value = null }
    }

    fun liveContext(): PredictionContext? = brain.currentContext()
    fun alphabetOf(): Alphabet = alphabet
}
