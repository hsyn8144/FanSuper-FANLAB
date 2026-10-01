package fan.superai.v13

/** FAN LAB ekranları için değişmez (iş parçacığı güvenli) görünüm — motor iş parçacığında üretilir. */
class StatsView(
    val total: Int, val numberAcc: Double, val top2Acc: Double, val bsAcc: Double, val oeAcc: Double, val sideAcc: Double,
    val numberLL: Double, val sideLL: Double, val baseNumber: Double, val baseBs: Double, val baseOe: Double, val baseSide: Double,
    val recentNumber: Double, val recentSide: Double, val recentN: Int, val blendProduct: Double
)

class RegimeRow(val name: String, val steps: Int, val n: Int, val numberAcc: Double, val top2Acc: Double, val sideAcc: Double, val current: Boolean)
class RegimeView(val current: String, val duration: Int, val rows: List<RegimeRow>, val transitions: DoubleArray, val history: List<IntArray>)

class DiversityView(
    val axis: String, val ids: List<String>, val names: List<String>, val corr: Array<DoubleArray>,
    val agree: Array<DoubleArray>, val disagreement: Double, val diversity: Double, val tau: Double
)

class CalView(
    val axis: String, val n: Int, val gap: Double, val brier: Double, val logLoss: Double, val entropy: Double,
    val accuracy: Double, val bins: List<ReliabilityBin>
)

class SeqView(
    val axis: String, val labels: List<String>, val counts: DoubleArray, val transitions: DoubleArray,
    val last: Int, val run: Int
)

class V13Insights(
    val count: Int, val stats: StatsView, val models: Map<String, List<ModelRow>>, val diversity: List<DiversityView>,
    val calibration: List<CalView>, val regime: RegimeView, val sequences: List<SeqView>,
    val leakChecks: Long, val leakViolations: Long, val leakMessages: List<String>, val tau: Map<String, Double>
) {
    companion object {
        fun build(b: FanBrain): V13Insights {
            val st = b.stats
            fun r(a: Int) = if (st.total == 0) 0.0 else a.toDouble() / st.total
            val stats = StatsView(st.total, r(st.numberHits), r(st.top2Hits), r(st.bsHits), r(st.oeHits), r(st.sideHits),
                if (st.total == 0) 0.0 else st.numberLL / st.total, if (st.total == 0) 0.0 else st.sideLL / st.total,
                r(st.baseNumber), r(st.baseBs), r(st.baseOe), r(st.baseSide),
                st.rollNumber.rate(), st.rollSide.rate(), st.rollNumber.count, st.blendProduct)
            val models = LinkedHashMap<String, List<ModelRow>>()
            val div = ArrayList<DiversityView>(); val cal = ArrayList<CalView>(); val tau = LinkedHashMap<String, Double>()
            for (ax in Axis.values()) {
                val e = b.ens[ax.ordinal]
                models[ax.name] = e.rows().sortedByDescending { it.weight }
                val ids = e.trackers.keys.toList()
                val n = ids.size
                val corr = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else e.corr(ids[i], ids[j]) } }
                val agree = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else e.agreement(ids[i], ids[j]) } }
                div += DiversityView(ax.name, ids, ids.map { e.name(it) }, corr, agree, e.disagreeEwma, e.diversityEwma, e.tau)
                val t = e.finalTracker
                cal += CalView(ax.name, t.n, t.cal.gap(), t.brier, t.logLoss, t.entropy, t.accuracy, t.cal.table())
                tau[ax.name] = e.tau
            }
            val rg = b.regime
            val rows = (0 until V13.REGIMES).map { i ->
                val p = rg.perf[i]; val n = p[0].toInt()
                RegimeRow(V13.REGIME_NAMES[i], rg.steps[i], n, if (n == 0) 0.0 else p[1] / n, if (n == 0) 0.0 else p[3] / n,
                    if (n == 0) 0.0 else p[2] / n, i == rg.current)
            }
            val regime = RegimeView(rg.name(), rg.duration, rows, rg.transitions.copyOf(), rg.history.map { it.copyOf() })
            val seqs = ArrayList<SeqView>()
            for (ax in listOf(Axis.BS, Axis.OE, Axis.COMB)) {
                val labels = (0 until b.alphabet.k(ax)).map {
                    when (ax) {
                        Axis.BS -> BigSmall.fromIndex(it).tr; Axis.OE -> OddEven.fromIndex(it).tr
                        else -> CombinedSide.fromIndex(it).display
                    }
                }
                seqs += SeqView(ax.name, labels, b.mem.counts(ax).copyOf(), b.mem.transition(ax).copyOf(), b.mem.sym(ax, 1), b.mem.run(ax))
            }
            return V13Insights(b.count, stats, models, div, cal, regime, seqs, b.guard.checks, b.guard.violations, b.guard.recent(), tau)
        }
    }
}
