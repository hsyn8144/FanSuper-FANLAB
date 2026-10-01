package fan.superai.v13

/**
 * FAN SUPER v1.3 — ortak alan modeli.
 *
 * Üç analiz seviyesi aynı ham veriden beslenir:
 *   NUMBER : rakam (alfabe boyutu kadar sınıf; bu veri setinde 1..4)
 *   BS     : Büyük/Küçük   (indeks 0 = KÜÇÜK, 1 = BÜYÜK)
 *   OE     : Tek/Çift      (indeks 0 = ÇİFT,  1 = TEK)
 *   COMB   : birleşik yan  (indeks bs*2+oe: 0 SMALL_EVEN, 1 SMALL_ODD, 2 BIG_EVEN, 3 BIG_ODD)
 */
object V13 {
    const val ENSEMBLE_VERSION = 13
    const val STATE_VERSION = 1
    const val REGIMES = 3
    val REGIME_NAMES = arrayOf("REGIME_A", "REGIME_B", "REGIME_C")
}

enum class Axis { NUMBER, BS, OE, COMB }

enum class Group(val tr: String) { KOTLIN("Kotlin"), PYTHON("Python") }

enum class Mode { LIVE, REPLAY }

/**
 * Rakam alfabesi. Varsayılan 1..4 (mevcut v1.2 veri alanı). Büyük = üst yarı
 * (4 sınıfta 3,4; 10 sınıfta 5..9), Tek = rakam değeri tek.
 */
class Alphabet(val min: Int = 1, val size: Int = 4) {
    init { require(size >= 2) }
    val max: Int get() = min + size - 1
    fun valid(number: Int) = number in min..max
    fun idx(number: Int) = number - min
    fun number(idx: Int) = min + idx
    fun bsOf(number: Int): Int = if (idx(number) >= size / 2) 1 else 0
    fun oeOf(number: Int): Int = if (number % 2 != 0) 1 else 0
    fun combOf(number: Int): Int = bsOf(number) * 2 + oeOf(number)
    fun k(axis: Axis): Int = when (axis) { Axis.NUMBER -> size; Axis.BS, Axis.OE -> 2; Axis.COMB -> 4 }
    /** Rakam sınıfı için eksen sınıfı (yalnızca GERÇEK sonucu eksenlere ayırmak için kullanılır). */
    fun actual(axis: Axis, number: Int): Int = when (axis) {
        Axis.NUMBER -> idx(number); Axis.BS -> bsOf(number); Axis.OE -> oeOf(number); Axis.COMB -> combOf(number)
    }
}

enum class BigSmall(val tr: String, val index: Int) {
    BIG("BÜYÜK", 1), SMALL("KÜÇÜK", 0);
    companion object { fun fromIndex(i: Int) = if (i == 1) BIG else SMALL }
}

enum class OddEven(val tr: String, val index: Int) {
    ODD("TEK", 1), EVEN("ÇİFT", 0);
    companion object { fun fromIndex(i: Int) = if (i == 1) ODD else EVEN }
}

enum class CombinedSide(val bigSmall: BigSmall, val oddEven: OddEven) {
    BIG_ODD(BigSmall.BIG, OddEven.ODD), BIG_EVEN(BigSmall.BIG, OddEven.EVEN),
    SMALL_ODD(BigSmall.SMALL, OddEven.ODD), SMALL_EVEN(BigSmall.SMALL, OddEven.EVEN);

    val index: Int get() = bigSmall.index * 2 + oddEven.index
    val display: String get() = bigSmall.tr + " + " + oddEven.tr

    companion object {
        fun of(bs: BigSmall, oe: OddEven): CombinedSide = entries.first { it.bigSmall == bs && it.oddEven == oe }
        fun fromIndex(i: Int): CombinedSide = of(BigSmall.fromIndex(i / 2), OddEven.fromIndex(i % 2))
    }
}

/** Ham fan sonucu. */
data class FanRecord(
    val recordId: Long, val timestamp: Long, val number: Int,
    val bigSmall: BigSmall, val oddEven: OddEven, val combinedSide: CombinedSide
) {
    companion object {
        fun of(recordId: Long, timestamp: Long, number: Int, a: Alphabet = Alphabet()): FanRecord {
            val bs = BigSmall.fromIndex(a.bsOf(number)); val oe = OddEven.fromIndex(a.oeOf(number))
            return FanRecord(recordId, timestamp, number, bs, oe, CombinedSide.of(bs, oe))
        }
    }
}

/** Ortak rakam dağılımı: Kotlin ve Python çıktıları aynı biçimde Meta Ensemble'a girer. */
class PredictionDistribution(
    val modelId: String, val classes: IntArray, val probabilities: DoubleArray,
    val confidence: Double, val entropy: Double, val timestamp: Long
) {
    companion object {
        fun of(modelId: String, classes: IntArray, p: DoubleArray, ts: Long) =
            PredictionDistribution(modelId, classes, p, p.max(), Mx.entropy(p), ts)
    }
}

/** Yan dağılımı: BS (KÜÇÜK,BÜYÜK), OE (ÇİFT,TEK) ve birleşik 4 sınıf. */
class SideDistribution(
    val modelId: String, val bigSmall: DoubleArray, val oddEven: DoubleArray, val combined: DoubleArray,
    val confidence: Double, val entropy: Double, val timestamp: Long
)

/** Bir temel modelin (Kotlin/Python, rakam ya da yan) tek adımlık çıktısı. */
class ModelOutput(
    val id: String, val name: String, val group: Group,
    val number: DoubleArray? = null, val bs: DoubleArray? = null,
    val oe: DoubleArray? = null, val comb: DoubleArray? = null
) {
    fun dist(axis: Axis): DoubleArray? = when (axis) {
        Axis.NUMBER -> number; Axis.BS -> bs; Axis.OE -> oe; Axis.COMB -> comb
    }
}

/** Bir eksendeki tahmin anı girdisi: modeller, dağılımları ve KULLANILAN ağırlıklar. */
class AxisInput(
    val ids: Array<String>, val names: Array<String>, val groups: Array<Group>,
    val dists: Array<DoubleArray>, val weights: DoubleArray, val tau: Double
) {
    val size: Int get() = ids.size
}

/**
 * Tahmin anında bilinen her şey. Kilitle birlikte saklanır; karşı-olgusal analiz bunu kullanır.
 * [pairMode]: 0 otomatik (konformal), 1 her zaman çift, 2 her zaman tek — v1.4'te kilitli
 * tahminin kaç rakam gösterdiğini belirleyen ayar; kilitle saklanır ki geri yüklemede aynı
 * gösterim yeniden üretilsin.
 */
class PredictionContext(
    val axes: Array<AxisInput>, val regime: Int, val blendProduct: Double, val pairMode: Int = 0
) {
    fun axis(a: Axis) = axes[a.ordinal]
}

data class ModelContribution(
    val axis: Axis, val modelId: String, val name: String, val group: Group,
    val weight: Double, val favors: Int, val probOfFinal: Double, val share: Double
)

data class PredictionExplanation(
    val numberPositive: List<String>, val numberNegative: List<String>,
    val sidePositive: List<String>, val sideNegative: List<String>,
    val sideDetail: List<String>
) {
    fun toText(): String = buildString {
        append("RAKAM\n"); numberPositive.forEach { append("+ $it\n") }; numberNegative.forEach { append("- $it\n") }
        append("YAN\n"); sidePositive.forEach { append("+ $it\n") }; sideNegative.forEach { append("- $it\n") }
        sideDetail.forEach { append("· $it\n") }
    }
}

/**
 * Sistemin dışarı verdiği TEK nihai sonuç. Kotlin/Python ayrıntıları metadata altındadır.
 *
 * v1.4 ek alanları (kilit geri yüklenirken [FanBrain] tarafından yeniden üretilir, ayrıca
 * saklanmaz):
 *  - [numberSecondary]: çift (2 rakam) kararında ikinci aday; null → gösterim tek rakam.
 *  - [kotlinNumberProbs] / [pythonNumberProbs]: meclislerin rakam eksenindeki grup karışımı
 *    (overlay'in K ve Py satırları). Python yoksa null → "--".
 */
class FinalPrediction(
    val number: Int,
    val numberDistribution: PredictionDistribution,
    val bigSmall: BigSmall,
    val oddEven: OddEven,
    val sideLabel: String,
    val sideDistribution: SideDistribution,
    val confidence: Double,
    val sideConfidence: Double,
    val entropy: Double,
    val regime: String,
    val ensembleVersion: Int,
    val predictionTimestamp: Long,
    val predictionLockId: String,
    val predictionSequence: Int,
    val lastKnownRecordId: Long,
    val lockHash: Long,
    val contributions: List<ModelContribution>,
    val explanation: PredictionExplanation,
    val numberSecondary: Int? = null,
    val kotlinNumberProbs: DoubleArray? = null,
    val pythonNumberProbs: DoubleArray? = null
) {
    val side: CombinedSide get() = CombinedSide.of(bigSmall, oddEven)
    val sideDisplay: String get() = side.display
    /** Çift kararda "1/2", tek kararda "1" — overlay ve ana ekran rakam satırı. */
    val numberLabel: String get() = if (numberSecondary == null) "$number" else "$number/$numberSecondary"
    /** Kısa yan gösterimi: "B•T" / "K•Ç" gibi (v1.4 overlay yan satırı). */
    val sideShort: String get() = (if (bigSmall == BigSmall.BIG) "B" else "K") + "•" + (if (oddEven == OddEven.ODD) "T" else "Ç")
}

/** Değerlendirme sonucu: tahmin kaydını DEĞİŞTİRMEZ, ayrı bir kayıttır. */
class EvaluationResult(
    val sequence: Int, val recordId: Long, val actualTimestamp: Long, val actualNumber: Int,
    val predictedNumber: Int, val predictedSide: CombinedSide,
    val numberHit: Boolean, val top2Hit: Boolean, val bigSmallHit: Boolean, val oddEvenHit: Boolean, val sideHit: Boolean,
    val numberLogLoss: Double, val sideLogLoss: Double, val regime: String, val lockId: String,
    val evaluated: Boolean
)

class FanException(val code: String, message: String) : RuntimeException("$code: $message")
class LeakageViolation(message: String) : RuntimeException("${ErrorCodes.LEAK_FUTURE}: $message")

object ErrorCodes {
    const val LEAK_FUTURE = "FAN-E-LEAK-001"
    const val LOCK_DUPLICATE = "FAN-E-LOCK-001"
    const val LOCK_TAMPERED = "FAN-E-LOCK-002"
    const val LOCK_STALE = "FAN-E-LOCK-003"
    const val SEQUENCE = "FAN-E-SEQ-001"
    const val STATE_LOAD = "FAN-E-STATE-001"
    const val STATE_SAVE = "FAN-E-STATE-002"
    const val PYTHON = "FAN-E-PY-001"
    const val DB = "FAN-E-DB-001"
    const val REPLAY = "FAN-E-REPLAY-001"
    const val VALIDATE = "FAN-E-REC-001"
}
