package fan.lightningroulette.core

/** Ham spin: analiz sırası EN ESKİ → EN YENİ'dir (arayüz tersini gösterir). */
data class Spin(val id: Long, val value: Int, val ts: Long, val source: String = "LIVE", val tsType: String = "REAL")

/** Aday: merkez numara + k komşu (+ yön). `contrib` yalnızca gerçekten hesaplanan skor katkılarını taşır (uydurma neden yok). */
data class Candidate(
    val n: Int, val k: Int, val dir: Dir, val rank: Int,
    val p: Double,                    // kalibre P(merkez = sonuç)
    val mass: Double,                 // aralığın toplam olasılığı
    val sector: Int, val region: Int,
    val contrib: Map<String, Double>  // skor adı → log-odds katkısı
) {
    val span: IntArray get() = Wheel.span(n, k, dir)
    val label: String get() = if (dir == Dir.BI) "$n-k$k" else "$n-${dir.code}$k"
    val kLabel: String get() = if (dir == Dir.BI) "k$k" else "${dir.code}$k"
    fun toMap(): Map<String, Any?> = mapOf("n" to n, "k" to k, "d" to dir.code, "rank" to rank, "p" to p, "mass" to mass, "sector" to sector, "region" to region, "contrib" to contrib)
    companion object {
        fun fromMap(m: Map<String, Any?>): Candidate = Candidate(
            m["n"].jint(), m["k"].jint(), Dir.of(m["d"].jstr("bi")), m["rank"].jint(), m["p"].jnum(), m["mass"].jnum(),
            m["sector"].jint(), m["region"].jint(), m["contrib"].jmap().mapValues { it.value.jnum() })
    }
}

/** Bir masa kategorisinde modelin seçtiği taraf. */
data class TableCall(val cat: Int, val cls: Int, val p: Double, val base: Double, val probs: DoubleArray) {
    val label: String get() = TableCats.LABELS[cat][cls]
    val pick: String get() = TableCats.PICKS[cat][cls]
    fun toMap(): Map<String, Any?> = mapOf("cat" to cat, "cls" to cls, "p" to p, "base" to base, "probs" to probs)
    companion object {
        fun fromMap(m: Map<String, Any?>) = TableCall(m["cat"].jint(), m["cls"].jint(), m["p"].jnum(), m["base"].jnum(), m["probs"].jdoubles())
    }
}

/** Python meclisinden gelen (doğrulanmış) çıktı. */
class PyOut(val ids: List<String>, val probs: List<DoubleArray>)

/** Tek bir tahminin tüm içeriği. Kilitlendikten sonra DEĞİŞMEZ. */
class PredictionCore(
    val pFinal: DoubleArray,
    val pRaw: DoubleArray,                // kalibrasyon (sıcaklık) öncesi
    val pKotlin: DoubleArray,
    val pPython: DoubleArray?,
    val memberIds: List<String>, val memberProbs: List<DoubleArray>,
    val pyIds: List<String>, val pyProbs: List<DoubleArray>,
    val candidates: List<Candidate>,
    val table: List<TableCall>,
    val pyStatus: String,                 // OK | ERROR | OFF
    val weightsK: DoubleArray,
    val weightsP: DoubleArray?,
    val wKotlin: Double,
    val tau: Double
) {
    val top: Candidate? get() = candidates.firstOrNull()
    val p5: Double get() = candidates.sumOf { it.p }
    fun coverage(): Int { val s = HashSet<Int>(); for (c in candidates) for (x in c.span) s.add(x); return s.size }
}

/** Değerlendirme: exact ve candidate kesinlikle ayrı (Prompt §64). */
data class Eval(
    val actual: Int,
    val exact: Boolean,
    val candidate: Boolean, val candRank: Int,   // candRank: 1..5, yoksa 0
    val neighbor: Boolean,
    val sector: Boolean, val region: Boolean,
    val tableHits: BooleanArray,
    val coverage: Int,
    val pActual: Double,                 // nihai dağılımın gerçek sonuca verdiği olasılık
    val logLoss: Double,
    val candCount: Int = 5,              // tahmindeki aday sayısı (Candidate tabanı = candCount / 37)
    val sectorBase: Double = 0.0,        // en üst adayın sektörünün boyut düzeltmeli tabanı
    val regionBase: Double = 0.0
) {
    val tableCount: Int get() = tableHits.count { it }
    fun toMap(): Map<String, Any?> = mapOf("actual" to actual, "exact" to exact, "cand" to candidate, "rank" to candRank, "nei" to neighbor,
        "sec" to sector, "reg" to region, "tab" to tableHits.map { if (it) 1 else 0 }, "cov" to coverage, "pa" to pActual, "ll" to logLoss,
        "nc" to candCount, "sb" to sectorBase, "rb" to regionBase)
    companion object {
        fun fromMap(m: Map<String, Any?>) = Eval(m["actual"].jint(), m["exact"].jbool(), m["cand"].jbool(), m["rank"].jint(), m["nei"].jbool(),
            m["sec"].jbool(), m["reg"].jbool(), BooleanArray(5) { m["tab"].jlist().getOrNull(it).jint() == 1 }, m["cov"].jint(), m["pa"].jnum(), m["ll"].jnum(),
            m["nc"].jint(5), m["sb"].jnum(0.0), m["rb"].jnum(0.0))
    }
}

/** Hata kodları: FanSuper'daki FAN-E-* yerine LR-E-* (Prompt §56, §69). */
class LrError(val code: String, message: String) : RuntimeException("$code: $message")
object Codes {
    const val LEAK = "LR-E-LEAK-001"; const val LOCK1 = "LR-E-LOCK-001"; const val LOCK2 = "LR-E-LOCK-002"; const val LOCK3 = "LR-E-LOCK-003"
    const val SEQ = "LR-E-SEQ-001"; const val STATE1 = "LR-E-STATE-001"; const val STATE2 = "LR-E-STATE-002"; const val PY = "LR-E-PY-001"
    const val DB = "LR-E-DB-001"; const val REPLAY = "LR-E-REPLAY-001"; const val REC = "LR-E-REC-001"; const val IMP = "LR-E-IMP-001"
    const val EXP = "LR-E-EXP-001"; const val REPRO = "LR-E-REPRO-001"
}
