package fan.lightningroulette.core

import kotlin.math.abs
import kotlin.math.min

/** Komşu yönü: BI = iki yön (k1/k2/k3) · L = saat yönünün tersi · R = saat yönü. */
enum class Dir(val code: String) {
    BI("bi"), L("L"), R("R");
    companion object { fun of(s: String): Dir = values().firstOrNull { it.code.equals(s, true) || it.name.equals(s, true) } ?: BI }
}

/** Avrupa ruleti: gerçek fiziksel wheel sırası (saat yönü). Masa sırasıyla karıştırılmaz (Prompt §2). */
object Wheel {
    const val N = 37
    val ORDER = intArrayOf(0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26)
    val POS: IntArray = IntArray(N).also { for (i in ORDER.indices) it[ORDER[i]] = i }
    private val REDS = intArrayOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
    val RED: BooleanArray = BooleanArray(N).also { for (r in REDS) it[r] = true }

    /** 0 = yeşil, 1 = kırmızı, 2 = siyah */
    fun color(n: Int): Int = if (n == 0) 0 else if (RED[n]) 1 else 2

    fun valid(n: Int) = n in 0..36

    /** n sayısının wheel sırasına göre k komşu aralığı (soldan sağa, indeks artan = saat yönü). */
    fun span(n: Int, k: Int, d: Dir = Dir.BI): IntArray {
        val i = POS[n]
        val lo = if (d == Dir.R) 0 else -k
        val hi = if (d == Dir.L) 0 else k
        return IntArray(hi - lo + 1) { ORDER[((i + lo + it) % N + N) % N] }
    }

    fun cw(a: Int, b: Int): Int = ((POS[b] - POS[a]) % N + N) % N
    fun ccw(a: Int, b: Int): Int = cw(b, a)
    fun circ(a: Int, b: Int): Int = min(cw(a, b), ccw(a, b))
    fun circPos(pa: Int, pb: Int): Int { val d = abs(pa - pb); return min(d, N - d) }

    /** Saat yönüne işaretli en kısa ofset: −18..+18 */
    fun signedOffset(a: Int, b: Int): Int { val d = cw(a, b); return if (d > 18) d - N else d }
}

/** Sektör tanımı (wheel sırasına göre ardışık dilimler); konfigüre edilebilir (Prompt §3). */
class Sectors(val bounds: IntArray = DEFAULT_BOUNDS) {
    val count: Int = bounds.size - 1
    val of: IntArray = IntArray(Wheel.N)
    private val sizes: IntArray = IntArray(count) { bounds[it + 1] - bounds[it] }

    init {
        for (s in 0 until count) for (i in bounds[s] until bounds[s + 1]) of[Wheel.ORDER[i]] = s
    }

    fun size(s: Int): Int = sizes[s]
    fun members(s: Int): IntArray = IntArray(sizes[s]) { Wheel.ORDER[bounds[s] + it] }
    fun baseline(s: Int): Double = sizes[s].toDouble() / Wheel.N

    companion object {
        /** S1 = 5 cep (0 dahil), S2…S9 = 4 cep. */
        val DEFAULT_BOUNDS = intArrayOf(0, 5, 9, 13, 17, 21, 25, 29, 33, 37)
        val DEFAULT = Sectors()
        fun fromBounds(b: List<Int>): Sectors? {
            if (b.size < 3 || b.first() != 0 || b.last() != Wheel.N) return null
            for (i in 1 until b.size) if (b[i] <= b[i - 1]) return null
            return Sectors(b.toIntArray())
        }
    }
}

/** Klasik bölgeler. 0 = VOISINS, 1 = TIERS, 2 = ORPHELINS */
object Regions {
    val NAMES = arrayOf("VOISINS", "TIERS", "ORPHELINS")
    val VOISINS = intArrayOf(22, 18, 29, 7, 28, 12, 35, 3, 26, 0, 32, 15, 19, 4, 21, 2, 25)
    val TIERS = intArrayOf(27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33)
    val ORPH = intArrayOf(17, 34, 6, 1, 20, 14, 31, 9)
    val of: IntArray = IntArray(Wheel.N).also {
        for (n in VOISINS) it[n] = 0
        for (n in TIERS) it[n] = 1
        for (n in ORPH) it[n] = 2
    }
    fun size(r: Int) = when (r) { 0 -> VOISINS.size; 1 -> TIERS.size; else -> ORPH.size }
    fun members(r: Int) = when (r) { 0 -> VOISINS; 1 -> TIERS; else -> ORPH }
}

/** Masa kategorileri (wheel'den bağımsız koordinat sistemi). Sıfır hiçbir sınıfa girmez. */
object TableCats {
    const val COLOR = 0; const val PARITY = 1; const val HIGHLOW = 2; const val DOZEN = 3; const val COLUMN = 4
    val IDS = arrayOf("COLOR", "PARITY", "HIGH/LOW", "DOZEN", "COLUMN")
    val LABELS = arrayOf(
        arrayOf("KIRMIZI", "SİYAH"),
        arrayOf("ÇİFT", "TEK"),
        arrayOf("KÜÇÜK", "BÜYÜK"),
        arrayOf("1. DOZEN", "2. DOZEN", "3. DOZEN"),
        arrayOf("1. COLUMN", "2. COLUMN", "3. COLUMN")
    )
    val PICKS = arrayOf(arrayOf("Kırmızı", "Siyah"), arrayOf("Çift", "Tek"), arrayOf("1–18", "19–36"), arrayOf("1–12", "13–24", "25–36"), arrayOf("Sütun 1", "Sütun 2", "Sütun 3"))
    fun classes(cat: Int) = LABELS[cat].size
    /** Sınıf büyüklüğü / 37 */
    fun baseline(cat: Int): Double = (if (classes(cat) == 2) 18 else 12).toDouble() / Wheel.N

    /** n sayısının cat kategorisindeki sınıfı; sıfır için −1. */
    fun classOf(cat: Int, n: Int): Int {
        if (n == 0) return -1
        return when (cat) {
            COLOR -> if (Wheel.RED[n]) 0 else 1
            PARITY -> if (n % 2 == 0) 0 else 1
            HIGHLOW -> if (n <= 18) 0 else 1
            DOZEN -> (n - 1) / 12
            else -> (n - 1) % 3
        }
    }
}
