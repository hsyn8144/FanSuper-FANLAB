package fan.superai.v13

import kotlin.math.pow

/**
 * Kotlin YAN analiz motoru. Yan tahmini rakamdan türetilmez: her model kendi ekseninin
 * (Büyük/Küçük, Tek/Çift, birleşik) dizisini öğrenir.
 *
 * Her model bir eksendeki k sınıf için olasılık dağılımı üretir. predict() yalnızca [SequenceMemory]
 * içindeki (öğrenilmiş) geçmişi görür; learn() hafıza GÜNCELLENMEDEN önce çağrılır, böylece öğrenilen
 * bağlam tahmin anındaki bağlamla birebir aynıdır.
 */
class SideCtx(val mem: SequenceMemory, val regime: Int)

abstract class CatModel(val id: String, val name: String, val axis: Axis, val k: Int) {
    abstract fun predict(c: SideCtx): DoubleArray
    open fun learn(c: SideCtx, actual: Int) {}
    open fun write(o: Out) {}
    open fun read(i: In) {}

    protected fun ktFreq(counts: DoubleArray): DoubleArray {
        var t = 0.0; for (x in counts) t += x
        return DoubleArray(k) { (counts[it] + 0.5) / (t + 0.5 * k) }
    }
}

/** Tarihsel frekans. */
class FreqModel(axis: Axis, k: Int) : CatModel("ks_freq", "Frekans", axis, k) {
    override fun predict(c: SideCtx) = ktFreq(c.mem.counts(axis))
}

/** Son pencere frekansı. */
class RecentModel(axis: Axis, k: Int, private val w: Int = 20) : CatModel("ks_recent", "Son pencere ($w)", axis, k) {
    override fun predict(c: SideCtx): DoubleArray {
        val wc = c.mem.windowCounts(axis, w)
        var t = 0.0; for (x in wc) t += x
        // Küçük örnekte tarihsel frekansa yaslan: gürültüyü kovalama.
        val base = ktFreq(c.mem.counts(axis))
        val lam = 8.0
        return Mx.normalize(DoubleArray(k) { (wc[it] + lam * base[it]) / (t + lam) })
    }
}

/** n. dereceden Markov geçiş olasılığı; az gözlemde tarihsel frekansa yaslanır. */
class MarkovModel(axis: Axis, k: Int, private val order: Int) :
    CatModel("ks_markov$order", "Geçiş olasılığı ($order)", axis, k) {
    private val table = DoubleArray(Mx.pow(k, order) * k)
    private val lam = 2.0

    override fun predict(c: SideCtx): DoubleArray {
        val base = ktFreq(c.mem.counts(axis))
        val ctx = c.mem.ctx(axis, order)
        if (ctx < 0) return base
        var t = 0.0; for (j in 0 until k) t += table[ctx * k + j]
        return Mx.normalize(DoubleArray(k) { (table[ctx * k + it] + lam * base[it]) / (t + lam) })
    }

    override fun learn(c: SideCtx, actual: Int) {
        val ctx = c.mem.ctx(axis, order)
        if (ctx >= 0) table[ctx * k + actual]++
    }

    override fun write(o: Out) = o.da(table)
    override fun read(i: In) = i.fill(table)
}

/**
 * Seri (streak) modeli. "Artık kesin döner" mantığı YOKTUR: seri uzunluğuna göre devam/kırılma oranı
 * yalnızca geçmişte gözlenen ilişki kadar (KT yumuşatmayla) tahmini genel orandan ayırır.
 */
class StreakModel(axis: Axis, k: Int) : CatModel("ks_streak", "Seri (streak)", axis, k) {
    companion object { const val B = 8 }
    val cont = DoubleArray(B + 1)
    val brk = DoubleArray(B + 1)

    private fun bucket(run: Int) = minOf(run, B)

    /** Seri uzunluğu r iken devam olasılığı (öğrenilmiş). */
    fun continueProb(run: Int): Double {
        var cs = 0.0; var bsum = 0.0
        for (b in 1..B) { cs += cont[b]; bsum += brk[b] }
        val overall = (cs + 1.0 / k * 4) / (cs + bsum + 4.0)
        val b = bucket(run)
        return (cont[b] + overall * 6.0) / (cont[b] + brk[b] + 6.0)
    }

    override fun predict(c: SideCtx): DoubleArray {
        val last = c.mem.sym(axis, 1)
        val base = ktFreq(c.mem.counts(axis))
        if (last < 0) return base
        val pc = continueProb(c.mem.run(axis))
        var other = 0.0; for (j in 0 until k) if (j != last) other += base[j]
        return Mx.normalize(DoubleArray(k) { if (it == last) pc else (1 - pc) * base[it] / other })
    }

    override fun learn(c: SideCtx, actual: Int) {
        val last = c.mem.sym(axis, 1)
        if (last < 0) return
        val b = bucket(c.mem.run(axis))
        if (actual == last) cont[b]++ else brk[b]++
    }

    override fun write(o: Out) { o.da(cont); o.da(brk) }
    override fun read(i: In) { i.fill(cont); i.fill(brk) }
}

/** Rejime koşullu frekans. */
class RegimeFreqModel(axis: Axis, k: Int) : CatModel("ks_regime", "Rejim frekansı", axis, k) {
    private val table = DoubleArray(V13.REGIMES * k)
    override fun predict(c: SideCtx): DoubleArray {
        val base = ktFreq(c.mem.counts(axis))
        var t = 0.0; for (j in 0 until k) t += table[c.regime * k + j]
        val lam = 10.0
        return Mx.normalize(DoubleArray(k) { (table[c.regime * k + it] + lam * base[it]) / (t + lam) })
    }
    override fun learn(c: SideCtx, actual: Int) { table[c.regime * k + actual]++ }
    override fun write(o: Out) = o.da(table)
    override fun read(i: In) = i.fill(table)
}

/**
 * Dizi kalıbı (Kalıp analizi stili): farklı uzunluktaki son-bağlamların geçmişteki devamlarına oy verir;
 * ayna (ters çevrilmiş) kalıplar yarım ağırlıkla katılır. Tablo boyutu k^L ile sınırlıdır.
 */
class PatternModel(axis: Axis, k: Int, private val lens: IntArray) : CatModel("ks_pattern", "Dizi kalıbı", axis, k) {
    private val tables = Array(lens.size) { DoubleArray(Mx.pow(k, lens[it]) * k) }

    private fun mirrorCtx(ctx: Int, len: Int): Int {
        var m = 0; var x = ctx
        val digits = IntArray(len)
        for (j in len - 1 downTo 0) { digits[j] = x % k; x /= k }
        for (j in 0 until len) m = m * k + ((k - 1) - digits[j])
        return m
    }

    override fun predict(c: SideCtx): DoubleArray {
        val votes = DoubleArray(k); var tot = 0.0
        for (li in lens.indices) {
            val len = lens[li]
            val ctx = c.mem.ctx(axis, len)
            if (ctx < 0) continue
            val w = len.toDouble().pow(1.5)
            val t = tables[li]
            var s = 0.0; for (j in 0 until k) s += t[ctx * k + j]
            if (s > 0) { val ww = w * s / (s + 2.0); for (j in 0 until k) votes[j] += ww * t[ctx * k + j] / s; tot += ww }
            val mc = mirrorCtx(ctx, len)
            var ms = 0.0; for (j in 0 until k) ms += t[mc * k + j]
            if (ms > 0) { val ww = 0.5 * w * ms / (ms + 2.0); for (j in 0 until k) votes[(k - 1) - j] += ww * t[mc * k + j] / ms; tot += ww }
        }
        val base = ktFreq(c.mem.counts(axis))
        if (tot <= 0) return base
        return Mx.normalize(DoubleArray(k) { (votes[it] + 0.2 * tot * base[it]) / (1.2 * tot) })
    }

    override fun learn(c: SideCtx, actual: Int) {
        for (li in lens.indices) {
            val ctx = c.mem.ctx(axis, lens[li])
            if (ctx >= 0) tables[li][ctx * k + actual]++
        }
    }

    override fun write(o: Out) { tables.forEach { o.da(it) } }
    override fun read(i: In) { tables.forEach { i.fill(it) } }
}

/** Üç yan ekseni için Kotlin yan modelleri. */
class KotlinSideEngine(private val alphabet: Alphabet) {
    val axes = listOf(Axis.BS, Axis.OE, Axis.COMB)
    private val models: Map<Axis, List<CatModel>> = axes.associateWith { build(it) }

    private fun build(axis: Axis): List<CatModel> {
        val k = alphabet.k(axis)
        val lens = if (k == 2) intArrayOf(3, 4, 5, 6, 8) else intArrayOf(2, 3, 4, 5)
        return listOf(
            FreqModel(axis, k), RecentModel(axis, k), MarkovModel(axis, k, 1), MarkovModel(axis, k, 2),
            MarkovModel(axis, k, 3), StreakModel(axis, k), RegimeFreqModel(axis, k), PatternModel(axis, k, lens)
        )
    }

    fun models(axis: Axis): List<CatModel> = models[axis] ?: emptyList()
    fun model(axis: Axis, id: String): CatModel? = models(axis).firstOrNull { it.id == id }

    /** Tahmin anı çıktıları: kotlin yan modeli başına ModelOutput. */
    fun outputs(mem: SequenceMemory, regime: Int): List<ModelOutput> {
        val c = SideCtx(mem, regime)
        val bs = models(Axis.BS); val oe = models(Axis.OE); val cb = models(Axis.COMB)
        return bs.indices.map { i ->
            ModelOutput(bs[i].id, "Kotlin yan · " + bs[i].name, Group.KOTLIN,
                bs = bs[i].predict(c), oe = oe[i].predict(c), comb = cb[i].predict(c))
        }
    }

    /** Hafıza güncellenmeden ÖNCE çağrılır. actual: BS, OE, COMB gerçek sınıfları. */
    fun learn(mem: SequenceMemory, regime: Int, bs: Int, oe: Int, comb: Int) {
        val c = SideCtx(mem, regime)
        models(Axis.BS).forEach { it.learn(c, bs) }
        models(Axis.OE).forEach { it.learn(c, oe) }
        models(Axis.COMB).forEach { it.learn(c, comb) }
    }

    fun write(o: Out) { axes.forEach { a -> models(a).forEach { it.write(o) } } }
    fun read(i: In) { axes.forEach { a -> models(a).forEach { it.read(i) } } }
}
