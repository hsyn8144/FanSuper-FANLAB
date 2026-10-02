package fan.lightningroulette.lab

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import fan.lightningroulette.core.*
import fan.lightningroulette.data.ExperimentE
import fan.lightningroulette.data.StateE
import fan.lightningroulette.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class LabUi(
    val running: Boolean = false, val jobCode: String = "", val jobTitle: String = "", val progress: Int = 0, val detail: String = "",
    val queued: Int = 0, val baseBusy: Boolean = false, val baseProgress: Int = 0, val baseReady: Boolean = false,
    val version: Int = 0, val message: String = ""
)

/**
 * LAB arka plan yöneticisi: canlı motordan AYRI, düşük öncelikli tek iş parçacığı (canlı giriş bloke olmaz).
 * Kuyruk, checkpoint/resume, iptal, Python ön-hesaplama ve sonuç önbelleği (dataset hash + parametre hash'ine bağlı).
 */
object LabManager {
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "lr-lab").also { it.priority = Thread.MIN_PRIORITY } }
    private val scope = CoroutineScope(SupervisorJob() + exec.asCoroutineDispatcher())
    private val _ui = MutableStateFlow(LabUi())
    val ui: StateFlow<LabUi> = _ui
    private val cancelFlag = AtomicBoolean(false)
    private val pumping = AtomicBoolean(false)
    private val secCache = HashMap<String, List<Sec>>()
    @Volatile private var ctxCache: LabCtx? = null
    @Volatile private var ctxKey: String = ""
    @Volatile private var baseJobKey: String = ""

    private val PY_IDS: List<String> get() = PY_TITLES.keys.toList()

    fun baseConfig(): ExpConfig {
        val s = Engine.settings
        val kw = if (s.pythonEnabled && s.labPython && Engine.py != null) (if (s.weightAuto) 0.5 else s.kotlinPct / 100.0) else 1.0
        return ExpConfig("LAB tabanı (canlı yapılandırma)", s.window, s.kMode, s.nCand, Dir.of(s.dir), BrainConfig.ALL_FEATURES, s.decayPm / 1000.0, kw, 42L)
    }

    private fun year(): Int = Calendar.getInstance().get(Calendar.YEAR)
    fun codeFor(id: Long): String = "LAB-" + year() + "-" + id.toString().padStart(7, '0')

    private fun spinsOf(): Triple<IntArray, LongArray, String>? {
        val d = Engine.dataset ?: return null
        val sp = Engine.dao.spins(d.id)
        if (sp.isEmpty()) return null
        val v = IntArray(sp.size) { sp[it].value }; val t = LongArray(sp.size) { sp[it].tsMs }
        val hash = Importer.datasetHash(sp.map { Spin(it.id, it.value, it.tsMs, it.source, it.tsType) })
        return Triple(v, t, hash)
    }

    private fun key(prefix: String, hash: String, cfg: ExpConfig): String = "$prefix.${Engine.dataset?.id ?: 0}.$hash.${cfg.paramHash()}"

    // ───────── durum önbelleği (state_blobs; büyük JSON GZIP+Base64)
    private fun putBlob(k: String, json: String) { Engine.dao.putState(StateE(k, Pack.pack(json), System.currentTimeMillis())) }
    private fun getBlob(k: String): String? = try { Engine.dao.getState(k)?.let { Pack.unpack(it) } } catch (e: Exception) { null }

    // ───────── taban koşusu ve bağlam
    fun invalidate() { synchronized(secCache) { secCache.clear() }; ctxCache = null; ctxKey = ""; _ui.update { it.copy(version = it.version + 1) } }

    /** LAB sekmeleri için bağlam: tabanı önbellekten yükler; yoksa null (requestBase() arka planda hesaplar). */
    fun ctx(): LabCtx? {
        val (v, t, hash) = spinsOf() ?: return null
        val cfg = baseConfig()
        val k = key("lab.base", hash, cfg)
        ctxCache?.let { if (ctxKey == k + "|" + Engine.dao.experimentCount() + "|" + (Engine.dao.getState("${Engine.dataset?.id}:suites.$hash") != null)) return it }
        val snap = getBlob(k)?.let { RunSnap.fromJson(it) } ?: return null
        val suites = getBlob(key("lab.suites", hash, cfg))?.let { Suites.fromJson(it) }
        val det = getBlob(key("lab.det", hash, cfg))?.split(",")?.let { if (it.size == 2) (it[0].toLongOrNull() ?: 0L) to (it[1].toLongOrNull() ?: 0L) else null }
        val ex = Engine.dao.experiments().map { toRow(it) }
        val d = Engine.dataset
        val errs = Engine.dao.batches(1000).sumOf { it.invalid }
        val ctx = LabCtx(v, t, Engine.settings.sectors(), snap, cfg, cfg.kotlinWeight < 0.999, PY_IDS.map { PY_TITLES[it] ?: it }, det, suites, ex, errs,
            (d?.name ?: "dataset") + " v" + (d?.version ?: 1), Engine.settings.championVersion)
        ctxCache = ctx; ctxKey = k + "|" + Engine.dao.experimentCount() + "|" + (Engine.dao.getState("${Engine.dataset?.id}:suites.$hash") != null)
        _ui.update { it.copy(baseReady = true) }
        return ctx
    }

    fun cachedSecs(tab: String, sub: String, build: () -> List<Sec>): List<Sec> {
        val k = "$ctxKey|$tab|$sub"
        synchronized(secCache) { secCache[k]?.let { return it } }
        val r = build()
        synchronized(secCache) { secCache[k] = r }
        return r
    }

    fun toRow(e: ExperimentE) = ExpRow(e.id, e.code, e.hypothesis, e.paramsJson, e.status, e.cls, e.deltaPp, e.n, e.reason, e.createdAt)

    /** Taban koşusu yoksa arka planda hesapla (ilerleme LabUi'de). */
    fun requestBase(context: Context? = null) {
        scope.launch {
            val tri = spinsOf() ?: return@launch
            val cfg = baseConfig(); val k = key("lab.base", tri.third, cfg)
            if (getBlob(k) != null) { ctx(); return@launch }
            if (baseJobKey == k) return@launch
            baseJobKey = k
            _ui.update { it.copy(baseBusy = true, baseProgress = 0, message = "LAB taban koşusu hesaplanıyor…") }
            try { computeBase(tri.first, tri.second, cfg, k) } catch (e: Throwable) {
                Engine.store?.log("ERROR", Codes.REPLAY, "LAB_BASE_FAILED", e.message ?: "")
                _ui.update { it.copy(message = "${Codes.REPLAY}: ${e.message}") }
            }
            baseJobKey = ""
            _ui.update { it.copy(baseBusy = false, baseProgress = 100, message = "") }
            invalidate()
            ctx()
        }
    }

    private fun computeBase(v: IntArray, t: LongArray, cfg: ExpConfig, k: String): RunSnap? {
        val run = runWithPython(v, t, cfg, { p -> _ui.update { it.copy(baseProgress = p, jobTitle = "LAB taban koşusu") } }) ?: return null
        val snap = RunSnap.of(run)
        putBlob(k, snap.toJson())
        return snap
    }

    /** Python dahilse önce üyeleri parça parça hesaplar (iptal/ilerleme), sonra walk-forward koşar. İptalde null. */
    private fun runWithPython(v: IntArray, t: LongArray, cfg: ExpConfig, prog: (Int) -> Unit, resume: Resume? = null, ckptEvery: Int = 0, onCkpt: ((Resume) -> Unit)? = null): ExpRun? {
        val sectors = Engine.settings.sectors()
        var py: ((Int) -> PyOut?)? = null
        var pyShare = 0
        val bridge = Engine.py
        if (cfg.kotlinWeight < 0.999 && bridge != null) {
            pyShare = 60
            val n = v.size; val learnFrom = maxOf(50, LabRunner.valStart(n) - 2000)
            try {
                bridge.replayBegin(v, learnFrom)
                val outs = arrayOfNulls<PyOut>(n)
                var i = learnFrom
                while (i < n) {
                    if (cancelFlag.get()) return null
                    val step = minOf(150, n - i)
                    val chunk = bridge.replayChunk(learnFrom, step)
                    for ((j, members) in chunk.withIndex()) outs[i + j] = PyOut(PY_IDS, members)
                    i += step
                    prog(((i - learnFrom) * pyShare / maxOf(1, n - learnFrom)).coerceIn(0, pyShare))
                }
                py = { idx -> outs.getOrNull(idx) }
            } catch (e: Throwable) {
                Engine.store?.log("ERROR", Codes.PY, "LAB_PY_FAILED", e.message ?: "")
                py = null; pyShare = 0
            }
        }
        val c = ExpConfig(cfg.hypothesis, cfg.window, cfg.k, cfg.cands, cfg.dir, cfg.features, cfg.decay, if (py == null) 1.0 else cfg.kotlinWeight, cfg.seed)
        val run = LabRunner.run(v, t, c, sectors, py, { cancelFlag.get() }, { d, tot -> prog(pyShare + (d * (100 - pyShare) / maxOf(1, tot)).coerceIn(0, 100 - pyShare)) }, resume, ckptEvery, onCkpt)
        return if (run.result.status == "CANCELLED") null else run
    }

    // ───────── kuyruk
    fun enqueue(kind: String, cfg: ExpConfig, hypothesis: String, priority: Int = 0): ExperimentE? {
        val tri = spinsOf() ?: return null
        val d = Engine.dataset ?: return null
        val now = System.currentTimeMillis()
        val tmp = "TMP-" + System.nanoTime()
        val id = Engine.dao.insertExperiment(ExperimentE(0, tmp, kind, d.id, tri.third, hypothesis, Json.stringify(cfg.toMap()), cfg.paramHash(), cfg.seed, "lr-1.0.0", "QUEUED", "", 0.0, 0, "", "", "", priority, 0, now, 0, 0))
        val row = Engine.dao.experiment(id) ?: return null
        val upd = row.copy(code = codeFor(id))
        Engine.dao.updateExperiment(upd)
        Engine.store?.log("INFO", null, "EXPERIMENT_QUEUED", upd.code + " · " + hypothesis)
        refreshQueued()
        return upd
    }

    private fun refreshQueued() { _ui.update { it.copy(queued = Engine.dao.experiments().count { e -> e.status == "QUEUED" }) } }

    /** Kuyruğu arka planda işlet (zaten çalışıyorsa tekrar başlatmaz). */
    fun pump(context: Context? = null) {
        if (!pumping.compareAndSet(false, true)) return
        context?.let { try { ContextCompat.startForegroundService(it, Intent(it, LabService::class.java)) } catch (_: Throwable) { /* arka planda başlatma kısıtı: servis olmadan da çalışır */ } }
        scope.launch {
            try {
                while (true) {
                    val job = Engine.dao.nextQueued() ?: break
                    runJob(job)
                }
            } finally {
                pumping.set(false)
                _ui.update { it.copy(running = false, jobCode = "", progress = 0) }
                refreshQueued()
                invalidate()
            }
        }
    }

    fun cancelCurrent() { cancelFlag.set(true) }

    /** Uygulama açılışında: yarım kalan (RUNNING) işleri checkpoint'ten devam etmek üzere kuyruğa al. */
    fun resumeIfNeeded(context: Context?) {
        scope.launch {
            val stuck = Engine.dao.running()
            for (e in stuck) Engine.dao.updateExperiment(e.copy(status = "QUEUED", reason = "uygulama kapandı → checkpoint’ten devam"))
            if (stuck.isNotEmpty() || Engine.dao.nextQueued() != null) { refreshQueued(); pump(context) }
        }
    }

    private fun finish(e: ExperimentE, status: String, res: ExpResult?, reason: String, resultJson: String = "") {
        val upd = e.copy(status = status, cls = res?.cls ?: e.cls, deltaPp = res?.deltaPp ?: e.deltaPp, n = res?.n ?: e.n, reason = reason,
            resultJson = if (resultJson.isNotEmpty()) resultJson else (res?.toJson() ?: e.resultJson), ckpt = "", progress = if (status == "DONE") 100 else e.progress, finishedAt = System.currentTimeMillis())
        Engine.dao.updateExperiment(upd)
        Engine.store?.log(if (status == "DONE") "INFO" else "WARN", null, "EXPERIMENT_" + status, e.code + " · " + reason)
    }

    private fun runJob(e0: ExperimentE) {
        cancelFlag.set(false)
        var e = e0.copy(status = "RUNNING", startedAt = System.currentTimeMillis())
        Engine.dao.updateExperiment(e)
        _ui.update { it.copy(running = true, jobCode = e.code, jobTitle = e.hypothesis, progress = 0, detail = "", queued = Engine.dao.experiments().count { x -> x.status == "QUEUED" }) }
        val tri = spinsOf()
        if (tri == null) { finish(e, "REJECTED", null, "veri yok"); return }
        val (v, t, hash) = tri
        try {
            val cfg = ExpConfig.fromMap(Json.parse(e.paramsJson).jmap())
            val prog: (Int) -> Unit = { p -> _ui.update { it.copy(progress = p) }; if (p % 10 == 0) Engine.dao.updateExperiment(e.copy(progress = p)) }
            when (e.kind) {
                "SUITE" -> {
                    val base = baseConfig()
                    val kb = key("lab.base", hash, base)
                    if (getBlob(kb) == null) { _ui.update { it.copy(detail = "taban koşusu") }; computeBase(v, t, base, kb) ?: run { finish(e, "CANCELLED", null, "kullanıcı iptal etti"); return } }
                    val su = LabSuites.runAll(v, t, base, Engine.settings.sectors(), true, { cancelFlag.get() }) { d, tot, label -> _ui.update { it.copy(progress = (d * 100 / maxOf(1, tot)), detail = label) } }
                    if (cancelFlag.get()) { finish(e, "CANCELLED", null, "kullanıcı iptal etti (kısmi sonuç saklanmadı)"); return }
                    putBlob(key("lab.suites", hash, base), su.toJson())
                    invalidate()
                    val c = ctx()
                    val rs = c?.robust
                    val full = su.full
                    finish(e, "DONE", full, if (rs != null) "Robustness ${rs.score}/100 · sınıf ${rs.cls}" else "tamamlandı",
                        full?.toJson() ?: "")
                    if (rs != null) { val cur = Engine.dao.experiment(e.id); if (cur != null) Engine.dao.updateExperiment(cur.copy(cls = rs.cls)) }
                    Engine.labFlags = if (c != null) LabTabs.warnFlags(c).filter { f -> f.startsWith("Parameter") || f.startsWith("OVERFIT") || f.startsWith("MODEL DRIFT") || f.startsWith("Calibration") } else emptyList()
                }
                "DETERMINISM" -> {
                    val base = baseConfig()
                    val r1 = runWithPython(v, t, base, prog) ?: run { finish(e, "CANCELLED", null, "kullanıcı iptal etti"); return }
                    val r2 = runWithPython(v, t, base, prog) ?: run { finish(e, "CANCELLED", null, "kullanıcı iptal etti"); return }
                    putBlob(key("lab.det", hash, base), r1.result.hash.toString() + "," + r2.result.hash.toString())
                    val same = r1.result.hash == r2.result.hash
                    if (same) finish(e, "DONE", r1.result, "AYNI ✓ iki bağımsız koşu aynı özeti verdi") else finish(e, "REJECTED", r1.result, "${Codes.REPRO}: iki koşu farklı özet verdi")
                    invalidate()
                }
                else -> { // RUN
                    var ck: Resume? = e.ckpt.takeIf { it.isNotEmpty() }?.let { try { StepIO.decode(Pack.unpack(it)) } catch (_: Exception) { null } }
                    val run = runWithPython(v, t, cfg, prog, ck, Engine.settings.labCheckpoint) { r ->
                        try { Engine.dao.updateExperiment(e.copy(ckpt = Pack.pack(StepIO.encode(r)), progress = e.progress)) } catch (_: Exception) { }
                    }
                    if (run == null) { finish(e, "CANCELLED", null, "kullanıcı iptal etti — kısmi sonuç CANCELLED olarak saklandı"); return }
                    val res = run.result
                    val reason = LabReport.failReason(res)
                    finish(e, if (res.cls == "L") "REJECTED" else "DONE", res, reason)
                }
            }
        } catch (ex: Throwable) {
            val code = (ex as? LrError)?.code ?: Codes.EXP
            finish(e, "REJECTED", null, "$code: ${ex.message}")
        }
    }

    fun delete(e: ExperimentE) { /* silinmez: yalnızca CANCELLED işaretlenir */ if (e.status == "QUEUED") Engine.dao.updateExperiment(e.copy(status = "CANCELLED", reason = "kuyruktan çıkarıldı", finishedAt = System.currentTimeMillis())); refreshQueued() }
}
