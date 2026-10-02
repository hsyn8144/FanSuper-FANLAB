package fan.lightningroulette.core

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** Bir LAB deneyinin parametreleri (Prompt §20). Her deney benzersiz kimlik alır (kimliği veritabanı verir). */
class ExpConfig(
    val hypothesis: String,
    val window: Int = 300, val k: Int = 0, val cands: Int = 5, val dir: Dir = Dir.BI,
    val features: Set<String> = BrainConfig.ALL_FEATURES, val decay: Double = 0.02, val kotlinWeight: Double = 0.5, val seed: Long = 42L
) {
    fun brain(sectors: Sectors = Sectors.DEFAULT) = BrainConfig(window, decay, cands, if (k == 0) intArrayOf(1, 2, 3) else intArrayOf(k), dir, features, kotlinWeight, 50, sectors)
    private fun canon(): String = "w$window|k$k|c$cands|${dir.code}|${features.sorted().joinToString(",")}|d$decay|kw$kotlinWeight|s$seed"
    fun paramHash(): String { var h = Stats.FNV0; for (c in canon()) h = Stats.fnv(h, c.code.toLong()); return java.lang.Long.toHexString(h).takeLast(12) }
    fun label(): String = "w$window · ${if (k == 0) "k-oto" else "k$k"} · c$cands · ${dir.code}"
    fun with(hypothesis: String = this.hypothesis, window: Int = this.window, k: Int = this.k, cands: Int = this.cands, features: Set<String> = this.features, decay: Double = this.decay) =
        ExpConfig(hypothesis, window, k, cands, dir, features, decay, kotlinWeight, seed)
    fun toMap(): Map<String, Any?> = mapOf("h" to hypothesis, "w" to window, "k" to k, "c" to cands, "d" to dir.code, "f" to features.sorted(), "dec" to decay, "kw" to kotlinWeight, "seed" to seed)
    companion object {
        fun fromMap(m: Map<String, Any?>) = ExpConfig(m["h"].jstr(), m["w"].jint(300), m["k"].jint(0), m["c"].jint(5), Dir.of(m["d"].jstr("bi")),
            m["f"].jlist().map { it.jstr() }.toSet().ifEmpty { BrainConfig.ALL_FEATURES }, m["dec"].jnum(0.02), m["kw"].jnum(0.5), m["seed"].jlong(42L))
    }
}

/** Yüzde değerleri (0–100): gözlenen, taban, fark %95 aralığı (taban + CI). */
class Metric(val name: String, val obs: Double, val base: Double, val lo: Double, val hi: Double) {
    val delta: Double get() = obs - base
    fun toMap(): Map<String, Any?> = mapOf("n" to name, "o" to obs, "b" to base, "lo" to lo, "hi" to hi)
    companion object { fun fromMap(m: Map<String, Any?>) = Metric(m["n"].jstr(), m["o"].jnum(), m["b"].jnum(), m["lo"].jnum(), m["hi"].jnum()) }
}

class ExpResult(
    val cfg: ExpConfig, val n: Int, val nVal: Int, val metrics: List<Metric>,
    val deltaPp: Double, val ciLo: Double, val ciHi: Double, val permP: Double, val valDeltaPp: Double,
    val brier: Double, val baseBrier: Double, val logLoss: Double, val baseLogLoss: Double, val ece: Double,
    val cls: String, val hash: Long, val leakFree: Boolean, val status: String = "DONE"
) {
    val headline: Metric get() = metrics.firstOrNull { it.name == "Candidate-${cfg.cands}" } ?: metrics.first()
    fun toMap(): Map<String, Any?> = mapOf("cfg" to cfg.toMap(), "n" to n, "nVal" to nVal, "metrics" to metrics.map { it.toMap() }, "delta" to deltaPp, "lo" to ciLo, "hi" to ciHi,
        "permP" to permP, "valDelta" to valDeltaPp, "brier" to brier, "baseBrier" to baseBrier, "ll" to logLoss, "baseLl" to baseLogLoss, "ece" to ece, "cls" to cls,
        "hash" to hash.toString(), "leak" to leakFree, "status" to status)
    fun toJson(): String = Json.stringify(toMap())
    companion object {
        fun fromJson(s: String): ExpResult? = try {
            val m = Json.parse(s).jmap()
            ExpResult(ExpConfig.fromMap(m["cfg"].jmap()), m["n"].jint(), m["nVal"].jint(), m["metrics"].jlist().map { Metric.fromMap(it.jmap()) }, m["delta"].jnum(), m["lo"].jnum(), m["hi"].jnum(),
                m["permP"].jnum(1.0), m["valDelta"].jnum(), m["brier"].jnum(), m["baseBrier"].jnum(), m["ll"].jnum(), m["baseLl"].jnum(), m["ece"].jnum(), m["cls"].jstr("C"),
                m["hash"].jstr("0").toLong(), m["leak"].jbool(true), m["status"].jstr("DONE"))
        } catch (e: Exception) { null }
    }
}

class ExpRun(val result: ExpResult, val steps: List<StepRec>, val replay: ReplayResult, val oosStart: Int, val valStart: Int)

object LabRunner {
    fun segmentLen(n: Int) = ceil(n / 5.0).toInt()
    /** Kronolojik Train / Validation / OOS (%60/%20/%20) — rastgele shuffle yasaktır (Prompt §21, §22). */
    fun valStart(n: Int) = n - 2 * segmentLen(n)
    fun oosStart(n: Int) = n - segmentLen(n)

    private fun excess(steps: List<StepRec>, hit: (StepRec) -> Boolean, base: (StepRec) -> Double): DoubleArray = DoubleArray(steps.size) { (if (hit(steps[it])) 1.0 else 0.0) - base(steps[it]) }

    fun metricList(steps: List<StepRec>, cands: Int, seed: Long): List<Metric> {
        val out = ArrayList<Metric>()
        fun add(name: String, hit: (StepRec) -> Boolean, base: (StepRec) -> Double) {
            if (steps.isEmpty()) { out.add(Metric(name, 0.0, 0.0, 0.0, 0.0)); return }
            val ex = excess(steps, hit, base)
            val ci = Stats.bootstrapCI(ex, 1000, seed)
            var b = 0.0; for (s in steps) b += base(s); b /= steps.size
            val o = b + ci[0]
            out.add(Metric(name, o * 100, b * 100, (b + ci[1]) * 100, (b + ci[2]) * 100))
        }
        add("Exact", { it.exact }, { 1.0 / Wheel.N })
        for (c in 3..5) if (c <= cands) add("Candidate-$c", { it.candRank in 1..c }, { c.toDouble() / Wheel.N })
        add("Neighbor", { it.neighbor }, { it.coverage.toDouble() / Wheel.N })
        add("Sector", { it.sectorHit }, { it.topSectorBase })
        add("Region", { it.regionHit }, { it.topRegionBase })
        for (cat in 0 until 5) add(listOf("Color", "Parity", "High/Low", "Dozen", "Column")[cat], { it.tableHit[cat] }, { TableCats.baseline(cat) })
        return out
    }

    fun permutationP(steps: List<StepRec>, c: Int, seed: Long, b: Int = 1000): Double {
        val n = steps.size; if (n < 30) return 1.0
        var obs = 0; for (s in steps) if (s.candRank in 1..c) obs++
        val bb = min(b, max(200, 6_000_000 / (n * c)))
        val rnd = java.util.Random(seed)
        val act = IntArray(n) { steps[it].actual }
        var ge = 0
        for (r in 0 until bb) {
            for (i in n - 1 downTo 1) { val j = rnd.nextInt(i + 1); val t = act[i]; act[i] = act[j]; act[j] = t }
            var h = 0
            for (i in 0 until n) { val cs = steps[i].centers; val a = act[i]; for (q in 0 until min(c, cs.size)) if (cs[q] == a) { h++; break } }
            if (h >= obs) ge++
        }
        return (ge + 1.0) / (bb + 1)
    }

    fun classify(n: Int, delta: Double, lo: Double, hi: Double, permP: Double, valDelta: Double, leakFree: Boolean, robust: Int?): String {
        if (!leakFree) return "L"
        if (n < 300) return "S"
        if (valDelta - delta > 2.0 && valDelta > 1.5 && delta < 0.5) return "O"
        if (hi < 0 && delta < 0) return "F"
        if (delta > 0 && lo > 0 && permP < 0.01 && (robust ?: 0) >= 60) return "A"
        if (delta > 0 && permP < 0.10) return "B"
        if (delta < -0.3) return "D"
        return "C"
    }

    fun run(
        values: IntArray, ts: LongArray, cfg: ExpConfig, sectors: Sectors = Sectors.DEFAULT, py: ((Int) -> PyOut?)? = null,
        cancel: (() -> Boolean)? = null, progress: ((Int, Int) -> Unit)? = null,
        resume: Resume? = null, checkpointEvery: Int = 0, onCheckpoint: ((Resume) -> Unit)? = null
    ): ExpRun {
        val n = values.size
        val bc = cfg.brain(sectors)
        val vs = valStart(n); val os = oosStart(n)
        val learnFrom = max(bc.minSample, vs - 2000)
        val rp = ReplayEngine(bc).run(values, ts, learnFrom, vs, n, py, cancel, progress, resume = resume, checkpointEvery = checkpointEvery, onCheckpoint = onCheckpoint)
        val oos = rp.steps.filter { it.i >= os }
        val vl = rp.steps.filter { it.i < os }
        val metrics = metricList(oos, cfg.cands, cfg.seed)
        val head = metrics.firstOrNull { it.name == "Candidate-${cfg.cands}" } ?: metrics.first()
        val hexc = excess(oos, { it.candRank in 1..cfg.cands }, { cfg.cands.toDouble() / Wheel.N })
        val ci = Stats.bootstrapCI(hexc, 1000, cfg.seed)
        val permP = permutationP(oos, cfg.cands, cfg.seed)
        val valDelta = if (vl.isEmpty()) 0.0 else Stats.mean(excess(vl, { it.candRank in 1..cfg.cands }, { cfg.cands.toDouble() / Wheel.N })) * 100
        // kalibrasyon: aday-N olayı (p5 ↔ isabet)
        val p = DoubleArray(oos.size) { oos[it].p5 }; val y = BooleanArray(oos.size) { oos[it].candRank in 1..cfg.cands }
        val bins = Stats.calibration(p, y)
        var br = 0.0; for (i in p.indices) br += (p[i] - (if (y[i]) 1.0 else 0.0)).let { it * it }
        br = if (oos.isEmpty()) 0.0 else br / oos.size
        val bp = cfg.cands.toDouble() / Wheel.N
        val ll = if (oos.isEmpty()) 0.0 else Stats.mean(DoubleArray(oos.size) { oos[it].ll })
        val delta = ci[0] * 100; val lo = ci[1] * 100; val hi = ci[2] * 100
        val cls = classify(oos.size, delta, lo, hi, permP, valDelta, rp.leakFree, null)
        val res = ExpResult(cfg, oos.size, vl.size, metrics, delta, lo, hi, permP, valDelta, br, bp * (1 - bp), ll, ln(Wheel.N.toDouble()), Stats.ece(bins), cls, rp.hash, rp.leakFree,
            if (rp.cancelled) "CANCELLED" else "DONE")
        return ExpRun(res, rp.steps, rp, os, vs)
    }
}

/** Robustness skoru (Prompt §43): pozitif bileşenler − negatif bileşenler. Garanti değildir. */
class RobustPart(val name: String, val value: Double, val max: Double, val note: String)
class RobustScore(val score: Int, val cls: String, val pos: List<RobustPart>, val neg: List<RobustPart>)

object Robustness {
    /** sensCells: parametre ızgarası hücre Δ'ları (null = hesaplanmadı); regimeSigns: rejim Δ işaretleri (n ≥ 100); drift: eğim/se; phi: Kotlin üyeleri arası ortalama |φ| */
    fun compute(res: ExpResult, sensDeltas: List<Double>?, spike: Boolean, regimeDeltas: List<Double>, regimeLowN: Int, periodDeltas: List<Double>, phiMean: Double, totalExperiments: Int): RobustScore {
        val pos = ArrayList<RobustPart>(); val neg = ArrayList<RobustPart>()
        val oosP = 25.0 * (1 - res.permP) * (if (res.deltaPp > 0) 1.0 else 0.5)
        pos.add(RobustPart("OOS performansı", oosP.coerceIn(0.0, 25.0), 25.0, "perm. p = ${S.f(res.permP, 2)}"))
        pos.add(RobustPart("Calibration", (20.0 * (1 - res.ece / 0.12)).coerceIn(0.0, 20.0), 20.0, "ECE ${S.f(res.ece, 3)}"))
        val stab = if (periodDeltas.isEmpty()) 7.5 else 15.0 * periodDeltas.count { it >= 0 } / periodDeltas.size
        pos.add(RobustPart("Stability (dönemler)", stab, 15.0, "${periodDeltas.count { it >= 0 }}/${periodDeltas.size} dönem ≥ 0"))
        val same = regimeDeltas.count { (it >= 0) == (res.deltaPp >= 0) }
        pos.add(RobustPart("Rejim tutarlılığı", if (regimeDeltas.isEmpty()) 7.5 else 15.0 * same / regimeDeltas.size, 15.0, "$same/${regimeDeltas.size} rejim aynı yönde"))
        if (sensDeltas != null && sensDeltas.isNotEmpty()) {
            val ok = sensDeltas.count { it >= 0 }
            pos.add(RobustPart("Parametre stabilitesi", if (spike) min(5.0, 15.0 * ok / sensDeltas.size / 2) else 15.0 * ok / sensDeltas.size, 15.0, "$ok/${sensDeltas.size} hücre ≥ 0" + if (spike) " · tek nokta" else ""))
        } else pos.add(RobustPart("Parametre stabilitesi", 0.0, 15.0, "hesaplanmadı"))
        pos.add(RobustPart("Diversity", (10.0 * (1 - phiMean / 0.5)).coerceIn(0.0, 10.0), 10.0, "ort. |φ| ${S.f(phiMean, 2)}"))
        neg.add(RobustPart("Overfit", if (res.cls == "O") 10.0 else 0.0, 10.0, if (res.cls == "O") "overfit şüphesi" else "işaret yok"))
        neg.add(RobustPart("Leakage", if (!res.leakFree) 100.0 else 0.0, 100.0, if (res.leakFree) "kapılar temiz" else "LEAKAGE"))
        neg.add(RobustPart("Data-snooping", min(15.0, 3.0 * log10(max(1.0, totalExperiments.toDouble()))), 15.0, "$totalExperiments deney"))
        neg.add(RobustPart("Yetersiz sample", if (res.n < 300) 10.0 else if (regimeLowN > 0) 5.0 else 0.0, 10.0, if (regimeLowN > 0) "$regimeLowN rejimde n < 100" else "yeterli"))
        val sc = (pos.sumOf { it.value } - neg.sumOf { it.value }).toInt().coerceIn(0, 100)
        val cls = when { !res.leakFree -> "L"; res.n < 300 -> "S"; res.cls == "O" -> "O"; sc >= 80 -> "A"; sc >= 60 -> "B"; sc >= 35 -> "C"; sc >= 20 -> "D"; else -> "F" }
        return RobustScore(sc, cls, pos, neg)
    }
}
