package fan.superai.v13

import kotlin.math.abs

/**
 * Yan/rakam dizi hafızası. YALNIZCA gerçek sonuç öğrenildikten sonra [append] ile büyür; bu yüzden
 * tahmin anında gelecek bilgi hafızada fiziksel olarak bulunmaz (leakage guard'ın temel taşı).
 *
 * Tüm özellikler artımlı tutulur: sayaçlar, geçiş matrisleri, seri uzunlukları ve son [CAP] sembol.
 */
class SequenceMemory(val alphabet: Alphabet) {
    companion object { const val CAP = 64 }

    var n = 0; private set
    var lastRecordId = 0L; private set
    var lastTimestamp = 0L; private set

    private val ring = Array(4) { IntArray(CAP) }
    private val counts = Array(4) { DoubleArray(alphabet.k(Axis.values()[it])) }
    private val trans = Array(4) { DoubleArray(alphabet.k(Axis.values()[it]) * alphabet.k(Axis.values()[it])) }
    private val runLen = IntArray(4)

    fun k(axis: Axis) = alphabet.k(axis)

    /** Son sembolden [back] önceki sembol (back=1 → en son). Yetmiyorsa -1. */
    fun sym(axis: Axis, back: Int = 1): Int =
        if (back < 1 || back > n || back > CAP) -1 else ring[axis.ordinal][(n - back) % CAP]

    fun counts(axis: Axis): DoubleArray = counts[axis.ordinal]
    fun transition(axis: Axis): DoubleArray = trans[axis.ordinal]
    fun run(axis: Axis): Int = runLen[axis.ordinal]

    /** Son [w] sembolün sınıf sayıları. */
    fun windowCounts(axis: Axis, w: Int): DoubleArray {
        val c = DoubleArray(k(axis))
        val m = minOf(w, n, CAP)
        for (b in 1..m) c[sym(axis, b)]++
        return c
    }

    /** Son [order] sembolün tabana göre indeksi; yeterli geçmiş yoksa -1. */
    fun ctx(axis: Axis, order: Int): Int {
        if (order <= 0) return 0
        if (n < order || order > CAP) return -1
        var idx = 0; val kk = k(axis)
        for (b in order downTo 1) idx = idx * kk + sym(axis, b)
        return idx
    }

    /** Geçiş olasılığı P(sonraki=j | son=i) (KT yumuşatmalı). */
    fun transitionProb(axis: Axis, from: Int): DoubleArray {
        val kk = k(axis); val t = trans[axis.ordinal]
        var tot = 0.0; for (j in 0 until kk) tot += t[from * kk + j]
        return DoubleArray(kk) { (t[from * kk + it] + 0.5) / (tot + 0.5 * kk) }
    }

    fun append(rec: FanRecord) {
        val syms = intArrayOf(
            alphabet.idx(rec.number), rec.bigSmall.index, rec.oddEven.index, rec.combinedSide.index
        )
        for (a in 0 until 4) {
            val kk = k(Axis.values()[a])
            val prev = if (n > 0) ring[a][(n - 1) % CAP] else -1
            if (prev >= 0) trans[a][prev * kk + syms[a]]++
            runLen[a] = if (prev == syms[a]) runLen[a] + 1 else 1
            counts[a][syms[a]]++
            ring[a][n % CAP] = syms[a]
        }
        n++
        lastRecordId = rec.recordId; lastTimestamp = rec.timestamp
    }

    fun write(o: Out) {
        o.i(n); o.l(lastRecordId); o.l(lastTimestamp)
        for (a in 0 until 4) { o.ia(ring[a]); o.da(counts[a]); o.da(trans[a]) }
        o.ia(runLen)
    }

    fun read(i: In) {
        n = i.i(); lastRecordId = i.l(); lastTimestamp = i.l()
        for (a in 0 until 4) {
            val r = i.ia(); if (r.size != CAP) throw FanException(ErrorCodes.STATE_LOAD, "ring")
            r.copyInto(ring[a]); i.fill(counts[a]); i.fill(trans[a])
        }
        val rl = i.ia(); rl.copyInto(runLen)
    }
}

/**
 * Regime motoru: verinin davranışındaki değişimi izler.
 * Özellikler (son 30 sembol): ortalama dönüş (flip) oranı ve Büyük/Küçük + Tek/Çift dengesizliği.
 * Üç dahili merkez (REGIME_A/B/C) çevrimiçi k-means ile yavaşça uyarlanır; geçiş için histerezis vardır.
 * Özellikler yalnızca öğrenilmiş geçmişten hesaplanır → leakage yok.
 */
class RegimeEngine {
    companion object {
        const val WINDOW = 30
        const val MIN_N = 20
        const val LR = 0.01
        const val SWITCH_AFTER = 4
        const val MARGIN = 0.002
    }

    val R = V13.REGIMES
    var current = 0; private set
    var duration = 0; private set            // mevcut rejimde geçen adım
    var changes = 0; private set
    private val centroids = doubleArrayOf(0.38, 0.12, 0.50, 0.10, 0.62, 0.12)
    private var pending = -1
    private var pendingCount = 0
    val transitions = DoubleArray(R * R)
    val steps = IntArray(R)                  // rejim başına adım sayısı
    val history = ArrayList<IntArray>()      // [rejim, başlangıç sırası, süre] (son 50)
    private var startedAt = 0

    // Rejim başına performans: [n, sayı isabeti, yan isabeti (ikisi birden), top2 isabeti]
    val perf = Array(R) { DoubleArray(4) }

    fun name(r: Int = current) = V13.REGIME_NAMES[r]

    fun features(mem: SequenceMemory): DoubleArray? {
        if (mem.n < MIN_N) return null
        val w = minOf(WINDOW, mem.n)
        fun flip(axis: Axis): Double {
            var f = 0
            for (b in 1 until w) if (mem.sym(axis, b) != mem.sym(axis, b + 1)) f++
            return f.toDouble() / (w - 1)
        }
        val bc = mem.windowCounts(Axis.BS, w); val oc = mem.windowCounts(Axis.OE, w)
        val imb = abs(bc[1] / w - 0.5) + abs(oc[1] / w - 0.5)
        return doubleArrayOf((flip(Axis.BS) + flip(Axis.OE)) / 2.0, imb)
    }

    private fun dist(x: DoubleArray, r: Int): Double {
        val a = x[0] - centroids[r * 2]; val b = x[1] - centroids[r * 2 + 1]
        return a * a + b * b
    }

    /** Hafıza GÜNCELLENDİKTEN sonra çağrılır; bir sonraki tahmin için rejimi belirler. Değiştiyse true. */
    fun update(mem: SequenceMemory): Boolean {
        val x = features(mem) ?: run { duration++; steps[current]++; return false }
        var best = 0; var bd = dist(x, 0)
        for (r in 1 until R) { val d = dist(x, r); if (d < bd) { bd = d; best = r } }
        var changed = false
        if (best == current) { pending = -1; pendingCount = 0 }
        else if (dist(x, current) - bd > MARGIN) {
            if (pending == best) pendingCount++ else { pending = best; pendingCount = 1 }
            if (pendingCount >= SWITCH_AFTER) {
                transitions[current * R + best]++
                history.add(intArrayOf(current, startedAt, duration))
                while (history.size > 50) history.removeAt(0)
                current = best; duration = 0; startedAt = mem.n; changes++
                pending = -1; pendingCount = 0; changed = true
            }
        } else { pending = -1; pendingCount = 0 }
        centroids[best * 2] += LR * (x[0] - centroids[best * 2])
        centroids[best * 2 + 1] += LR * (x[1] - centroids[best * 2 + 1])
        duration++; steps[current]++
        return changed
    }

    fun record(regime: Int, numberHit: Boolean, sideHit: Boolean, top2Hit: Boolean) {
        val p = perf[regime]
        p[0]++; if (numberHit) p[1]++; if (sideHit) p[2]++; if (top2Hit) p[3]++
    }

    fun write(o: Out) {
        o.i(current); o.i(duration); o.i(changes); o.da(centroids); o.i(pending); o.i(pendingCount)
        o.da(transitions); o.ia(steps); o.i(startedAt)
        o.i(history.size); history.forEach { o.ia(it) }
        for (r in 0 until R) o.da(perf[r])
    }

    fun read(i: In) {
        current = i.i(); duration = i.i(); changes = i.i(); i.fill(centroids); pending = i.i(); pendingCount = i.i()
        i.fill(transitions); i.ia().copyInto(steps); startedAt = i.i()
        history.clear(); repeat(i.i()) { history.add(i.ia()) }
        for (r in 0 until R) i.fill(perf[r])
    }
}
