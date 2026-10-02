package fan.lightningroulette.engine

import android.app.Application
import fan.lightningroulette.core.*
import fan.lightningroulette.data.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class BootStep(val name: String, val status: String, val detail: String = "")   // status: pending | run | ok | err

data class EngineUi(
    val phase: String = "boot",                 // boot | setup | ready | error
    val steps: List<BootStep> = emptyList(),
    val datasetId: Long = 0, val datasetName: String = "", val datasetVersion: Int = 0, val synthetic: Boolean = false, val spinCount: Int = 0,
    val pred: PredView? = null, val minSample: Int = 50,
    val last8: List<Int> = emptyList(),                       // EN YENİ solda
    val pyStatus: String = "OFF", val pyMessage: String = "",
    val evaluated: PredView? = null, val eval: Eval? = null, val evalActual: Int = -1,
    val summary: List<SumRow> = emptyList(), val cls: String = "S", val flags: List<String> = emptyList(), val sumN: Int = 0,
    val busy: String? = null, val toast: String? = null, val toastId: Long = 0, val error: String? = null,
    val champion: String = "v1.0.0", val tick: Long = 0
)

/**
 * Motor: tek iş parçacıklı (Room + Python + Brain). UI ve overlay yalnızca bu nesne üzerinden konuşur.
 * Akış: tahmin → LOCK → sonuç → değerlendirme → öğrenme (Session). Çift ENTER `expectCount` ile yoksayılır.
 */
object Engine {
    private lateinit var app: Application
    lateinit var settings: Settings; private set
    lateinit var db: LrDb; private set
    val dao: LrDao get() = db.dao()

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "lr-engine") }
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _ui = MutableStateFlow(EngineUi())
    val ui: StateFlow<EngineUi> = _ui

    @Volatile private var started = false
    var session: Session? = null; private set
    var store: RoomStore? = null; private set
    var py: PyBridge? = null; private set
    var dataset: DatasetE? = null; private set
    private var pyError: String? = null
    @Volatile var labFlags: List<String> = emptyList()
    private var toastSeq = 0L

    fun init(application: Application) {
        if (started) return
        started = true
        app = application
        settings = Settings(application)
        scope.launch { boot() }
    }

    val context: Application get() = app

    /** Açılış hata ekranından "Yeniden dene". */
    fun retryBoot() { scope.launch { boot() } }

    // ───────────────── açılış
    private fun setStep(name: String, status: String, detail: String = "") {
        _ui.update { u -> u.copy(steps = u.steps.map { if (it.name == name) BootStep(name, status, detail) else it }) }
    }

    private val STEP_DB = "Room şeması"
    private val STEP_DATA = "Dataset ve kayıtlar"
    private val STEP_PY = "Python meclisi (Chaquopy)"
    private val STEP_ENGINE = "Motor ve kilitli tahmin"

    private fun boot() {
        _ui.update { it.copy(phase = "boot", steps = listOf(STEP_DB, STEP_DATA, STEP_PY, STEP_ENGINE).map { n -> BootStep(n, "pending") }, champion = settings.championVersion) }
        setStep(STEP_DB, "run")
        try {
            db = LrDb.get(app)
            val c = db.openHelper.writableDatabase.query("PRAGMA quick_check")
            val ok = try { c.moveToFirst() && c.getString(0) == "ok" } finally { c.close() }
            if (!ok) throw IllegalStateException("PRAGMA quick_check başarısız")
            setStep(STEP_DB, "ok", "şema v${LrDb.VERSION} · bütünlük tamam")
        } catch (e: Throwable) {
            setStep(STEP_DB, "err", "${Codes.DB}: ${e.message}")
            _ui.update { it.copy(phase = "error", error = "${Codes.DB}: veritabanı açılamadı (${e.message}). Uygulama verilerini silmeden önce yedek al.") }
            return
        }
        setStep(STEP_DATA, "run")
        try {
            dataset = dao.activeDataset()
            val d = dataset
            if (d == null || !settings.setupDone) {
                setStep(STEP_DATA, "ok", "başlangıç verisi seçimi bekleniyor")
                setStep(STEP_PY, "pending", "kurulumdan sonra"); setStep(STEP_ENGINE, "pending")
                _ui.update { it.copy(phase = "setup") }
                return
            }
            setStep(STEP_DATA, "ok", "${d.name} · ${dao.spinCount(d.id)} spin")
        } catch (e: Throwable) {
            setStep(STEP_DATA, "err", "${Codes.DB}: ${e.message}")
            _ui.update { it.copy(phase = "error", error = "${Codes.DB}: ${e.message}") }
            return
        }
        openSession()
    }

    /** Aktif dataset için Python + oturum + kilitli tahmin. Ayar/dataset değişince de çağrılır. */
    fun openSession() {
        val d = dataset ?: dao.activeDataset().also { dataset = it } ?: return
        setStep(STEP_PY, "run")
        if (settings.pythonEnabled && py == null && pyError == null) {
            try { py = PyBridge(app, settings.sectors().bounds); setStep(STEP_PY, "ok", "numpy · 8 üye · Python 3.11") }
            catch (e: Throwable) {
                pyError = e.message ?: e.javaClass.simpleName
                setStep(STEP_PY, "err", "${Codes.PY}: $pyError · Kotlin-only")
                RoomStore(db, d.id).log("ERROR", Codes.PY, "PY_INIT_FAILED", pyError ?: "")
            }
        } else if (!settings.pythonEnabled) setStep(STEP_PY, "ok", "kapalı (Ayarlar) · Kotlin-only")
        else if (pyError != null) setStep(STEP_PY, "err", "${Codes.PY}: $pyError · Kotlin-only")
        else setStep(STEP_PY, "ok", "hazır")

        setStep(STEP_ENGINE, "run")
        try {
            val st = RoomStore(db, d.id); store = st
            val host: PyHost? = py ?: if (settings.pythonEnabled && pyError != null) FailingPy(pyError ?: "Python başlatılamadı") else null
            val s = Session(st, settings.brainConfig(), host, modelVersion = settings.championVersion)
            s.open { done, total -> setBusy("Geçmiş işleniyor $done / $total") }
            s.predictNext()
            session = s
            setStep(STEP_ENGINE, "ok", "${s.size} spin · öğrenilen ${s.learned}")
            setBusy(null)
            publish { it.copy(phase = "ready", error = null) }
        } catch (e: Throwable) {
            val code = (e as? LrError)?.code ?: Codes.STATE1
            setStep(STEP_ENGINE, "err", "$code: ${e.message}")
            setBusy(null)
            _ui.update { it.copy(phase = "error", error = "$code: ${e.message}") }
        }
    }

    private class FailingPy(val msg: String) : PyHost { override fun predict(window: IntArray, offset: Int): PyOut = throw IllegalStateException(msg) }

    private fun setBusy(text: String?) { _ui.update { it.copy(busy = text) } }

    // ───────────────── UI durumu
    fun publish(extra: (EngineUi) -> EngineUi = { it }) {
        val s = session; val d = dataset ?: return
        if (s == null) { _ui.update { extra(it) }; return }
        val evals = try { dao.lastEvaluations(d.id, LiveSummaryCalc.WINDOW).reversed().map { Eval.fromMap(Json.parse(it.json).jmap()) } } catch (e: Exception) { emptyList() }
        val sum = LiveSummaryCalc.compute(evals, labFlags)
        val pred = s.view
        val pyS = pred?.pyStatus ?: if (!settings.pythonEnabled) "OFF" else if (pyError != null) "ERROR" else "OK"
        _ui.update { u ->
            extra(u.copy(
                datasetId = d.id, datasetName = d.name, datasetVersion = d.version, synthetic = d.synthetic, spinCount = s.size,
                pred = pred, minSample = s.cfg.minSample, last8 = s.last(8).reversed(), pyStatus = pyS,
                pyMessage = if (pyS == "ERROR") (pyError ?: "Python çıktısı geçersiz/yok") + " — Kotlin-only fallback" else "",
                summary = sum.rows, cls = sum.cls, flags = sum.flags, sumN = sum.n, champion = settings.championVersion, tick = u.tick + 1
            ))
        }
    }

    private fun toast(text: String): (EngineUi) -> EngineUi = { it.copy(toast = text, toastId = ++toastSeq) }
    fun notify(text: String) { publish(toast(text)) }
    fun clearToast() { _ui.update { it.copy(toast = null) } }
    fun clearEval() { _ui.update { it.copy(evaluated = null, eval = null, evalActual = -1) } }
    fun clearError() { _ui.update { it.copy(error = null) } }

    // ───────────────── canlı işlemler
    fun enter(value: Int, expectCount: Int) { scope.launch { doEnter(value, expectCount) } }

    private fun doEnter(value: Int, expectCount: Int) {
        val s = session ?: return
        try {
            when (val r = s.enter(value, expectCount)) {
                is EnterResult.Entered -> {
                    py?.let { if (s.size % 10 == 0) it.save() }
                    publish { u -> toast("Spin #${r.spin.id} = $value eklendi" + (if (r.eval != null) " · tahmin değerlendirildi" else "") + (if (r.next != null) " · yeni tahmin kilitlendi" else ""))(u.copy(evaluated = r.evaluated, eval = r.eval, evalActual = value)) }
                }
                is EnterResult.Ignored -> publish(toast(r.reason))
                is EnterResult.Rejected -> publish(toast("${r.code}: ${r.reason}"))
            }
        } catch (e: Throwable) {
            val code = (e as? LrError)?.code ?: Codes.STATE1
            store?.log("ERROR", code, "ENTER_ERROR", e.message ?: "")
            publish { it.copy(error = "$code: ${e.message}") }
        }
    }

    fun undo() {
        scope.launch {
            val s = session ?: return@launch
            try {
                val r = s.undoLast()
                py?.save()
                publish { u -> toast(if (r != null) "Son spin (${r.value}) geri alındı · aynı kilitli tahmin geri geldi" else "Geri alınacak spin yok")(u.copy(evaluated = null, eval = null, evalActual = -1)) }
            } catch (e: Throwable) {
                val code = (e as? LrError)?.code ?: Codes.STATE1
                publish { it.copy(error = "$code: ${e.message}") }
            }
        }
    }

    /** Ayar değişti: yeni yapılandırmayla oturumu yeniden aç (kayıtlı durum + kilitli tahmin korunur). */
    fun applySettings() {
        scope.launch {
            if (dataset == null || !settings.setupDone) return@launch
            if (!settings.pythonEnabled) { /* Python kapalı: köprü kalsın, çağrılmaz */ }
            else if (py == null && pyError == null) { /* ilk kez açıldı: openSession başlatır */ }
            openSession()
            publish(toast("Ayarlar uygulandı · aktif kilitli tahmin değişmez; yeni tahmin sonraki sonuçta"))
        }
    }

    /** Python'u yeniden başlat: kilitli tahmin etkilenmez; bu sırada Kotlin-only etiketi görünür. */
    fun restartPython() {
        scope.launch {
            try { py?.reset() } catch (_: Exception) { }
            py = null; pyError = null
            openSession()
            publish(toast("Python meclisi yeniden başlatıldı"))
        }
    }

    /** Dışarıdan değişen veri sonrası (içe aktarma, silme, sıfırlama) tam senkron. */
    fun reloadAfterDataChange(rebuild: Boolean = false, message: String? = null) {
        val st = store
        val s = session
        if (s == null || st == null) { openSession(); return }
        setBusy("Veri yeniden işleniyor…")
        try {
            if (rebuild) s.fullRebuild { d, t -> setBusy("Yeniden hesaplama $d / $t") } else s.reload { d, t -> setBusy("Yeni kayıtlar işleniyor $d / $t") }
            s.predictNext()
        } catch (e: Throwable) {
            publish { it.copy(error = "${(e as? LrError)?.code ?: Codes.REPLAY}: ${e.message}") }
        }
        setBusy(null)
        publish { u -> if (message != null) toast(message)(u) else u }
    }

    /** Tamamen yeni dataset aç (örnek veri, içe aktarma, boş başlangıç). Önceki dataset pasif olur, silinmez. */
    fun createDataset(name: String, source: String, synthetic: Boolean, spins: List<Spin>, note: String = ""): Long {
        val now = System.currentTimeMillis()
        val hash = Importer.datasetHash(spins)
        val id = db.runInTransaction(java.util.concurrent.Callable {
            dao.deactivateAll()
            val newId = dao.insertDataset(DatasetE(0, name, now, source, synthetic, true, 1, hash, note))
            for (chunk in spins.chunked(500)) dao.insertSpins(chunk.map { SpinE(0, newId, it.value, it.ts, it.tsType, it.source, 0) })
            dao.insertVersion(DatasetVersionE(0, newId, 1, spins.size, hash, now, "ilk sürüm · $source"))
            newId
        })
        dataset = dao.dataset(id)
        try { py?.reset() } catch (_: Exception) { }
        RoomStore(db, id).log("INFO", null, "DATASET_CREATED", "$name · ${spins.size} spin · $source")
        return id
    }

    fun finishSetup() { settings.setupDone = true }

    /** DataOps: aktif dataset nesnesini güncelle. */
    fun setDataset(d: DatasetE?) { dataset = d }

    /** Aktif dataset satırını yeniden oku (sürüm/hash değişti) ve oturumu yeni dataset nesnesiyle sürdür. */
    fun openSessionDataset() { dataset = dataset?.let { dao.dataset(it.id) } }

    /** Veri tamamen sıfırlanınca kurulum akışına dön. */
    fun restartBoot() {
        session = null; store = null
        _ui.update { EngineUi(phase = "setup", steps = it.steps, champion = settings.championVersion) }
    }
}
