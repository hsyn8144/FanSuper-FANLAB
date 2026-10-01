package fan.superai.v13

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Olasılık havuzlama: doğrusal karışım + sıcaklık. Karşı-olgusal analiz de aynı kodu kullanır. */
object Pool {
    fun mix(k: Int, dists: Array<DoubleArray>, w: DoubleArray): DoubleArray {
        val out = DoubleArray(k)
        for (i in dists.indices) for (c in 0 until k) out[c] += w[i] * dists[i][c]
        var s = 0.0; for (c in 0 until k) s += out[c]
        if (s <= 0 || s.isNaN()) return Mx.uniform(k)
        for (c in 0 until k) out[c] /= s
        return out
    }

    fun shape(k: Int, mix: DoubleArray, tau: Double): DoubleArray {
        val z = DoubleArray(k) { ln(max(mix[it], Mx.FLOOR)) * tau }
        return Mx.normalize(Mx.softmax(z))
    }
}

data class ReliabilityBin(val lo: Double, val hi: Double, val n: Int, val meanConf: Double, val accuracy: Double)

/** Güven kalibrasyonu: üst-sınıf olasılığı kutularına göre gerçek isabet (persistent). */
class Calibrator13(val k: Int, val bins: Int = 10) {
    private val n = DoubleArray(bins)
    private val hit = DoubleArray(bins)
    private val sumConf = DoubleArray(bins)
    var total = 0; private set

    private fun bin(p: Double): Int {
        val lo = 1.0 / k
        return (((p - lo) / (1.0 - lo)) * bins).toInt().coerceIn(0, bins - 1)
    }

    fun add(conf: Double, ok: Boolean) {
        val i = bin(conf); n[i]++; sumConf[i] += conf; if (ok) hit[i]++; total++
    }

    /** Kalibre edilmiş güven: kutu isabeti, az gözlemde ham güvene yaslanır. */
    fun calibrate(conf: Double): Double { val i = bin(conf); val m = 25.0; return (hit[i] + conf * m) / (n[i] + m) }

    /** Beklenen kalibrasyon hatası (ağırlıklı |isabet − güven|). */
    fun gap(): Double {
        if (total == 0) return 0.0
        var g = 0.0
        for (i in 0 until bins) if (n[i] > 0) g += n[i] / total * abs(hit[i] / n[i] - sumConf[i] / n[i])
        return g
    }

    fun table(): List<ReliabilityBin> = (0 until bins).filter { n[it] > 0 }.map {
        val lo = 1.0 / k; val w = (1.0 - lo) / bins
        ReliabilityBin(lo + w * it, lo + w * (it + 1), n[it].toInt(), sumConf[it] / n[it], hit[it] / n[it])
    }

    fun write(o: Out) { o.da(n); o.da(hit); o.da(sumConf); o.i(total) }
    fun read(i: In) { i.fill(n); i.fill(hit); i.fill(sumConf); total = i.i() }
}

/** Sabit boyutlu kayan isabet penceresi. */
class RollBits(private val cap: Int = 100) {
    private val buf = BooleanArray(cap)
    var count = 0; private set
    private var pos = 0
    private var hits = 0
    fun add(b: Boolean) {
        if (count == cap) { if (buf[pos]) hits-- } else count++
        buf[pos] = b; if (b) hits++
        pos = (pos + 1) % cap
    }
    fun rate(): Double = if (count == 0) 0.0 else hits.toDouble() / count
    fun write(o: Out) { o.ba(buf); o.i(count); o.i(pos); o.i(hits) }
    fun read(i: In) { val b = i.ba(); if (b.size != cap) throw FanException(ErrorCodes.STATE_LOAD, "roll"); b.copyInto(buf); count = i.i(); pos = i.i(); hits = i.i() }
}

/**
 * Konformal tek/çift kararı için sabit boyutlu skor halkası (v1.4).
 * Skor = 1 − p[gerçek]; halka dolduğunda en eski skor düşer, böylece karar son 300
 * değerlendirmeyi yansıtır (v1.1 hakemindeki `scoreCap = 300` ile aynı pencere).
 */
class ScoreRing(private val cap: Int = 300) {
    private val buf = DoubleArray(cap)
    var count = 0; private set
    private var pos = 0

    fun add(v: Double) {
        buf[pos] = if (v.isNaN()) 1.0 else v
        pos = (pos + 1) % cap
        if (count < cap) count++
    }

    /** Skorlar (sıra önemsiz; yalnızca dağılım kullanılır). */
    fun values(): DoubleArray = if (count == 0) DoubleArray(0) else DoubleArray(count) { buf[(pos - count + it + cap) % cap] }

    fun clear() { buf.fill(0.0); count = 0; pos = 0 }

    fun write(o: Out) { o.da(buf); o.i(count); o.i(pos) }
    fun read(i: In) { i.fill(buf); count = i.i(); pos = i.i() }
}

/** Bir modelin (bir eksendeki) kalıcı performans durumu. */
class ModelTracker(val k: Int) {
    companion object {
        const val ALPHA_LONG = 0.01
        const val ALPHA_SHORT = 0.06
        const val ALPHA_REG = 0.05
    }
    var n = 0; private set
    var top1 = 0; private set
    var top2 = 0; private set
    var sumLL = 0.0; private set
    var sumBrier = 0.0; private set
    var sumEntropy = 0.0; private set
    var ewmaLong = 0.0; private set          // log-skor kazancı (uniform baseline'a göre)
    var ewmaShort = 0.0; private set
    val roll = RollBits(100)
    val cal = Calibrator13(k)
    val regN = IntArray(V13.REGIMES)
    val regGain = DoubleArray(V13.REGIMES)
    val mu = DoubleArray(k)                  // çıktı ortalaması (çeşitlilik için)
    val vr = DoubleArray(k)

    val accuracy get() = if (n == 0) 0.0 else top1.toDouble() / n
    val top2Rate get() = if (n == 0) 0.0 else top2.toDouble() / n
    val logLoss get() = if (n == 0) 0.0 else sumLL / n
    val brier get() = if (n == 0) 0.0 else sumBrier / n
    val entropy get() = if (n == 0) 0.0 else sumEntropy / n
    val recent get() = roll.rate()

    fun regimeGain(r: Int) = regGain[r] * regN[r] / (regN[r] + 20.0)

    fun observe(p: DoubleArray, actual: Int, regime: Int) {
        val gain = ln(max(p[actual], Mx.FLOOR)) - ln(1.0 / k)
        val o = Mx.top2(p)
        val hit1 = o[0] == actual
        n++; if (hit1) top1++; if (hit1 || o[1] == actual) top2++
        sumLL += Mx.logLoss(p, actual); sumBrier += Mx.brier(p, actual); sumEntropy += Mx.entropy(p)
        ewmaLong += max(1.0 / n, ALPHA_LONG) * (gain - ewmaLong)
        ewmaShort += max(1.0 / n, ALPHA_SHORT) * (gain - ewmaShort)
        roll.add(hit1)
        cal.add(p[o[0]], hit1)
        regN[regime]++
        regGain[regime] += max(1.0 / regN[regime], ALPHA_REG) * (gain - regGain[regime])
    }

    fun write(o: Out) {
        o.i(n); o.i(top1); o.i(top2); o.d(sumLL); o.d(sumBrier); o.d(sumEntropy); o.d(ewmaLong); o.d(ewmaShort)
        roll.write(o); cal.write(o); o.ia(regN); o.da(regGain); o.da(mu); o.da(vr)
    }

    fun read(i: In) {
        n = i.i(); top1 = i.i(); top2 = i.i(); sumLL = i.d(); sumBrier = i.d(); sumEntropy = i.d()
        ewmaLong = i.d(); ewmaShort = i.d()
        roll.read(i); cal.read(i); i.ia().copyInto(regN); i.fill(regGain); i.fill(mu); i.fill(vr)
    }
}

class ModelRow(
    val id: String, val name: String, val group: Group, val weight: Double,
    val n: Int, val top1: Double, val top2: Double, val logLoss: Double, val brier: Double,
    val entropy: Double, val calGap: Double, val recent: Double, val gain: Double
)

/**
 * Eksen başına Meta Ensemble. Taban modellerin (Kotlin + Python) olasılıklarını:
 *   rolling/uzun dönem log-skor kazancı + kalibrasyon + rejim performansı + örnek sayısı + korelasyon cezası
 * ile ağırlıklandırır. Ağırlıklar YALNIZCA gerçek sonuç öğrenildikten sonra, üstel yumuşatmayla
 * (stabil) güncellenir; tahmin anında hiçbir ağırlık hesabı yeni bilgi kullanmaz.
 */
class AxisEnsemble(val axis: Axis, val k: Int) {
    companion object {
        const val SMOOTH = 0.10          // ağırlık geçişi: tek sonuçla aşırı oynamaz
        const val MIN_SHARE = 0.15       // ağırlık alt sınırı (1/n çarpanı)
        const val PAIR_ALPHA = 0.02
    }

    val trackers = LinkedHashMap<String, ModelTracker>()
    private val names = HashMap<String, String>()
    private val groups = HashMap<String, Group>()
    private val weights = HashMap<String, Double>()
    /** Çift istatistikleri: [kovaryans, argmax uyumu, JSD, gözlem sayısı] */
    private val pairs = HashMap<String, DoubleArray>()
    var tau = 1.0; private set
    val finalTracker = ModelTracker(k)
    var disagreeEwma = 0.0; private set      // modellerin nihai tahminle ayrışma oranı
    var diversityEwma = 0.0; private set      // ortalama ikili JSD
    private val eta = 12.0 / ln(k.toDouble())

    private fun key(a: String, b: String) = if (a < b) "$a|$b" else "$b|$a"

    fun register(id: String, name: String, group: Group) {
        if (id !in trackers) trackers[id] = ModelTracker(k)
        names[id] = name; groups[id] = group
    }

    /** Verilen modeller için normalize edilmiş ağırlıklar (bilinmeyen model → eşit pay). */
    fun weightsFor(ids: List<String>): DoubleArray {
        if (ids.isEmpty()) return DoubleArray(0)
        val raw = DoubleArray(ids.size) { weights[ids[it]] ?: (1.0 / ids.size) }
        val s = raw.sum()
        return if (s <= 0) DoubleArray(ids.size) { 1.0 / ids.size } else DoubleArray(ids.size) { raw[it] / s }
    }

    fun mix(dists: Array<DoubleArray>, w: DoubleArray): DoubleArray = Pool.mix(k, dists, w)

    /** Sıcaklık ölçekleme (argmax'ı değiştirmez; güven/entropi kalibrasyonu). */
    fun shape(mix: DoubleArray, tau: Double = this.tau): DoubleArray = Pool.shape(k, mix, tau)

    /** Gerçek sonuç öğrenildi: bütün istatistikleri ve (ayrıca) çeşitlilik durumunu güncelle. */
    fun observe(inp: AxisInput, actual: Int, regime: Int) {
        val n = inp.size
        for (i in 0 until n) register(inp.ids[i], inp.names[i], inp.groups[i])
        // Çeşitlilik: eski ortalamalara göre sapmalar
        val dev = Array(n) { i -> val t = trackers[inp.ids[i]]!!; DoubleArray(k) { c -> inp.dists[i][c] - t.mu[c] } }
        val arg = IntArray(n) { Mx.argmax(inp.dists[it]) }
        val m = mix(inp.dists, inp.weights)
        val fin = shape(m, inp.tau)
        val fa = Mx.argmax(fin)
        var dis = 0; for (i in 0 until n) if (arg[i] != fa) dis++
        if (n > 0) disagreeEwma += 0.05 * (dis.toDouble() / n - disagreeEwma)
        var jsdSum = 0.0; var jsdCnt = 0
        for (i in 0 until n) for (j in i + 1 until n) {
            val ps = pairs.getOrPut(key(inp.ids[i], inp.ids[j])) { DoubleArray(4) }
            var cov = 0.0; for (c in 0 until k) cov += dev[i][c] * dev[j][c]
            val a = max(1.0 / (ps[3] + 1), PAIR_ALPHA)
            ps[0] += a * (cov - ps[0])
            ps[1] += a * ((if (arg[i] == arg[j]) 1.0 else 0.0) - ps[1])
            val js = Mx.jsd(inp.dists[i], inp.dists[j])
            ps[2] += a * (js - ps[2]); ps[3] += 1.0
            jsdSum += js; jsdCnt++
        }
        if (jsdCnt > 0) diversityEwma += 0.05 * (jsdSum / jsdCnt - diversityEwma)
        for (i in 0 until n) {
            val t = trackers[inp.ids[i]]!!
            val first = t.n == 0
            // Ortalama/varyans önce güncellenir (n'den bağımsız EWMA), sonra performans
            val a = max(1.0 / (t.n + 1), 0.02)
            for (c in 0 until k) {
                val d = if (first) 0.0 else dev[i][c]
                t.mu[c] += a * (inp.dists[i][c] - t.mu[c])
                t.vr[c] += a * (d * d - t.vr[c])
            }
            t.observe(inp.dists[i], actual, regime)
        }
        finalTracker.observe(fin, actual, regime)
        // Sıcaklık (tau): log-kayıp gradyanı, küçük adım, sınırlı aralık
        val z = DoubleArray(k) { ln(max(m[it], Mx.FLOOR)) }
        val p = Mx.softmax(DoubleArray(k) { z[it] * tau })
        var ez = 0.0; for (c in 0 until k) ez += p[c] * z[c]
        val g = -(z[actual] - ez)
        tau = Mx.clamp(tau - 0.02 * g, 0.5, 1.5)
    }

    private fun correlation(a: String, b: String): Double {
        val ps = pairs[key(a, b)] ?: return 0.0
        if (ps[3] < 20) return 0.0
        val ta = trackers[a] ?: return 0.0; val tb = trackers[b] ?: return 0.0
        val va = ta.vr.sum(); val vb = tb.vr.sum()
        if (va < 1e-12 || vb < 1e-12) return 0.0
        return Mx.clamp(ps[0] / sqrt(va * vb), -1.0, 1.0)
    }

    fun corr(a: String, b: String) = correlation(a, b)
    fun agreement(a: String, b: String): Double = pairs[key(a, b)]?.get(1) ?: 0.0

    /** Bir model için ortalama pozitif korelasyon (çeşitlilik cezası girdisi). */
    fun meanCorr(id: String): Double {
        var s = 0.0; var c = 0
        for (o in trackers.keys) if (o != id) { s += max(0.0, correlation(id, o)); c++ }
        return if (c == 0) 0.0 else s / c
    }

    /** Gerçek sonuç + rejim güncellemesinden SONRA: bir sonraki tahminde kullanılacak ağırlıkları yumuşat. */
    fun updateWeights(nextRegime: Int) {
        if (trackers.isEmpty()) return
        val raw = HashMap<String, Double>()
        var sum = 0.0
        for ((id, t) in trackers) {
            val perf = 0.5 * t.ewmaLong + 0.5 * t.ewmaShort + 0.5 * t.regimeGain(nextRegime)
            val calPen = if (t.cal.total >= 50) 4.0 * t.cal.gap() else 0.0
            val s = Mx.clamp(eta * perf - calPen, -1.5, 1.5)
            val sample = t.n / (t.n + 30.0)
            val div = 1.0 / (1.0 + meanCorr(id))
            val r = exp(s) * (0.3 + 0.7 * sample) * div
            raw[id] = r; sum += r
        }
        val nn = trackers.size
        for ((id, r) in raw) {
            val target = r / sum
            val prev = weights[id] ?: target
            weights[id] = max((1 - SMOOTH) * prev + SMOOTH * target, MIN_SHARE / nn)
        }
        val s2 = weights.values.sum()
        for (id in weights.keys.toList()) weights[id] = weights[id]!! / s2
    }

    fun rows(): List<ModelRow> = trackers.map { (id, t) ->
        ModelRow(id, names[id] ?: id, groups[id] ?: Group.KOTLIN, weights[id] ?: 0.0, t.n, t.accuracy, t.top2Rate,
            t.logLoss, t.brier, t.entropy, t.cal.gap(), t.recent, 0.5 * t.ewmaLong + 0.5 * t.ewmaShort)
    }.sortedByDescending { it.weight }

    fun name(id: String) = names[id] ?: id
    fun group(id: String) = groups[id] ?: Group.KOTLIN
    fun weight(id: String) = weights[id] ?: 0.0

    // ---- serileştirme ----
    fun writeEnsemble(o: Out) {
        o.d(tau); o.d(disagreeEwma); o.d(diversityEwma)
        o.i(trackers.size)
        for (id in trackers.keys) { o.s(id); o.s(names[id] ?: id); o.s((groups[id] ?: Group.KOTLIN).name); o.d(weights[id] ?: 0.0) }
        o.i(pairs.size)
        for ((kk, v) in pairs) { o.s(kk); o.da(v) }
    }

    fun writeTracker(id: String, o: Out) { trackers[id]!!.write(o) }
    fun writeFinal(o: Out) { finalTracker.write(o) }

    fun readEnsemble(i: In) {
        tau = i.d(); disagreeEwma = i.d(); diversityEwma = i.d()
        trackers.clear(); weights.clear(); pairs.clear()
        repeat(i.i()) {
            val id = i.s(); val nm = i.s(); val g = Group.valueOf(i.s()); val w = i.d()
            trackers[id] = ModelTracker(k); names[id] = nm; groups[id] = g; if (w > 0) weights[id] = w
        }
        repeat(i.i()) { pairs[i.s()] = i.da() }
    }

    fun reset() { trackers.clear(); weights.clear(); pairs.clear(); names.clear(); groups.clear(); tau = 1.0; disagreeEwma = 0.0; diversityEwma = 0.0 }

    fun readTracker(id: String, i: In) { trackers[id]?.read(i) }
    fun readFinal(i: In) { finalTracker.read(i) }
}
