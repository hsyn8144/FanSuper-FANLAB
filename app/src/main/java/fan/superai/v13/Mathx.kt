package fan.superai.v13

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Küçük sayısal yardımcılar (saf Kotlin). */
object Mx {
    const val FLOOR = 1e-9

    fun entropy(p: DoubleArray): Double {
        var s = 0.0
        for (q in p) { val x = max(q, 1e-12); s -= x * ln(x) }
        return s
    }

    fun normEntropy(p: DoubleArray): Double = if (p.size < 2) 0.0 else entropy(p) / ln(p.size.toDouble())

    fun logLoss(p: DoubleArray, a: Int): Double = -ln(max(p[a], FLOOR))

    fun brier(p: DoubleArray, a: Int): Double {
        var s = 0.0
        for (i in p.indices) { val y = if (i == a) 1.0 else 0.0; s += (p[i] - y) * (p[i] - y) }
        return s
    }

    fun argmax(p: DoubleArray): Int {
        var b = 0
        for (i in 1 until p.size) if (p[i] > p[b]) b = i
        return b
    }

    /** İkinci en yüksek sınıf (eşitlikte düşük indeks önce). */
    fun argmaxInt(c: IntArray): Int {
        var b = 0
        for (i in 1 until c.size) if (c[i] > c[b]) b = i
        return b
    }

    fun top2(p: DoubleArray): IntArray {
        val o = p.indices.sortedWith(compareByDescending<Int> { p[it] }.thenBy { it })
        return intArrayOf(o[0], o[1])
    }

    fun normalize(p: DoubleArray, floor: Double = 1e-4): DoubleArray {
        var s = 0.0
        for (i in p.indices) { if (p[i].isNaN() || p[i] < floor) p[i] = floor; s += p[i] }
        for (i in p.indices) p[i] /= s
        return p
    }

    fun uniform(k: Int) = DoubleArray(k) { 1.0 / k }

    fun softmax(z: DoubleArray): DoubleArray {
        var m = z[0]; for (v in z) if (v > m) m = v
        val e = DoubleArray(z.size) { exp(z[it] - m) }
        val s = e.sum()
        for (i in e.indices) e[i] /= s
        return e
    }

    fun jsd(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) {
            val x = max(a[i], 1e-12); val y = max(b[i], 1e-12); val m = 0.5 * (x + y)
            s += 0.5 * x * ln(x / m) + 0.5 * y * ln(y / m)
        }
        return s
    }

    fun pow(base: Int, e: Int): Int { var r = 1; repeat(e) { r *= base }; return r }

    fun clamp(x: Double, lo: Double, hi: Double) = if (x < lo) lo else if (x > hi) hi else x

    fun mean(xs: DoubleArray): Double = if (xs.isEmpty()) 0.0 else xs.sum() / xs.size

    fun sd(xs: DoubleArray): Double {
        if (xs.size < 2) return 0.0
        val m = mean(xs)
        return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
    }
}
