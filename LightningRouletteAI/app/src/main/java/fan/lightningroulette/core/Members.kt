package fan.lightningroulette.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Üyelerin ortak parametreleri. window: kaç spin geriye bakılır; decay: üstel unutma (0 = yok). */
class Params(val window: Int = 300, val decay: Double = 0.0, val sectors: Sectors = Sectors.DEFAULT) {
    fun wt(age: Int): Double = if (decay > 0) exp(-decay * age) else 1.0
}

/** Her üye, KENDİSİNE VERİLEN geçmişten (en eski → en yeni; fiziksel olarak kesilmiş) 37 sayı için olasılık üretir. */
interface Member {
    val id: String
    val title: String
    fun predict(h: IntArray): DoubleArray
}

fun norm(p: DoubleArray): DoubleArray {
    var s = 0.0
    for (x in p) s += x
    if (s <= 0.0 || s.isNaN()) { val u = 1.0 / p.size; for (i in p.indices) p[i] = u; return p }
    for (i in p.indices) p[i] /= s
    return p
}
fun uniform37(): DoubleArray = DoubleArray(Wheel.N) { 1.0 / Wheel.N }

class WheelMember(private val pr: Params, private val sigma: Double = 1.6) : Member {
    override val id = "wheel"; override val title = "Wheel Engine"
    private val kern = DoubleArray(19) { d -> exp(-(d * d) / (2 * sigma * sigma)) }
    override fun predict(h: IntArray): DoubleArray {
        val n = h.size; if (n == 0) return uniform37()
        val w = min(pr.window, n)
        val dens = DoubleArray(Wheel.N)
        for (t in n - w until n) {
            val wt = pr.wt(n - 1 - t); val pos = Wheel.POS[h[t]]
            for (j in 0 until Wheel.N) dens[j] += wt * kern[Wheel.circPos(pos, j)]
        }
        var s = 0.0; for (x in dens) s += x
        val out = DoubleArray(Wheel.N)
        for (j in 0 until Wheel.N) out[Wheel.ORDER[j]] = 0.85 * dens[j] / s + 0.15 / Wheel.N
        return out
    }
}

class SectorMember(private val pr: Params) : Member {
    override val id = "sector"; override val title = "Sector Engine"
    override fun predict(h: IntArray): DoubleArray {
        val n = h.size; if (n < 2) return uniform37()
        val sec = pr.sectors; val s = sec.count; val w = min(pr.window, n)
        val freq = DoubleArray(s) { 0.5 }; val trans = Array(s) { DoubleArray(s) { 0.3 } }
        for (t in n - w until n) {
            val wt = pr.wt(n - 1 - t); val a = sec.of[h[t]]
            freq[a] += wt
            if (t > n - w) trans[sec.of[h[t - 1]]][a] += wt
        }
        val pf = norm(freq); val pt = norm(trans[sec.of[h[n - 1]]].copyOf())
        val out = DoubleArray(Wheel.N)
        for (x in 0 until Wheel.N) { val a = sec.of[x]; out[x] = (0.5 * pf[a] + 0.5 * pt[a]) / sec.size(a) }
        return norm(out)
    }
}

class RegionMember(private val pr: Params) : Member {
    override val id = "region"; override val title = "Region Engine"
    override fun predict(h: IntArray): DoubleArray {
        val n = h.size; if (n < 2) return uniform37()
        val w = min(pr.window, n)
        val freq = DoubleArray(3) { 0.5 }; val trans = Array(3) { DoubleArray(3) { 0.3 } }
        for (t in n - w until n) {
            val wt = pr.wt(n - 1 - t); val a = Regions.of[h[t]]
            freq[a] += wt
            if (t > n - w) trans[Regions.of[h[t - 1]]][a] += wt
        }
        val pf = norm(freq); val pt = norm(trans[Regions.of[h[n - 1]]].copyOf())
        val out = DoubleArray(Wheel.N)
        for (x in 0 until Wheel.N) { val a = Regions.of[x]; out[x] = (0.5 * pf[a] + 0.5 * pt[a]) / Regions.size(a) }
        return norm(out)
    }
}

class NeighborMember(private val pr: Params) : Member {
    override val id = "neighbor"; override val title = "Neighbor Engine"
    override fun predict(h: IntArray): DoubleArray {
        val n = h.size; if (n < 3) return uniform37()
        val w = min(pr.window, n)
        val hist = DoubleArray(37) { 0.4 }
        for (t in n - w + 1 until n) hist[Wheel.signedOffset(h[t - 1], h[t]) + 18] += pr.wt(n - 1 - t)
        val last = h[n - 1]
        val out = DoubleArray(Wheel.N) { hist[Wheel.signedOffset(last, it) + 18] }
        return norm(out)
    }
}

class FrequencyMember(private val pr: Params) : Member {
    override val id = "frequency"; override val title = "Frequency Engine"
    override fun predict(h: IntArray): DoubleArray {
        val n = h.size; if (n == 0) return uniform37()
        val gm = min(n, 3000)
        val cg = DoubleArray(Wheel.N); for (t in n - gm until n) cg[h[t]] += 1.0
        val rm = min(n, 50)
        val cr = DoubleArray(Wheel.N); for (t in n - rm until n) cr[h[t]] += 1.0
        val lam = if (pr.decay > 0) pr.decay else 0.02
        val dm = min(n, max(pr.window, 100))
        val cd = DoubleArray(Wheel.N); for (t in n - dm until n) cd[h[t]] += exp(-lam * (n - 1 - t))
        val out = DoubleArray(Wheel.N)
        var sd = 0.0; for (x in cd) sd += x
        for (j in 0 until Wheel.N) out[j] = 0.4 * (cg[j] + 1) / (gm + Wheel.N) + 0.3 * (cr[j] + 1) / (rm + Wheel.N) + 0.3 * (cd[j] + 0.5) / (sd + 0.5 * Wheel.N)
        return norm(out)
    }
}

class PatternMember(private val pr: Params) : Member {
    override val id = "pattern"; override val title = "Pattern Engine (Kalıp 2.0)"
    override fun predict(h: IntArray): DoubleArray {
        val n = h.size; if (n < 12) return uniform37()
        val lim = min(n, max(pr.window * 4, 600)); val start = n - lim
        val sec = pr.sectors
        val tok = IntArray(n); for (t in start until n) tok[t] = sec.of[h[t]] * 3 + Wheel.color(h[t])
        val votes = DoubleArray(Wheel.N); var evidence = 0.0
        for (len in 4 downTo 2) {
            for (t in n - 2 downTo start + len - 1) {
                var ok = true
                for (q in 0 until len) if (tok[t - q] != tok[n - 1 - q]) { ok = false; break }
                if (ok) { votes[h[t + 1]] += len.toDouble(); evidence += len }
            }
        }
        for (t in n - 2 downTo start + 1) if (h[t] == h[n - 1] && h[t - 1] == h[n - 2]) { votes[h[t + 1]] += 3.0; evidence += 3.0 }
        val lam = evidence / (evidence + 12.0)
        var sv = 0.0; for (x in votes) sv += x
        val out = DoubleArray(Wheel.N) { (1 - lam) / Wheel.N + lam * (votes[it] + 0.2) / (sv + 0.2 * Wheel.N) }
        return norm(out)
    }
}

class TransitionMember(private val pr: Params) : Member {
    override val id = "transition"; override val title = "Transition Engine"
    private val orderW = doubleArrayOf(0.5, 0.3, 0.2)
    override fun predict(h: IntArray): DoubleArray {
        val n = h.size; if (n < 8) return uniform37()
        val sec = pr.sectors; val s = sec.count; val w = min(pr.window, n); val from = n - w
        val psec = DoubleArray(s); var wsum = 0.0
        for (o in 1..3) {
            if (n < o + 1) continue
            val maps = HashMap<Int, DoubleArray>()
            for (t in max(from + o, o) until n) {
                var key = 0; for (q in 1..o) key = key * 64 + sec.of[h[t - q]]
                val row = maps.getOrPut(key) { DoubleArray(s) }
                row[sec.of[h[t]]] += 1.0
            }
            var key = 0; for (q in 1..o) key = key * 64 + sec.of[h[n - q]]
            val row = maps[key] ?: continue
            var tot = 0.0; for (x in row) tot += x
            if (tot < 3) continue
            val g = orderW[o - 1] * tot / (tot + 5.0)
            for (a in 0 until s) psec[a] += g * (row[a] + 0.3) / (tot + 0.3 * s)
            wsum += g
        }
        val pn = DoubleArray(Wheel.N) { 0.3 }
        val last = h[n - 1]
        val cnt = DoubleArray(Wheel.N) { 0.3 }
        for (t in max(from, 1) until n) if (h[t - 1] == last) cnt[h[t]] += 1.0
        for (j in 0 until Wheel.N) pn[j] = cnt[j]
        norm(pn)
        val out = DoubleArray(Wheel.N)
        for (x in 0 until Wheel.N) {
            val a = sec.of[x]
            val ps = if (wsum > 0) psec[a] / wsum else sec.baseline(a)
            out[x] = 0.6 * ps / sec.size(a) + 0.4 * pn[x]
        }
        return norm(out)
    }
}

/** Durum taşıyan üyeler (yalnızca Kotlin ML): LAB checkpoint/resume ve uygulama yeniden başlatma için. */
interface StatefulMember { fun stateMap(): Map<String, Any?>; fun restoreState(m: Map<String, Any?>) }

/** Çevrimiçi softmax-lojistik regresyon (özellik grupları: sektör gecikmeleri, renk, tek/çift, yüksek/düşük, dozen, column, son-50 sektör frekansı). */
class KotlinMlMember(private val pr: Params, private val seed: Long = 7L) : Member, StatefulMember {
    override val id = "kml"; override val title = "Kotlin ML (softmax)"
    private val sc = pr.sectors.count
    private val nf = 4 * sc + 18
    private val wts = Array(Wheel.N) { DoubleArray(nf) }
    private var fitAt = -1000

    private fun feat(h: IntArray, t: Int, out: DoubleArray) {
        java.util.Arrays.fill(out, 0.0)
        var o = 0
        val sec = pr.sectors
        for (lag in 1..3) { if (t - lag >= 0) out[o + sec.of[h[t - lag]]] = 1.0; o += sc }
        val v = if (t >= 1) h[t - 1] else -1
        if (v >= 0) out[o + Wheel.color(v)] = 1.0; o += 3
        if (v >= 0) out[o + (if (v == 0) 0 else if (v % 2 == 0) 1 else 2)] = 1.0; o += 3
        if (v >= 0) out[o + (if (v == 0) 0 else if (v <= 18) 1 else 2)] = 1.0; o += 3
        if (v >= 0) out[o + (if (v == 0) 0 else (v - 1) / 12 + 1)] = 1.0; o += 4
        if (v >= 0) out[o + (if (v == 0) 0 else (v - 1) % 3 + 1)] = 1.0; o += 4
        val w = min(50, t)
        if (w > 0) for (u in t - w until t) out[o + sec.of[h[u]]] += 1.0 / w
        o += sc
        out[o] = 1.0
    }

    private fun softmax(f: DoubleArray, out: DoubleArray) {
        var mx = -1e300
        for (c in 0 until Wheel.N) { var z = 0.0; val wr = wts[c]; for (q in 0 until nf) z += wr[q] * f[q]; out[c] = z; if (z > mx) mx = z }
        var s = 0.0
        for (c in 0 until Wheel.N) { out[c] = exp(out[c] - mx); s += out[c] }
        for (c in 0 until Wheel.N) out[c] /= s
    }

    private fun fit(h: IntArray) {
        val n = h.size; val m = min(400, n - 3); if (m < 30) return
        val f = DoubleArray(nf); val pr0 = DoubleArray(Wheel.N)
        val rnd = java.util.Random(seed + n)
        val idx = IntArray(m) { n - m + it }
        repeat(2) {
            for (i in m - 1 downTo 1) { val j = rnd.nextInt(i + 1); val tmp = idx[i]; idx[i] = idx[j]; idx[j] = tmp }
            for (t in idx) {
                feat(h, t, f); softmax(f, pr0)
                for (c in 0 until Wheel.N) {
                    val g = pr0[c] - (if (c == h[t]) 1.0 else 0.0); val wr = wts[c]
                    for (q in 0 until nf) wr[q] -= 0.05 * (g * f[q] + 1e-3 * wr[q])
                }
            }
        }
    }

    override fun stateMap(): Map<String, Any?> = mapOf("fitAt" to fitAt, "w" to wts.map { it.toList() })
    override fun restoreState(m: Map<String, Any?>) {
        fitAt = m["fitAt"].jint(-1000)
        val rows = m["w"].jlist()
        for (i in 0 until minOf(Wheel.N, rows.size)) { val r = rows[i].jdoubles(); if (r.size == nf) wts[i] = r }
    }

    override fun predict(h: IntArray): DoubleArray {
        val n = h.size; if (n < 40) return uniform37()
        if (n - fitAt >= 25 || fitAt > n) { fit(h); fitAt = n }
        val f = DoubleArray(nf); feat(h, n, f)
        val p = DoubleArray(Wheel.N); softmax(f, p)
        for (i in p.indices) p[i] = 0.9 * p[i] + 0.1 / Wheel.N
        return norm(p)
    }
}

/** Fixed-Share Hedge: log-skor kazancıyla (tekdüze dağılıma göre) ağırlık günceller; alt sınır alpha/m. */
class Hedge(val m: Int, private val eta: Double = 0.25, private val alpha: Double = 0.02) {
    var w = DoubleArray(m) { 1.0 / m }

    fun mix(ps: List<DoubleArray>): DoubleArray {
        val len = ps[0].size
        val out = DoubleArray(len)
        for (i in 0 until m) { val p = ps[i]; val wi = w[i]; for (x in 0 until len) out[x] += wi * p[x] }
        return norm(out)
    }

    fun update(ps: List<DoubleArray>, actual: Int) {
        val len = ps[0].size
        val g = DoubleArray(m) { ln(max(ps[it][actual], 1e-9) * len) }
        var mx = g[0]; for (x in g) if (x > mx) mx = x
        var s = 0.0
        for (i in 0 until m) { w[i] *= exp(eta * (g[i] - mx)); s += w[i] }
        for (i in 0 until m) w[i] = (1 - alpha) * (w[i] / s) + alpha / m
    }
}

/** Table motoru: sayı tahmininden bağımsız, kendi özellikleriyle (frekans-decay, son-50, Markov-1) öğrenir. */
class TableEngine(private val pr: Params) {
    val hedges = Array(5) { Hedge(3, 0.3, 0.02) }
    var shrink = 0.6
    private var cacheLen = -1
    private var cache: Array<List<DoubleArray>>? = null

    private fun labels(cat: Int, h: IntArray, k: Int): IntArray = IntArray(h.size) { val c = TableCats.classOf(cat, h[it]); if (c < 0) k else c }

    private fun memberProbs(cat: Int, h: IntArray): List<DoubleArray> {
        val k = TableCats.classes(cat); val m = k + 1
        val lab = labels(cat, h, k); val n = lab.size
        val base = DoubleArray(m) { if (it == k) 1.0 / Wheel.N else (if (k == 2) 18.0 else 12.0) / Wheel.N }
        val lam = if (pr.decay > 0) pr.decay else 0.02
        val dm = min(n, max(pr.window, 100))
        val fd = DoubleArray(m) { base[it] * 4 }
        for (t in n - dm until n) fd[lab[t]] += exp(-lam * (n - 1 - t))
        val rm = min(n, 50)
        val fr = DoubleArray(m) { base[it] * 4 }
        for (t in n - rm until n) fr[lab[t]] += 1.0
        val tr = DoubleArray(m) { base[it] * 4 }
        if (n >= 2) {
            val last = lab[n - 1]
            for (t in max(1, n - pr.window) until n) if (lab[t - 1] == last) tr[lab[t]] += 1.0
        }
        return listOf(norm(fd), norm(fr), norm(tr))
    }

    private fun members(h: IntArray): Array<List<DoubleArray>> {
        if (cache != null && cacheLen == h.size) return cache!!
        val r = Array(5) { memberProbs(it, h) }
        cache = r; cacheLen = h.size
        return r
    }

    fun predict(h: IntArray): List<TableCall> {
        val ms = members(h)
        return List(5) { cat ->
            val k = TableCats.classes(cat)
            val mix = hedges[cat].mix(ms[cat])
            val base = DoubleArray(k + 1) { if (it == k) 1.0 / Wheel.N else (if (k == 2) 18.0 else 12.0) / Wheel.N }
            val cal = DoubleArray(k + 1) { base[it] + shrink * (mix[it] - base[it]) }
            norm(cal)
            var best = 0; for (c in 1 until k) if (cal[c] > cal[best]) best = c
            TableCall(cat, best, cal[best], TableCats.baseline(cat), cal)
        }
    }

    /** h: gerçek sonuçtan ÖNCEKİ geçmiş. */
    fun learn(h: IntArray, actual: Int) {
        val ms = members(h)
        for (cat in 0 until 5) {
            val k = TableCats.classes(cat); val c = TableCats.classOf(cat, actual)
            hedges[cat].update(ms[cat], if (c < 0) k else c)
        }
    }

    fun state(): Map<String, Any?> = mapOf("w" to hedges.map { it.w.toList() }, "shrink" to shrink)
    fun restore(m: Map<String, Any?>) {
        val ws = m["w"].jlist()
        for (i in 0 until min(5, ws.size)) { val a = ws[i].jdoubles(); if (a.size == 3) hedges[i].w = a }
        shrink = m["shrink"].jnum(0.6)
    }
}
