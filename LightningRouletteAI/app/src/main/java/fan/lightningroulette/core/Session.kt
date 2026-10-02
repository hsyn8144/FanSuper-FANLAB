package fan.lightningroulette.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ───────────────────────── Kalıcılık ve Python soyutlamaları (Android'de Room/Chaquopy ile gerçeklenir; testte bellek içi)

class StoredPrediction(val id: Long, val refCount: Int, val createdAt: Long, val json: String, val pyStatus: String, val modelVersion: String)

interface Store {
    fun spins(): List<Spin>                                       // EN ESKİ → EN YENİ
    fun addSpin(value: Int, ts: Long, source: String, tsType: String): Spin
    fun deleteLastSpin(): Spin?
    fun prediction(refCount: Int): StoredPrediction?
    /** Aynı refCount için ikinci kayıt YASAK (unique): null döner → LR-E-LOCK-002. */
    fun savePrediction(refCount: Int, createdAt: Long, json: String, pyStatus: String, modelVersion: String): StoredPrediction?
    fun deletePredictionsAfter(refCount: Int)                     // refCount > x
    fun saveEvaluation(predictionId: Long, spinId: Long, ev: Eval)
    fun deleteEvaluationFor(spinId: Long)
    fun getState(key: String): String?
    fun putState(key: String, value: String)
    fun deleteState(key: String)
    fun log(level: String, code: String?, event: String, detail: String)
    /** Tek transaction: istisna → tüm yazmalar geri alınır. */
    fun <T> tx(block: () -> T): T
}

interface PyHost {
    /** window: son ≤ 4000 değer (en eski → en yeni); offset: pencerenin MUTLAK başlangıç sırası. Hata → istisna. */
    fun predict(window: IntArray, offset: Int): PyOut
}

/** Kotlin, Python sonucunu doğrulamadan kullanmaz (Prompt §57): şema, 0–36 aralığı, olasılık toplamı. */
object PyValidator {
    fun check(o: PyOut): String? {
        if (o.probs.isEmpty() || o.probs.size != o.ids.size) return "şema: üye sayısı ${o.probs.size} ≠ ${o.ids.size}"
        for ((i, p) in o.probs.withIndex()) {
            if (p.size != Wheel.N) return "şema: ${o.ids[i]} boyutu ${p.size} ≠ ${Wheel.N} (0–36 aralığı)"
            var s = 0.0
            for (x in p) { if (x.isNaN() || x.isInfinite() || x < 0.0) return "${o.ids[i]}: geçersiz olasılık"; s += x }
            if (abs(s - 1.0) > 1e-3) return "${o.ids[i]}: olasılık toplamı $s"
        }
        return null
    }
}

val PY_TITLES: Map<String, String> = mapOf(
    "lstm" to "LSTM (BPTT)", "transformer" to "Mini Transformer", "cnn" to "1D-CNN", "gboost" to "Gradient Boosting",
    "hmm" to "HMM (sektör rejimleri)", "knn_dtw" to "kNN-DTW", "context" to "Bağlam modeli", "motif" to "Motif keşfi"
)

// ───────────────────────── Kilitli tahminin kalıcı/arayüz görünümü

class Vote(val council: String, val id: String, val title: String, val top: Int, val p: Double, val weight: Double)

class PredView(
    val id: Long, val refCount: Int, val createdAt: Long, val modelVersion: String, val pyStatus: String,
    val candidates: List<Candidate>, val table: List<TableCall>,
    val pFinal: DoubleArray, val pKotlin: DoubleArray, val pPython: DoubleArray?,
    val votes: List<Vote>, val wKotlin: Double, val tau: Double, val coverage: Int
) {
    val code: String get() = "PRED-" + id.toString().padStart(7, '0')
    val p5: Double get() = candidates.sumOf { it.p }
    /** Kotlin ve Python meclisi top-1 oyları farklı mı? (yalnızca ikisi de varsa) */
    val councilsDisagree: Boolean get() {
        val k = pKotlin; val p = pPython ?: return false
        var a = 0; var b = 0
        for (i in 1 until k.size) { if (k[i] > k[a]) a = i; if (p[i] > p[b]) b = i }
        return a != b
    }

    /** Değerlendirme için gereken parçalarla çekirdek nesne (resmi, kilitli tahminden). */
    fun asCore(): PredictionCore = PredictionCore(pFinal, pFinal, pKotlin, pPython, emptyList(), emptyList(), emptyList(), emptyList(), candidates, table, pyStatus,
        DoubleArray(0), null, wKotlin, tau)

    fun toJson(): String = Json.stringify(mapOf(
        "v" to 1, "mv" to modelVersion, "py" to pyStatus, "cands" to candidates.map { it.toMap() }, "tab" to table.map { it.toMap() },
        "pf" to r6(pFinal), "pk" to r6(pKotlin), "pp" to pPython?.let { r6(it) }, "wk" to wKotlin, "tau" to tau, "cov" to coverage,
        "votes" to votes.map { listOf(it.council, it.id, it.title, it.top, it.p, it.weight) }
    ))

    companion object {
        private fun r6(a: DoubleArray): List<Double> = a.map { Math.round(it * 1e6) / 1e6 }

        fun of(id: Long, refCount: Int, createdAt: Long, modelVersion: String, c: PredictionCore, kTitles: List<String>): PredView {
            fun top(p: DoubleArray): Int { var b = 0; for (i in 1 until p.size) if (p[i] > p[b]) b = i; return b }
            val votes = ArrayList<Vote>()
            for (i in c.memberIds.indices) {
                val p = c.memberProbs[i]; val t = top(p)
                votes.add(Vote("K", c.memberIds[i], kTitles.getOrElse(i) { c.memberIds[i] }, t, p[t], c.weightsK.getOrElse(i) { 0.0 }))
            }
            for (j in c.pyIds.indices) {
                val p = c.pyProbs[j]; val t = top(p)
                votes.add(Vote("P", c.pyIds[j], PY_TITLES[c.pyIds[j]] ?: c.pyIds[j], t, p[t], c.weightsP?.getOrNull(j) ?: 0.0))
            }
            return PredView(id, refCount, createdAt, modelVersion, c.pyStatus, c.candidates, c.table, c.pFinal, c.pKotlin, c.pPython, votes, c.wKotlin, c.tau, c.coverage())
        }

        fun fromStored(sp: StoredPrediction): PredView {
            val m = Json.parse(sp.json).jmap()
            val votes = m["votes"].jlist().map { e -> val l = e.jlist(); Vote(l[0].jstr(), l[1].jstr(), l[2].jstr(), l[3].jint(), l[4].jnum(), l[5].jnum()) }
            val cands = m["cands"].jlist().map { Candidate.fromMap(it.jmap()) }
            val set = HashSet<Int>(); for (c in cands) for (x in c.span) set.add(x)
            return PredView(sp.id, sp.refCount, sp.createdAt, m["mv"].jstr(sp.modelVersion), m["py"].jstr(sp.pyStatus), cands, m["tab"].jlist().map { TableCall.fromMap(it.jmap()) },
                m["pf"].jdoubles(), m["pk"].jdoubles(), m["pp"]?.jdoubles(), votes, m["wk"].jnum(1.0), m["tau"].jnum(1.0), m["cov"].jint(set.size))
        }
    }
}

sealed class EnterResult {
    class Entered(val spin: Spin, val eval: Eval?, val evaluated: PredView?, val next: PredView?) : EnterResult()
    class Ignored(val reason: String) : EnterResult()
    class Rejected(val code: String, val reason: String) : EnterResult()
}

/**
 * Canlı oturum: tahmin → LOCK → sonuç açılır → değerlendirme → öğrenme (Prompt §14, §47).
 * Tüm çağrılar tek bir motor iş parçacığından yapılmalıdır; kalıcılık [Store] üzerinden, ENTER tek transaction'dır.
 */
class Session(
    val store: Store, val cfg: BrainConfig, private val py: PyHost? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }, val modelVersion: String = "v1.0.0"
) {
    companion object {
        const val KEY = "brain"; const val PREV = "brain.prev"; const val WARM_START = 2000
    }
    var brain: Brain = Brain(cfg); private set
    private val values = ArrayList<Int>()
    private val times = ArrayList<Long>()
    var learned = 0; private set
    var view: PredView? = null; private set
    var pyStatus: String = "OFF"; private set
    private var core: PredictionCore? = null          // yalnızca bellekte: üye olasılıkları (Hedge öğrenmesi için)
    private var lastBlob: String = ""

    val size: Int get() = values.size
    fun valuesArray(): IntArray = values.toIntArray()
    fun last(n: Int): List<Int> = values.takeLast(n)
    val kTitles: List<String> get() = brain.members.map { it.title }

    private fun saveState(n: Int, blob: String) { store.putState(KEY, Json.stringify(mapOf("n" to n, "full" to blob))) }

    /** Açılış: kayıtlı durumu yükle, geride kaldıysa YALNIZCA yeni kayıtları işle (tail catch-up); bozuksa yeniden kur. */
    fun open(progress: ((Int, Int) -> Unit)? = null) {
        values.clear(); times.clear()
        for (s in store.spins()) { values.add(s.value); times.add(s.ts) }
        for (i in 1 until times.size) if (times[i - 1] > times[i]) throw LrError(Codes.SEQ, "kayıt sırası bozuk (#$i): zaman geriye gidiyor")
        var ok = false
        val raw = store.getState(KEY)
        if (raw != null) {
            try {
                val m = Json.parse(raw).jmap(); val n = m["n"].jint(); val full = m["full"].jstr()
                val b = Brain(cfg)
                if (n <= values.size && b.restoreFull(full)) { brain = b; learned = n; lastBlob = full; ok = true }
                else store.log("WARN", Codes.STATE2, "STATE_STALE", "kayıtlı durum uyumsuz (n=$n, spin=${values.size}); yeniden kurulacak")
            } catch (e: Exception) { store.log("ERROR", Codes.STATE1, "STATE_LOAD_FAILED", e.message ?: e.javaClass.simpleName) }
        }
        if (!ok) rebuild(progress) else if (learned < values.size) catchUp(progress)
        core = null; view = null
        loadPrediction()
    }

    /** Kotlin-only sıcak başlangıç: son ≤ 2000 adım üzerinde (sızıntısız) replay. Python meclisi ilk canlı tahminde kendini senkronlar. */
    fun rebuild(progress: ((Int, Int) -> Unit)? = null) {
        brain = Brain(cfg); learned = values.size
        if (values.size > cfg.minSample) {
            val res = ReplayEngine(cfg).run(valuesArray(), times.toLongArray(), max(cfg.minSample, values.size - WARM_START), Int.MAX_VALUE, values.size, null, null, progress, brain)
            brain = res.brain
        }
        lastBlob = brain.fullStateJson(); saveState(values.size, lastBlob); store.deleteState(PREV)
        store.log("INFO", null, "STATE_REBUILT", "spin=${values.size}")
    }

    private fun catchUp(progress: ((Int, Int) -> Unit)?) {
        val from = learned; val total = values.size
        if (total > cfg.minSample) {
            val res = ReplayEngine(cfg).run(valuesArray(), times.toLongArray(), from, Int.MAX_VALUE, total, null, null, progress, brain)
            brain = res.brain
        }
        learned = total; lastBlob = brain.fullStateJson(); saveState(total, lastBlob)
        store.log("INFO", null, "STATE_CAUGHT_UP", "$from → $total")
    }

    private fun loadPrediction() {
        val sp = store.prediction(values.size)
        view = sp?.let { PredView.fromStored(it) }
    }

    private fun pyCall(h: IntArray, n: Int): Pair<PyOut?, String> {
        if (py == null || cfg.kotlinWeight >= 0.999) return null to "OFF"
        return try {
            val out = py.predict(h, n - h.size)
            val bad = PyValidator.check(out)
            if (bad != null) { store.log("ERROR", Codes.PY, "PY_INVALID_OUTPUT", bad); null to "ERROR" } else out to "OK"
        } catch (e: Exception) {
            store.log("ERROR", Codes.PY, "PY_FAILED", e.message ?: e.javaClass.simpleName); null to "ERROR"
        }
    }

    /** İdempotent: aynı spin sayısı için ikinci tahmin ÜRETİLMEZ, kilitli tahmin değişmez. Eşik altında null. */
    fun predictNext(): PredView? {
        val n = values.size
        if (n < cfg.minSample) { view = null; core = null; return null }
        view?.let { if (it.refCount == n) return it }
        val existing = store.prediction(n)
        if (existing != null) { view = PredView.fromStored(existing); return view }
        val h = Brain.cut(valuesArray(), n)
        val (out, st) = pyCall(h, n)
        pyStatus = st
        val c = brain.predict(h, out, st)
        val bad = checkCore(c)
        if (bad != null) throw LrError(Codes.STATE1, "tahmin geçersiz: $bad")
        val pv0 = PredView.of(0, n, clock(), modelVersion, c, kTitles)
        val sp = store.savePrediction(n, pv0.createdAt, pv0.toJson(), st, modelVersion)
        if (sp == null) {
            store.log("ERROR", Codes.LOCK2, "LOCK_DUPLICATE", "refCount=$n için ikinci tahmin engellendi")
            val ex = store.prediction(n) ?: throw LrError(Codes.LOCK2, "kilitli tahmin okunamadı")
            view = PredView.fromStored(ex); return view
        }
        core = c
        view = PredView.of(sp.id, n, sp.createdAt, modelVersion, c, kTitles)
        return view
    }

    private fun checkCore(c: PredictionCore): String? {
        if (c.candidates.size !in 1..5) return "aday sayısı ${c.candidates.size}"
        if (c.candidates.any { it.n !in 0..36 }) return "aday 0–36 dışı"
        var s = 0.0; for (x in c.pFinal) { if (x.isNaN() || x < 0) return "olasılık geçersiz"; s += x }
        return if (abs(s - 1.0) > 1e-6) "olasılık toplamı $s" else null
    }

    /**
     * ENTER: tek transaction. expectCount = arayüzün gördüğü spin sayısı; uyuşmazsa (çift ENTER) yoksayılır.
     * Akış: kayıt → (kilitli tahmin varsa) değerlendirme → öğrenme → yeni tahmin + LOCK.
     */
    fun enter(value: Int, expectCount: Int = values.size): EnterResult {
        if (value !in 0..36) { store.log("WARN", Codes.REC, "ENTER_REJECTED", "değer=$value 0–36 dışı"); return EnterResult.Rejected(Codes.REC, "0–36 dışı") }
        if (expectCount != values.size) { store.log("INFO", null, "DOUBLE_ENTER_IGNORED", "beklenen=$expectCount gerçek=${values.size}"); return EnterResult.Ignored("çift ENTER yoksayıldı") }
        val n = values.size
        val official: StoredPrediction? = if (n >= cfg.minSample) store.prediction(n) else null
        val h = Brain.cut(valuesArray(), n)
        var learnCore: PredictionCore? = null
        if (n >= cfg.minSample) {
            learnCore = if (view?.refCount == n && core != null) core else { val (o, st) = pyCall(h, n); brain.predict(h, o, st) }
        }
        val t = if (times.isEmpty()) clock() else max(clock(), times[times.size - 1] + 1)
        val prev = lastBlob
        var ev: Eval? = null; var sp: Spin? = null
        try {
            store.tx {
                val added = store.addSpin(value, t, "LIVE", "REAL"); sp = added
                if (official != null) { ev = Evaluator.evaluate(PredView.fromStored(official).asCore(), value, cfg.sectors); store.saveEvaluation(official.id, added.id, ev!!) }
                if (learnCore != null) brain.learn(learnCore, h, value)
                val blob = brain.fullStateJson()
                store.putState(PREV, Json.stringify(mapOf("n" to n, "full" to prev)))
                saveState(n + 1, blob)
                lastBlob = blob
            }
        } catch (e: Exception) {
            if (prev.isNotEmpty()) brain.restoreFull(prev); lastBlob = prev
            store.log("ERROR", Codes.DB, "ENTER_FAILED", e.message ?: e.javaClass.simpleName)
            throw LrError(Codes.DB, "kayıt başarısız, durum korundu: ${e.message}")
        }
        val spin = sp!!
        values.add(value); times.add(spin.ts); learned = values.size
        val evaluated = view?.takeIf { it.refCount == n }?.let { it } ?: official?.let { PredView.fromStored(it) }
        core = null; view = null
        store.log("INFO", null, "SPIN_ENTERED", "#${spin.id} = $value (n=${values.size})")
        val next = predictNext()
        persistAfterPredict()
        return EnterResult.Entered(spin, ev, if (ev != null) evaluated else null, next)
    }

    /**
     * Bazı üyeler (ör. Kotlin ML) yeniden uydurmayı TAHMİN sırasında yapar; ENTER'da kaydedilen durum tahminden öncedir.
     * Yeni tahminden sonra durumu yeniden kaydederiz: yeniden başlatmada bellekteki durumla birebir aynı olur ve
     * bir sonraki geri alma anlık görüntüsü (PREV) da bu tutarlı durumdan alınır.
     */
    private fun persistAfterPredict() {
        try {
            val blob = brain.fullStateJson()
            if (blob != lastBlob) { lastBlob = blob; saveState(values.size, blob) }
        } catch (e: Exception) { store.log("WARN", Codes.STATE2, "STATE_RESAVE_FAILED", e.message ?: "") }
    }

    /** Son spini geri al: önceki anlık görüntüye dön, aynı kilitli tahmin geri gelir (Prompt §60). */
    fun undoLast(): Spin? {
        if (values.isEmpty()) return null
        val n = values.size
        var removed: Spin? = null; var restored = false
        store.tx {
            removed = store.deleteLastSpin()
            val r = removed ?: throw LrError(Codes.DB, "silinecek spin yok")
            store.deleteEvaluationFor(r.id)
            store.deletePredictionsAfter(n - 1)
            val pv = store.getState(PREV)
            if (pv != null) {
                val m = Json.parse(pv).jmap(); val full = m["full"].jstr()
                if (m["n"].jint() == n - 1 && full.isNotEmpty() && brain.restoreFull(full)) {
                    restored = true; lastBlob = full; saveState(n - 1, full); store.deleteState(PREV)
                }
            }
        }
        values.removeAt(values.size - 1); times.removeAt(times.size - 1); learned = values.size
        if (!restored) rebuild(null)
        core = null; view = null
        loadPrediction()
        store.log("INFO", null, "SPIN_UNDONE", "#${removed?.id} (n=${values.size}) anlık görüntü=${if (restored) "geri yüklendi" else "yeniden kuruldu"}")
        return removed
    }

    /** Dışarıdan veri eklendi/silindi: oturumu yeniden aç (yalnızca yeni kayıtlar işlenir; bozuksa tam yeniden kurulum). */
    fun reload(progress: ((Int, Int) -> Unit)? = null) = open(progress)

    /** Durum bozuldu / veri düzenlendi: tam yeniden kurulum + tahmin kilidini yeniden aç. */
    fun fullRebuild(progress: ((Int, Int) -> Unit)? = null) {
        values.clear(); times.clear()
        for (s in store.spins()) { values.add(s.value); times.add(s.ts) }
        store.deletePredictionsAfter(-1)
        rebuild(progress); core = null; view = null; loadPrediction()
    }
}
