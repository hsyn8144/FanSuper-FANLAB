package fan.lightningroulette.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Stats {
    fun mean(x: DoubleArray): Double { if (x.isEmpty()) return 0.0; var s = 0.0; for (v in x) s += v; return s / x.size }
    fun sd(x: DoubleArray): Double { if (x.size < 2) return 0.0; val m = mean(x); var s = 0.0; for (v in x) s += (v - m) * (v - m); return sqrt(s / (x.size - 1)) }

    /** [ortalama, alt, üst] — yüzde 95 percentile bootstrap. */
    fun bootstrapCI(x: DoubleArray, b: Int = 2000, seed: Long = 11L): DoubleArray {
        val n = x.size
        if (n == 0) return doubleArrayOf(0.0, 0.0, 0.0)
        val bb = min(b, max(200, 40_000_000 / n))
        val rnd = java.util.Random(seed)
        val means = DoubleArray(bb)
        for (i in 0 until bb) { var s = 0.0; for (j in 0 until n) s += x[rnd.nextInt(n)]; means[i] = s / n }
        means.sort()
        return doubleArrayOf(mean(x), means[(0.025 * (bb - 1)).toInt()], means[(0.975 * (bb - 1)).toInt()])
    }

    fun brier(p: DoubleArray, actual: Int): Double { var s = 0.0; for (i in p.indices) { val d = p[i] - (if (i == actual) 1.0 else 0.0); s += d * d }; return s }
    fun logLoss(p: DoubleArray, actual: Int): Double = -ln(max(p[actual], 1e-12))
    fun entropyBits(p: DoubleArray): Double { var h = 0.0; for (v in p) if (v > 0) h -= v * ln(v) / ln(2.0); return h }
    fun jsDiv(p: DoubleArray, q: DoubleArray): Double {
        var s = 0.0
        for (i in p.indices) {
            val m = 0.5 * (p[i] + q[i])
            if (p[i] > 0) s += 0.5 * p[i] * ln(p[i] / m)
            if (q[i] > 0) s += 0.5 * q[i] * ln(q[i] / m)
        }
        return s
    }

    /** İki ikili dizi arasındaki phi (Pearson) korelasyonu. */
    fun phi(a: BooleanArray, b: BooleanArray): Double {
        val n = min(a.size, b.size); if (n == 0) return 0.0
        var n11 = 0.0; var n10 = 0.0; var n01 = 0.0; var n00 = 0.0
        for (i in 0 until n) { if (a[i] && b[i]) n11++ else if (a[i]) n10++ else if (b[i]) n01++ else n00++ }
        val den = sqrt((n11 + n10) * (n01 + n00) * (n11 + n01) * (n10 + n00))
        return if (den == 0.0) 0.0 else (n11 * n00 - n10 * n01) / den
    }

    class Bins(val n: IntArray, val sumP: DoubleArray, val hits: IntArray) {
        fun meanP(i: Int) = if (n[i] == 0) 0.0 else sumP[i] / n[i]
        fun rate(i: Int) = if (n[i] == 0) 0.0 else hits[i].toDouble() / n[i]
    }
    /** 0–10 … 90–100 bucket'ları. */
    fun calibration(p: DoubleArray, y: BooleanArray, bins: Int = 10): Bins {
        val n = IntArray(bins); val sp = DoubleArray(bins); val h = IntArray(bins)
        for (i in p.indices) {
            val b = min(bins - 1, (p[i] * bins).toInt().coerceAtLeast(0))
            n[b]++; sp[b] += p[i]; if (y[i]) h[b]++
        }
        return Bins(n, sp, h)
    }
    fun ece(b: Bins): Double {
        var tot = 0; for (x in b.n) tot += x
        if (tot == 0) return 0.0
        var e = 0.0
        for (i in b.n.indices) if (b.n[i] > 0) e += b.n[i].toDouble() / tot * abs(b.meanP(i) - b.rate(i))
        return e
    }

    /** Ki-kare üst kuyruk olasılığı (Wilson–Hilferty yaklaşımı). */
    fun chiSqP(stat: Double, df: Int): Double {
        if (df <= 0) return 1.0
        val k = df.toDouble()
        val z = (Math.cbrt(stat / k) - (1 - 2.0 / (9 * k))) / sqrt(2.0 / (9 * k))
        return 1.0 - normCdf(z)
    }
    fun normCdf(z: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(z))
        val d = 0.3989423 * exp(-z * z / 2)
        val p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))))
        return if (z > 0) 1 - p else p
    }

    /** y = a + b·x en küçük kareler eğimi ve standart hatası. */
    fun trend(y: DoubleArray): DoubleArray {
        val n = y.size; if (n < 3) return doubleArrayOf(0.0, 1.0)
        val xm = (n - 1) / 2.0; val ym = mean(y)
        var sxx = 0.0; var sxy = 0.0
        for (i in 0 until n) { sxx += (i - xm) * (i - xm); sxy += (i - xm) * (y[i] - ym) }
        val b = sxy / sxx
        var sse = 0.0
        for (i in 0 until n) { val r = y[i] - (ym + b * (i - xm)); sse += r * r }
        val se = sqrt(sse / (n - 2) / sxx)
        return doubleArrayOf(b, se)
    }

    /** FNV-1a 64 */
    fun fnv(h: Long, v: Long): Long { var x = h; var t = v; for (i in 0 until 8) { x = (x xor (t and 0xff)) * 0x100000001b3L; t = t ushr 8 }; return x }
    const val FNV0 = -3750763034362895579L
}
