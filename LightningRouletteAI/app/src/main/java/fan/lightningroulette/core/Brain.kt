package fan.lightningroulette.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

class BrainConfig(
    val window: Int = 300,
    val decay: Double = 0.02,
    val nCand: Int = 5,
    val kChoices: IntArray = intArrayOf(1, 2, 3),
    val dir: Dir = Dir.BI,
    val features: Set<String> = ALL_FEATURES,
    val kotlinWeight: Double = 0.5,
    val minSample: Int = 50,
    val sectors: Sectors = Sectors.DEFAULT,
    val diversity: Boolean = true
) {
    fun copy(
        window: Int = this.window, decay: Double = this.decay, nCand: Int = this.nCand, kChoices: IntArray = this.kChoices, dir: Dir = this.dir,
        features: Set<String> = this.features, kotlinWeight: Double = this.kotlinWeight, minSample: Int = this.minSample,
        sectors: Sectors = this.sectors, diversity: Boolean = this.diversity
    ) = BrainConfig(window, decay, nCand, kChoices, dir, features, kotlinWeight, minSample, sectors, diversity)

    companion object {
        val ALL_FEATURES = setOf("wheel", "sector", "region", "neighbor", "frequency", "pattern", "transition", "table", "ML")
    }
}

val SCORE_NAMES: Map<String, String> = mapOf(
    "wheel" to "Wheel skoru", "sector" to "Sector skoru", "region" to "Region skoru", "neighbor" to "Neighbor skoru",
    "frequency" to "Frequency skoru", "pattern" to "Pattern skoru", "transition" to "Transition skoru", "kml" to "ML skoru"
)
const val SCORE_PY = "Python skoru"
const val SCORE_TABLE = "Table uyumu"

/** Kilitli tahmin üreten çekirdek: Kotlin meclisi + (varsa) Python meclisi + Table motoru → hakem. */
class Brain(val cfg: BrainConfig) {
    companion object {
        /** Üyelerin bakabileceği en fazla geçmiş uzunluğu (tüm üyeler için yeterli). Fiziksel kesme: [cut]. */
        const val LOOK = 4000
        fun cut(values: IntArray, end: Int): IntArray = values.copyOfRange(max(0, end - LOOK), end)
    }

    val pr = Params(cfg.window, cfg.decay, cfg.sectors)
    val members: List<Member> = buildList {
        add(WheelMember(pr))
        if ("sector" in cfg.features) add(SectorMember(pr))
        if ("region" in cfg.features) add(RegionMember(pr))
        if ("neighbor" in cfg.features) add(NeighborMember(pr))
        if ("frequency" in cfg.features) add(FrequencyMember(pr))
        if ("pattern" in cfg.features) add(PatternMember(pr))
        if ("transition" in cfg.features) add(TransitionMember(pr))
        if ("ML" in cfg.features) add(KotlinMlMember(pr))
    }
    var hedgeK = Hedge(members.size)
    var hedgeP: Hedge? = null
    val table = TableEngine(pr)
    var tau = 1.0
    var wK = cfg.kotlinWeight
    var steps = 0
    private val ring = ArrayList<Pair<DoubleArray, Int>>()

    private fun temper(p: DoubleArray, t: Double): DoubleArray {
        if (abs(t - 1.0) < 1e-9) return p.copyOf()
        return norm(DoubleArray(Wheel.N) { p[it].pow(t) })
    }

    private fun fitTau(): Double {
        var best = tau; var bl = Double.MAX_VALUE
        var t = 0.30
        while (t <= 1.5001) {
            var s = 0.0
            for ((p, a) in ring) s -= ln(max(temper(p, t)[a], 1e-12))
            if (s < bl - 1e-12) { bl = s; best = t }
            t += 0.05
        }
        return best
    }

    /** h: EN ESKİ → EN YENİ, tahmin anında bilinen geçmiş (fiziksel olarak kesilmiş). */
    fun predict(h: IntArray, py: PyOut?, pyStatus: String = if (py != null) "OK" else "OFF"): PredictionCore {
        val mp = members.map { it.predict(h) }
        val pK = hedgeK.mix(mp)
        var pP: DoubleArray? = null
        if (py != null && py.probs.isNotEmpty()) {
            if (hedgeP == null || hedgeP!!.m != py.probs.size) hedgeP = Hedge(py.probs.size)
            pP = hedgeP!!.mix(py.probs)
        }
        val raw = if (pP != null) norm(DoubleArray(Wheel.N) { wK * pK[it] + (1 - wK) * pP[it] }) else pK
        val cal = temper(raw, tau)
        val calls = table.predict(h)
        val adj = if ("table" in cfg.features) tableBonus(cal, calls) else cal
        val wk = hedgeK.w.copyOf()
        val wp = hedgeP?.w?.copyOf()
        val kScale = if (pP != null) wK else 1.0
        val pyIds = py?.ids ?: emptyList(); val pyProbs = py?.probs ?: emptyList()
        val contrib: (Int) -> Map<String, Double> = { c ->
            val m = LinkedHashMap<String, Double>()
            for (i in members.indices) {
                val nm = SCORE_NAMES[members[i].id] ?: members[i].id
                m[nm] = (m[nm] ?: 0.0) + kScale * wk[i] * ln(Wheel.N * max(mp[i][c], 1e-9))
            }
            if (pP != null && wp != null) {
                var s = 0.0
                for (j in pyProbs.indices) s += (1 - wK) * wp[j] * ln(Wheel.N * max(pyProbs[j][c], 1e-9))
                m[SCORE_PY] = s
            }
            var aligned = 0
            for (call in calls) if (TableCats.classOf(call.cat, c) == call.cls) aligned++
            m[SCORE_TABLE] = aligned / 5.0
            m
        }
        val cands = Candidates.build(adj, cfg.nCand, cfg.kChoices, cfg.dir, cfg.sectors, cfg.diversity, contrib)
        return PredictionCore(adj, raw, pK, pP, members.map { it.id }, mp, pyIds, pyProbs, cands, calls, pyStatus, wk, wp, wK, tau)
    }

    private fun tableBonus(p: DoubleArray, calls: List<TableCall>): DoubleArray {
        val out = DoubleArray(Wheel.N)
        for (n in 0 until Wheel.N) {
            var aligned = 0
            for (c in calls) if (TableCats.classOf(c.cat, n) == c.cls) aligned++
            out[n] = p[n] * exp(0.08 * (aligned - 2.5))
        }
        return norm(out)
    }

    /** h: gerçek sonuçtan ÖNCEKİ geçmiş. Yalnızca sonuç açıldıktan SONRA çağrılır. */
    fun learn(pred: PredictionCore, h: IntArray, actual: Int) {
        hedgeK.update(pred.memberProbs, actual)
        if (pred.pyProbs.isNotEmpty()) hedgeP?.update(pred.pyProbs, actual)
        table.learn(h, actual)
        ring.add(pred.pRaw to actual)
        if (ring.size > 300) ring.removeAt(0)
        steps++
        if (steps % 10 == 0 && ring.size >= 60) tau = fitTau()
    }

    fun stateJson(): String = Json.stringify(mapOf(
        "v" to 1, "ids" to members.map { it.id }, "hK" to hedgeK.w, "hP" to hedgeP?.w, "tau" to tau, "wK" to wK, "tbl" to table.state(), "steps" to steps
    ))

    /** Üye içi durumlar (Kotlin ML ağırlıkları) dahil tam anlık görüntü: LAB checkpoint için. */
    fun fullStateJson(): String {
        val ms = LinkedHashMap<String, Any?>()
        for (m in members) if (m is StatefulMember) ms[m.id] = m.stateMap()
        return Json.stringify(mapOf("brain" to Json.parse(stateJson()), "members" to ms))
    }

    fun restoreFull(json: String): Boolean {
        return try {
            val m = Json.parse(json).jmap()
            if (!restore(Json.stringify(m["brain"]))) return false
            val ms = m["members"].jmap()
            for (mem in members) if (mem is StatefulMember) { val st = ms[mem.id]; if (st != null) mem.restoreState(st.jmap()) }
            true
        } catch (e: Exception) { false }
    }

    fun restore(json: String): Boolean {
        return try {
            val m = Json.parse(json).jmap()
            if (m["ids"].jlist().map { it.jstr() } != members.map { it.id }) return false
            val hk = m["hK"].jdoubles(); if (hk.size != members.size) return false
            hedgeK.w = hk
            val hp = m["hP"]
            if (hp != null) { val a = hp.jdoubles(); if (a.isNotEmpty()) { hedgeP = Hedge(a.size); hedgeP!!.w = a } }
            tau = m["tau"].jnum(1.0); wK = m["wK"].jnum(cfg.kotlinWeight); steps = m["steps"].jint()
            table.restore(m["tbl"].jmap())
            true
        } catch (e: Exception) { false }
    }
}

object Candidates {
    /** Merkezleri kalibre olasılığa göre sıralar; her merkez için k'yi, aralık olasılığının tabana oranı (lift) en yüksek olacak şekilde seçer;
     *  mümkünse en az 2 farklı sektör kaynağı bulunmasını sağlar (Prompt §4). Minimum 3, maksimum 5 aday. */
    fun build(p: DoubleArray, nCand: Int, kChoices: IntArray, dir: Dir, sectors: Sectors, diversity: Boolean, contrib: (Int) -> Map<String, Double>): List<Candidate> {
        val n = nCand.coerceIn(3, 5)
        val order = (0 until Wheel.N).sortedWith(compareByDescending<Int> { p[it] }.thenBy { it })
        val picked = ArrayList<Int>(order.take(n))
        if (diversity && picked.map { sectors.of[it] }.toSet().size < 2) {
            val alt = order.firstOrNull { sectors.of[it] != sectors.of[picked[0]] }
            if (alt != null) picked[picked.size - 1] = alt
        }
        val sorted = picked.sortedWith(compareByDescending<Int> { p[it] }.thenBy { it })
        val ks = kChoices.sorted()
        return sorted.mapIndexed { idx, c ->
            var bestK = ks.first(); var bestLift = -1.0; var bestMass = 0.0
            for (k in ks) {
                val sp = Wheel.span(c, k, dir)
                var mass = 0.0; for (x in sp) mass += p[x]
                val lift = mass / (sp.size.toDouble() / Wheel.N)
                if (lift > bestLift + 1e-12) { bestLift = lift; bestK = k; bestMass = mass }
            }
            Candidate(c, bestK, dir, idx + 1, p[c], bestMass, sectors.of[c], Regions.of[c], contrib(c))
        }
    }
}

object Evaluator {
    fun evaluate(pred: PredictionCore, actual: Int, sectors: Sectors = Sectors.DEFAULT): Eval {
        val cs = pred.candidates
        val rank = cs.indexOfFirst { it.n == actual } + 1
        val union = HashSet<Int>(); for (c in cs) for (x in c.span) union.add(x)
        val top = cs.first()
        val hits = BooleanArray(5) { cat -> TableCats.classOf(cat, actual) == pred.table[cat].cls }
        val pa = pred.pFinal[actual]
        return Eval(
            actual, top.n == actual, rank > 0, rank, actual in union,
            sectors.of[actual] == sectors.of[top.n], Regions.of[actual] == Regions.of[top.n], hits, union.size, pa, -ln(max(pa, 1e-12))
        )
    }
}
