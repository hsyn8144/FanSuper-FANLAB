package fan.superai

import android.content.Context
import android.util.Log
import fan.superai.data.AppSettings
import fan.superai.data.DataStore
import fan.superai.data.Rec
import fan.superai.data.Settings
import fan.superai.engine.Discovery
import fan.superai.engine.DiscoveryReport
import fan.superai.engine.EngineState
import fan.superai.engine.FanEngine
import fan.superai.engine.ResearchLab
import fan.superai.engine.ResearchReport
import fan.superai.engine.MemberStat
import fan.superai.engine.PythonBridge
import fan.superai.v13.CfResult
import fan.superai.v13.Counterfactual
import fan.superai.v13.FanRecord
import fan.superai.v13.FinalPrediction
import fan.superai.v13.PredictionExplanation
import fan.superai.v13.ResearchOutcome
import fan.superai.v13.V13Env
import fan.superai.v13.V13Insights
import fan.superai.v13.V13Runtime
import fan.superai.v13.V13Status
import fan.superai.v13.db.FanDatabase
import fan.superai.v13.db.PredictionRecordEntity
import fan.superai.v13.db.V13Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Grafik ekranı verisi (motor iş parçacığında hazırlanır). */
data class ChartData(
    val ref: List<List<Double>>,   // [tek, çift, yan] için hakem serileri
    val kot: List<List<Double>>,
    val py: List<List<Double>>,
    val dist: IntArray,
    val calSingle: List<Triple<Double, Int, Double>>,
    val calPair: List<Triple<Double, Int, Double>>,
    val calSide: List<Triple<Double, Int, Double>>
)

/**
 * Düğmeye basıldığı anda ekranda gösterilecek sayılar (motor hesabı bitene kadar).
 * [base]: bu liste oluşurken motorda bulunan kayıt sayısı.
 * [values]: yalnızca EKRANDA gösterilecek son 6 sayı (görüntü amaçlı, kırpılabilir).
 * [pendingCount]: motora gönderilmiş ama henüz işlenmemiş TOPLAM işlem sayısı —
 * 6'dan fazla hızlı tuşlamada [values] kırpılsa bile bu sayı kırpılmaz, böylece
 * "hepsi işlendi mi" kontrolü (bkz. [EngineHost.syncEcho]) yanlış erken tetiklenmez.
 */
data class Echo(val base: Int, val values: List<Int>, val pendingCount: Int = values.size)

/** Motor durumu + anında geri bildirimi birleştirir (son 6 sayı). */
fun recentWithEcho(st: EngineState?, echo: Echo): List<Int> {
    val base = st?.recent ?: emptyList()
    return if (echo.values.isEmpty()) base else (base + echo.values).takeLast(6)
}

/**
 * "Loglar" ekranında gösterilen tek bir işlem kaydı: ne zaman, hangi düğmeye
 * basıldı, o anki hakem kararı ve o adımda HER ÜYE MODELİN (Kotlin + Python)
 * ürettiği tahmin/ağırlık. Ekstra hesap gerektirmez — motor bunu her adımda
 * zaten üretiyor ([FanEngine.state]), burada sadece o anlık görüntü saklanır.
 */
data class ActivityLogEntry(
    val time: Long,
    val action: String,             // "EKLE 3", "GERİ AL"
    val verdictLabel: String?,
    val confidencePct: Int?,
    val kotlinStats: List<MemberStat>,
    val pythonStats: List<MemberStat>
)

/**
 * Motorun tek sahibi. Tüm hesaplar tek bir arka plan iş parçacığında sırayla yapılır;
 * arayüz ve overlay yalnızca StateFlow'ları izler.
 *
 * DÜĞME GECİKMESİ: Sayı ve geri al düğmeleri bu sınıfa ANINDA işlenir — giriş önce
 * [echo] ile ekrana yansır, ardından motor iş parçacığında sıraya girer. Motor hazır
 * değilse (ilk açılış) girişler [waiting] içine alınır ve hazırlık bitince işlenir;
 * hiçbir düğme basışı sessizce kaybolmaz.
 */
object EngineHost {
    private const val TAG = "FAN_SUPER"
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "fan-engine").apply { priority = Thread.NORM_PRIORITY } }
    private lateinit var app: Context
    private lateinit var engine: FanEngine
    private var bridge: PythonBridge? = null
    private val lock = Any()
    private var recs = mutableListOf<Rec>()
    private var applied: AppSettings? = null
    private var sinceDiscovery = 0
    private var sinceBackup = 0
    private val waiting = ArrayDeque<Rec>()
    private val inFlight = AtomicInteger(0)

    private val _state = MutableStateFlow<EngineState?>(null)
    val state: StateFlow<EngineState?> = _state
    private val _busy = MutableStateFlow<String?>("Hazırlanıyor…")
    val busy: StateFlow<String?> = _busy
    private val _echo = MutableStateFlow(Echo(0, emptyList()))
    val echo: StateFlow<Echo> = _echo
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending
    private val _discovery = MutableStateFlow<DiscoveryReport?>(null)
    val discovery: StateFlow<DiscoveryReport?> = _discovery
    private val _charts = MutableStateFlow<ChartData?>(null)
    val charts: StateFlow<ChartData?> = _charts
    private val _pyError = MutableStateFlow<String?>(null)
    val pyError: StateFlow<String?> = _pyError
    private const val LOG_CAP = 500
    private val _activityLog = MutableStateFlow<List<ActivityLogEntry>>(emptyList())
    val activityLog: StateFlow<List<ActivityLogEntry>> = _activityLog
    private val _research = MutableStateFlow<ResearchReport?>(null)
    val research: StateFlow<ResearchReport?> = _research

    // ---- v1.3
    private val rexec = Executors.newSingleThreadExecutor { r -> Thread(r, "fan-research").apply { priority = Thread.MIN_PRIORITY } }
    private var v13: V13Runtime? = null
    private var v13Plan: V13Runtime.Plan? = null
    private val emptyFinal = MutableStateFlow<FinalPrediction?>(null)
    private val emptyInsights = MutableStateFlow<V13Insights?>(null)
    private val emptyStatus = MutableStateFlow(V13Status())
    private val emptyOutcome = MutableStateFlow<ResearchOutcome?>(null)
    private val emptyBusy = MutableStateFlow<String?>(null)

    /** Tek nihai tahmin (rakam + yan) — overlay, ana ekran ve FAN LAB buradan beslenir. */
    val finalPrediction: StateFlow<FinalPrediction?> get() = v13?.final ?: emptyFinal
    val v13Insights: StateFlow<V13Insights?> get() = v13?.insights ?: emptyInsights
    val v13Status: StateFlow<V13Status> get() = v13?.status ?: emptyStatus
    val v13Research: StateFlow<ResearchOutcome?> get() = v13?.researchResult ?: emptyOutcome
    val v13ResearchBusy: StateFlow<String?> get() = v13?.researchBusy ?: emptyBusy

    private fun fanRecords(): List<FanRecord> = synchronized(lock) {
        recs.mapIndexed { i, r -> FanRecord.of((i + 1).toLong(), r.time, r.value) }
    }

    private fun v13Env(): V13Env = V13Env(engine, bridge, engine.cfg) { fanRecords() }

    /** Python replay'inden ÖNCE: kayıtlı durumu incele; üye dağılımı gerekiyorsa köprüye bildir. */
    private fun v13Prepare(force: Boolean) {
        val rt = v13 ?: return
        try {
            val plan = rt.prepare(fanRecords(), force)
            v13Plan = plan
            bridge?.captureMembers = plan.needsPythonMembers
        } catch (e: Throwable) { Log.e(TAG, "v13 prepare", e); v13Plan = null }
    }

    /** Python/Kotlin kurulumu BİTTİKTEN sonra: replay/yakalama + yeni tahmin + kalıcılık. */
    private fun v13Finish(clearHistory: Boolean = false) {
        val rt = v13 ?: return
        val plan = v13Plan ?: return
        v13Plan = null
        try {
            _busy.value = "🧠 v1.4 meta-ensemble hazırlanıyor…"
            rt.finish(plan, v13Env(), clearHistory)
        } catch (e: Throwable) { Log.e(TAG, "v13 finish", e) }
        bridge?.captureMembers = false
    }

    fun predictionPage(offset: Int, limit: Int): List<PredictionRecordEntity> =
        try { v13?.store0?.page(offset, limit) ?: emptyList() } catch (e: Throwable) { emptyList() }

    fun predictionTotal(): Int = try { v13?.store0?.predictionTotal() ?: 0 } catch (e: Throwable) { 0 }

    fun explanationFor(seq: Int): PredictionExplanation? = try { v13?.store0?.explanation(seq) } catch (e: Throwable) { null }

    /** Karşı-olgusal analiz: kilitli tahmin girdileri üzerinde saf simülasyon (canlı durum değişmez). */
    fun liveCounterfactual(): List<CfResult> {
        val rt = v13 ?: return emptyList()
        val ctx = rt.liveContext() ?: return emptyList()
        return Counterfactual.runAll(rt.alphabet, ctx)
    }

    /** RESEARCH/REPLAY modu: ayrı iş parçacığı + sandbox; canlı durum ve düğmeler etkilenmez. */
    fun runResearchReplay() {
        val rt = v13 ?: return
        if (rt.researchBusy.value != null) return
        rexec.execute {
            try { rt.research(fanRecords(), engine.cfg, bridge) } catch (e: Throwable) { Log.e(TAG, "research", e) }
        }
    }

    /** Kullanıcı elle "Full Replay": tüm geçmiş baştan oynatılır, canlı tahmin geçmişi korunur. */
    fun fullReplay() = exec.execute {
        if (!::engine.isInitialized) return@execute
        bridge?.deleteState()
        rebuildFromRecs(clearHistory = false)
    }

    /** [action] anındaki durumu (hakem kararı + her üyenin tahmini) log listesine ekler. */
    private fun logActivity(action: String) {
        if (!::engine.isInitialized) return
        try {
            val st = engine.state()
            val entry = ActivityLogEntry(
                System.currentTimeMillis(), action, st.verdict?.label,
                st.verdict?.confidence?.let { (it * 100).toInt() },
                st.kotlinStats, st.pythonStats
            )
            _activityLog.value = (_activityLog.value + entry).takeLast(LOG_CAP)
        } catch (e: Throwable) { Log.e(TAG, "logActivity", e) }
    }

    fun clearActivityLog() { _activityLog.value = emptyList() }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        try { v13 = V13Runtime(V13Store(FanDatabase.get(app))) } catch (e: Throwable) { Log.e(TAG, "v13 db", e) }
        fan.superai.v13.FanLog.sink = { name, msg ->
            if (name == fan.superai.v13.FanLog.ERROR) Log.w("FAN_V13", "$name $msg") else Log.i("FAN_V13", "$name $msg")
        }
        exec.execute {
            try {
                val s = Settings.value
                applied = s
                val loaded = loadRecords()
                synchronized(lock) { recs = loaded }
                engine = FanEngine(s.engineConfig(), null)
                loaded.forEach { engine.values.add(it.value - 1); engine.times.add(it.time) }
                _busy.value = "🔵 Kotlin meclisi öğreniyor…"
                engine.replayPython(); engine.rebuildKotlin()
                publish()
                runDiscovery()
                v13Prepare(false)
                if (s.pythonEnabled) startPython(s)
                v13Finish()
                flushWaiting()          // hazırlık sırasında basılan düğmeler şimdi işlenir
            } catch (e: Throwable) {
                Log.e(TAG, "init", e); _pyError.value = e.message
            } finally { _busy.value = null; publish(); syncEcho() }
        }
    }

    /**
     * Başlangıç kayıtları: Room (v1.3) ana kaynaktır; CSV yalnızca ilk kurulum / içe aktarma / yedek içindir.
     * v1.2'den yükseltmede DB boştur → CSV bir kez DB'ye aktarılır. CSV DB'den uzunsa (ve DB onun öneki ise) kuyruk alınır.
     */
    private fun loadRecords(): MutableList<Rec> {
        val csv = DataStore.load(app)
        val store = v13?.store0 ?: return csv
        return try {
            val db = store.records()
            if (db.isEmpty()) return csv
            val dbRecs = db.map { Rec(it.number, it.timestamp) }.toMutableList()
            val csvExtends = csv.size > dbRecs.size && dbRecs.indices.all { csv[it].value == dbRecs[it].value && csv[it].time == dbRecs[it].time }
            if (csvExtends) csv else {
                if (dbRecs.size != csv.size) DataStore.save(app, dbRecs)
                dbRecs
            }
        } catch (e: Throwable) { Log.e(TAG, "loadRecords", e); csv }
    }

    private fun startPython(s: AppSettings) {
        _busy.value = "🐍 Python meclisi öğreniyor… (ilk açılışta biraz sürer)"
        try {
            val b = PythonBridge(app, s.pythonJson())
            bridge = b
            b.captureMembers = v13Plan?.needsPythonMembers == true
            engine.setPython(b)
            engine.replayPython()
            _busy.value = "⚖️ Hakem hazırlanıyor…"
            engine.rebuildKotlin()
            _pyError.value = null
        } catch (e: Throwable) {
            Log.e(TAG, "python", e)
            _pyError.value = "Python başlatılamadı: ${e.message}"
            bridge = null; engine.setPython(null)
            engine.replayPython(); engine.rebuildKotlin()
        }
    }

    /** Motor hazır olmadan gelen girişleri sırayla işler. */
    private fun flushWaiting() {
        while (waiting.isNotEmpty()) {
            val r = waiting.removeFirst()
            try { addNow(r) } catch (e: Throwable) { Log.e(TAG, "flush", e) }
        }
    }

    private fun publish() {
        if (!::engine.isInitialized) return
        try {
            _state.value = engine.state()
            val sel = listOf<(fan.superai.engine.StepLog) -> Boolean>({ it.top1 }, { it.top2 }, { it.sideAny })
            val kSel = listOf<(fan.superai.engine.StepLog) -> Boolean>({ it.kTop1 }, { it.kTop2 }, { it.sideAny })
            val pSel = listOf<(fan.superai.engine.StepLog) -> Boolean>({ it.pTop1 }, { it.pTop2 }, { it.sideAny })
            val dist = IntArray(4); engine.values.forEach { dist[it]++ }
            _charts.value = ChartData(
                sel.map { engine.rollingSeries(it) },
                kSel.map { engine.rollingSeries(it) },
                pSel.mapIndexed { i, f -> if (i == 2) emptyList() else engine.rollingSeries(f) { it.pAvail } },
                dist, engine.referee.calSingle.table(), engine.referee.calPair.table(), engine.referee.calSide.table()
            )
            _research.value = ResearchLab.analyze(engine.logs)
        } catch (e: Throwable) { Log.e(TAG, "publish", e) }
    }

    /** Echo listesini motor durumuna göre temizler (hepsi işlendiyse). */
    private fun syncEcho() {
        val e = _echo.value
        if (e.pendingCount == 0) return
        val c = _state.value?.count ?: 0
        // pendingCount kullanılır, e.values.size DEĞİL: values sadece son 6'yı tutar
        // (ekran için), 6'dan fazla bekleyen işlem varsa values kırpılmış olabilir.
        if (inFlight.get() <= 0 && c >= e.base + e.pendingCount) _echo.value = Echo(c, emptyList(), 0)
    }

    private fun runDiscovery() {
        _discovery.value = Discovery.run(engine.values.toIntArray(), engine.times.toLongArray())
        sinceDiscovery = 0
    }

    fun rerunDiscovery() = exec.execute { runDiscovery() }
    fun rerunResearch() = exec.execute { if (::engine.isInitialized) _research.value = ResearchLab.analyze(engine.logs) }

    /**
     * Yeni sayı (1..4). Çağrı ANINDA döner: sayı önce ekranda belirir, motor arkada işler.
     */
    fun add(value: Int) {
        if (value !in 1..4) return
        val base = _state.value?.count ?: 0
        val e = _echo.value
        _echo.value = Echo(if (e.pendingCount == 0) base else e.base, (e.values + value).takeLast(6), e.pendingCount + 1)
        _pending.value = inFlight.incrementAndGet()
        exec.execute {
            val t0 = System.nanoTime()
            try {
                val r = Rec(value, System.currentTimeMillis() / 1000)
                if (!::engine.isInitialized) { waiting.addLast(r); return@execute }
                addNow(r)
                Log.i(TAG, "add($value) ${(System.nanoTime() - t0) / 1_000_000L} ms")
            } catch (ex: Throwable) {
                Log.e(TAG, "add", ex)
            } finally {
                _pending.value = inFlight.decrementAndGet()
                syncEcho()
            }
        }
    }

    private fun addNow(r: Rec) {
        synchronized(lock) {
            recs.add(r)
            DataStore.append(app, recs.size, r)
        }
        engine.add(r.value - 1, r.time)
        v13?.onNewRecord(FanRecord.of(synchronized(lock) { recs.size }.toLong(), r.time, r.value), v13Env())
        publish()
        logActivity("EKLE ${r.value}")
        val s = Settings.value
        if (s.discoveryEvery > 0 && ++sinceDiscovery >= s.discoveryEvery) runDiscovery()
        if (s.backupDownload && ++sinceBackup >= 10) { sinceBackup = 0; DataStore.backupToDownload(app, recs) }
    }

    /**
     * Son sayıyı geri alır. Geri alma artık adım adım tutulan kayıtlarla yapılır:
     * iki meclis de tam olarak bir adım geriye sarılır, tüm geçmiş baştan öğrenilmez.
     */
    fun undo() {
        val e = _echo.value
        if (e.pendingCount > 0) {
            val newPending = e.pendingCount - 1
            _echo.value = if (newPending == 0) Echo(e.base, emptyList(), 0) else Echo(e.base, e.values.dropLast(1), newPending)
        }
        _pending.value = inFlight.incrementAndGet()
        exec.execute {
            try {
                if (!::engine.isInitialized) {
                    // Motor daha hazır değil: bekleyen girişlerden sonuncusunu geri al.
                    if (waiting.isNotEmpty()) waiting.removeLast()
                    return@execute
                }
                if (synchronized(lock) { recs.isEmpty() }) return@execute
                _busy.value = "Geri alınıyor…"
                synchronized(lock) { recs.removeAt(recs.size - 1) }
                if (!DataStore.removeLast(app)) synchronized(lock) { DataStore.save(app, recs) }
                engine.undo()
                v13?.onUndo(synchronized(lock) { recs.size }, v13Env())
                if (engine.lastUndoFast) Log.i(TAG, "undo ${engine.lastUndoMs} ms (anında)")
                else Log.w(TAG, "undo ${engine.lastUndoMs} ms (tam yeniden kurma gerekti)")
                logActivity("GERİ AL (${if (engine.lastUndoFast) "anında" else "tam yeniden kurma"}, ${engine.lastUndoMs} ms)")
            } catch (ex: Throwable) {
                Log.e(TAG, "undo", ex)
            } finally {
                _busy.value = null
                publish()
                _pending.value = inFlight.decrementAndGet()
                syncEcho()
            }
        }
    }

    /** Ayarlar değişince motoru gerekli ölçüde yeniden kurar. */
    fun applySettings(s: AppSettings) = exec.execute {
        if (!::engine.isInitialized) return@execute
        val old = applied ?: s
        applied = s
        val engineChanged = old.engineConfig() != s.engineConfig()
        val pyChanged = old.pythonEnabled != s.pythonEnabled || old.pythonJson() != s.pythonJson()
        if (!engineChanged && !pyChanged) return@execute
        engine.cfg = s.engineConfig()
        try {
            v13Prepare(true)
            if (pyChanged) {
                if (s.pythonEnabled) startPython(s) else {
                    bridge = null; engine.setPython(null)
                    _busy.value = "Yeniden kuruluyor…"; engine.replayPython(); engine.rebuildKotlin()
                }
            } else {
                _busy.value = "Yeniden kuruluyor…"; engine.rebuildKotlin()
            }
            v13Finish()
        } finally { _busy.value = null; publish(); syncEcho() }
    }

    /** İçe aktarılan veriyle değiştir. */
    fun replaceData(newRecs: List<Rec>) = exec.execute {
        synchronized(lock) { recs = newRecs.toMutableList(); DataStore.save(app, recs) }
        _echo.value = Echo(0, emptyList())
        waiting.clear()
        if (!::engine.isInitialized) return@execute
        rebuildFromRecs(clearHistory = true)
    }

    fun resetLearning() = exec.execute {
        if (!::engine.isInitialized) return@execute
        bridge?.deleteState()
        rebuildFromRecs(clearHistory = true)
    }

    fun deleteAll() = exec.execute {
        synchronized(lock) { recs.clear(); DataStore.save(app, recs) }
        _echo.value = Echo(0, emptyList())
        waiting.clear()
        clearActivityLog()
        if (!::engine.isInitialized) return@execute
        bridge?.deleteState()
        rebuildFromRecs(clearHistory = true)
    }

    private fun rebuildFromRecs(clearHistory: Boolean) {
        try {
            v13Prepare(true)
            engine.values.clear(); engine.times.clear()
            synchronized(lock) { recs.forEach { engine.values.add(it.value - 1); engine.times.add(it.time) } }
            _busy.value = if (bridge != null) "🐍 Python yeniden öğreniyor…" else "Yeniden kuruluyor…"
            engine.replayPython(); engine.rebuildKotlin()
            runDiscovery()
            v13Finish(clearHistory)
        } finally { _busy.value = null; publish(); syncEcho() }
    }

    fun records(): List<Rec> = synchronized(lock) { recs.toList() }

    fun exportCsv(): String = DataStore.toCsv(records())

    fun persist() = exec.execute { bridge?.save() }
}
