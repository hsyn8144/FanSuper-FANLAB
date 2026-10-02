package fan.lightningroulette.core

import kotlin.math.max

/** Ana ekranın "son OOS penceresi" özeti: gözlenen oran, tesadüf tabanı, fark ve %95 bootstrap aralığı (Prompt §25, §64). */
class SumRow(val name: String, val obs: Double, val base: Double, val lo: Double, val hi: Double, val n: Int) {
    val delta: Double get() = obs - base
}

class LiveSummary(val rows: List<SumRow>, val cls: String, val flags: List<String>, val n: Int)

object LiveSummaryCalc {
    const val WINDOW = 300

    /** evals: EN ESKİ → EN YENİ değerlendirilmiş canlı tahminler. Exact ve Candidate kesinlikle ayrı satırlardır. */
    fun compute(all: List<Eval>, labFlags: List<String> = emptyList()): LiveSummary {
        val ev = all.takeLast(WINDOW); val n = ev.size
        if (n == 0) return LiveSummary(emptyList(), "S", listOf("Düşük sample."), 0)
        fun row(name: String, hit: (Eval) -> Double, base: (Eval) -> Double, seed: Long): SumRow {
            val x = DoubleArray(n) { hit(ev[it]) }; val b = DoubleArray(n) { base(ev[it]) }
            val ci = Stats.bootstrapCI(x, 600, seed)
            return SumRow(name, Stats.mean(x) * 100, Stats.mean(b) * 100, ci[0] * 100, ci[1] * 100, n)
        }
        val tc = TableCats.IDS.size
        val rows = listOf(
            row("Exact", { if (it.exact) 1.0 else 0.0 }, { 1.0 / Wheel.N }, 1),
            row("Candidate", { if (it.candidate) 1.0 else 0.0 }, { it.candCount.toDouble() / Wheel.N }, 2),
            row("Neighbor", { if (it.neighbor) 1.0 else 0.0 }, { it.coverage.toDouble() / Wheel.N }, 3),
            row("Sector", { if (it.sector) 1.0 else 0.0 }, { it.sectorBase }, 4),
            row("Region", { if (it.region) 1.0 else 0.0 }, { it.regionBase }, 5),
            row("Table", { e -> e.tableHits.count { it }.toDouble() / tc }, { (0 until tc).sumOf { q -> TableCats.baseline(q) } / tc }, 6)
        )
        val cand = rows[1]
        val flags = ArrayList<String>()
        if (n < 300) flags.add("Düşük sample.")
        val sig = cand.lo > cand.base
        if (!sig) flags.add("Baseline’dan anlamlı ayrışma yok.")
        if (cand.delta < 0) flags.add("OOS zayıf.")
        for (f in labFlags) if (f !in flags) flags.add(f)
        val cls = if (n < 300) "S" else if (sig) "B" else if (cand.delta < -2.0) "D" else "C"
        return LiveSummary(rows, cls, flags, n)
    }
}
