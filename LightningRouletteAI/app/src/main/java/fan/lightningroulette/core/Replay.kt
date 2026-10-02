package fan.lightningroulette.core

import kotlin.math.max
import kotlin.math.min

/** Bir walk-forward adımının özeti (LAB analizlerinin ham verisi). */
class StepRec(
    val i: Int, val actual: Int,
    val centers: IntArray, val ks: IntArray, val pCand: DoubleArray,
    val coverage: Int, val p5: Double,
    val exact: Boolean, val cand: Boolean, val candRank: Int, val neighbor: Boolean,
    val sectorHit: Boolean, val regionHit: Boolean, val topSectorBase: Double, val topRegionBase: Double,
    val tableHit: BooleanArray, val tableCls: IntArray, val tableP: DoubleArray,
    val pActual: Double, val ll: Double,
    val memberPA: DoubleArray, val memberTop5Hit: BooleanArray,
    val pyPA: DoubleArray?, val pyTop5Hit: BooleanArray?,
    val jsK: Double,          // Kotlin üyeleri arasındaki ortalama ikili Jensen–Shannon ayrışması
    val agreeKP: Int,         // Kotlin ve Python top-1 aynı mı? 1/0, Python yoksa −1
    val kHit: Boolean = false, // Kotlin meclisi (Hedge karışımı) top-5 isabeti
    val pHit: Int = -1         // Python meclisi top-5 isabeti 1/0, Python yoksa −1
)

class Gate(val name: String, val ok: Boolean, val detail: String)

/** Walk-forward kaldığı yerden sürdürmek için anlık görüntü (LAB checkpoint/resume). */
class Resume(val next: Int, val brainState: String, val steps: List<StepRec>, val hash: Long)

class ReplayResult(val steps: List<StepRec>, val hash: Long, val gates: List<Gate>, val brain: Brain, val processed: Int, val total: Int, val cancelled: Boolean) {
    val leakFree: Boolean get() = gates.all { it.ok }
}

fun top5Hit(p: DoubleArray, actual: Int): Boolean {
    val pa = p[actual]
    var greater = 0
    for (x in p) if (x > pa) greater++
    return greater < 5
}

/**
 * Walk-forward replay: her adımda geçmiş fiziksel olarak kesilir (PAST | CUT | FUTURE);
 * TRAIN → PREDICT → LOCK → REVEAL → EVALUATE → UPDATE. Rastgele shuffle yoktur.
 */
class ReplayEngine(private val cfg: BrainConfig) {
    fun run(
        values: IntArray, ts: LongArray, learnFrom: Int, recordFrom: Int, to: Int = values.size,
        py: ((Int) -> PyOut?)? = null, cancel: (() -> Boolean)? = null, progress: ((Int, Int) -> Unit)? = null,
        brain: Brain = Brain(cfg), resume: Resume? = null, checkpointEvery: Int = 0, onCheckpoint: ((Resume) -> Unit)? = null
    ): ReplayResult {
        require(values.size == ts.size) { "values/ts boyutları farklı" }
        for (i in 1 until to) if (ts[i - 1] > ts[i]) throw LrError(Codes.SEQ, "kayıt sırası bozuk (#$i): zaman geriye gidiyor")
        val steps = ArrayList<StepRec>()
        var hash = Stats.FNV0
        var cutOk = true; var tsOk = true; var cancelled = false
        val start0 = max(learnFrom, cfg.minSample)
        val total = max(0, to - start0); var done = 0
        var start = start0
        if (resume != null && brain.restoreFull(resume.brainState)) {
            start = max(start0, resume.next); steps.addAll(resume.steps); hash = resume.hash; done = start - start0
        }
        for (i in start until to) {
            if (cancel?.invoke() == true) { cancelled = true; break }
            val past = Brain.cut(values, i)                       // CUT: gelecek fiziksel olarak yok
            if (past.size != min(i, Brain.LOOK)) cutOk = false
            if (ts[i - 1] > ts[i]) { tsOk = false; throw LrError(Codes.LEAK, "predictionTimestamp > actualTimestamp (i=$i)") }
            val pyOut = py?.invoke(i)
            val pred = brain.predict(past, pyOut, if (pyOut != null) "OK" else "OFF")  // PREDICT → LOCK
            val actual = values[i]                                                      // REVEAL
            val ev = Evaluator.evaluate(pred, actual, cfg.sectors)                      // EVALUATE
            if (i >= recordFrom) {
                val rec = record(i, pred, ev)
                steps.add(rec)
                hash = Stats.fnv(hash, actual.toLong())
                for (c in rec.centers) hash = Stats.fnv(hash, c.toLong())
                hash = Stats.fnv(hash, (pred.pFinal[actual] * 1_000_000.0).toLong())
            }
            brain.learn(pred, past, actual)                                             // UPDATE
            done++
            if (done % 50 == 0) progress?.invoke(done, total)
            if (checkpointEvery > 0 && done % checkpointEvery == 0 && onCheckpoint != null) onCheckpoint(Resume(i + 1, brain.fullStateJson(), ArrayList(steps), hash))
        }
        progress?.invoke(done, total)
        val gates = listOf(
            Gate("Kayıt sırası (eski → yeni)", true, "$done / $total"),
            Gate("lastKnownRecordId (kesim boyu)", cutOk, if (cutOk) "ardışık" else "tutarsız"),
            Gate("predictionTimestamp ≤ actualTimestamp", tsOk, "$done / $total"),
            Gate("Test setiyle model seçimi", true, "yok"),
            Gate("Rastgele shuffle", true, "kullanılmadı")
        )
        return ReplayResult(steps, hash, gates, brain, done, total, cancelled)
    }

    private fun argmax(p: DoubleArray): Int { var b = 0; for (i in 1 until p.size) if (p[i] > p[b]) b = i; return b }
    private fun meanJs(ps: List<DoubleArray>): Double {
        if (ps.size < 2) return 0.0
        var s = 0.0; var c = 0
        for (a in 0 until ps.size) for (b in a + 1 until ps.size) { s += Stats.jsDiv(ps[a], ps[b]); c++ }
        return s / c
    }

    private fun record(i: Int, pred: PredictionCore, ev: Eval): StepRec {
        val cs = pred.candidates; val a = ev.actual
        val top = cs[0]
        return StepRec(
            i, a, IntArray(cs.size) { cs[it].n }, IntArray(cs.size) { cs[it].k }, DoubleArray(cs.size) { cs[it].p },
            ev.coverage, pred.p5, ev.exact, ev.candidate, ev.candRank, ev.neighbor, ev.sector, ev.region,
            cfg.sectors.baseline(cfg.sectors.of[top.n]), Regions.size(Regions.of[top.n]).toDouble() / Wheel.N,
            ev.tableHits, IntArray(5) { pred.table[it].cls }, DoubleArray(5) { pred.table[it].p },
            ev.pActual, ev.logLoss,
            DoubleArray(pred.memberProbs.size) { pred.memberProbs[it][a] }, BooleanArray(pred.memberProbs.size) { top5Hit(pred.memberProbs[it], a) },
            if (pred.pyProbs.isEmpty()) null else DoubleArray(pred.pyProbs.size) { pred.pyProbs[it][a] },
            if (pred.pyProbs.isEmpty()) null else BooleanArray(pred.pyProbs.size) { top5Hit(pred.pyProbs[it], a) },
            meanJs(pred.memberProbs),
            if (pred.pPython == null) -1 else (if (argmax(pred.pKotlin) == argmax(pred.pPython)) 1 else 0),
            top5Hit(pred.pKotlin, a), if (pred.pPython == null) -1 else (if (top5Hit(pred.pPython, a)) 1 else 0)
        )
    }
}

/** StepRec ↔ JSON (checkpoint ve LAB sonuç önbelleği için). */
object StepIO {
    private fun bits(a: BooleanArray): Int { var m = 0; for (i in a.indices) if (a[i]) m = m or (1 shl i); return m }
    private fun unbits(m: Int, n: Int) = BooleanArray(n) { (m shr it) and 1 == 1 }

    fun toList(s: StepRec): List<Any?> = listOf(
        s.i, s.actual, s.centers.toList(), s.ks.toList(), s.pCand.toList(), s.coverage, s.p5, if (s.exact) 1 else 0, if (s.cand) 1 else 0, s.candRank,
        if (s.neighbor) 1 else 0, if (s.sectorHit) 1 else 0, if (s.regionHit) 1 else 0, s.topSectorBase, s.topRegionBase, bits(s.tableHit), s.tableCls.toList(),
        s.tableP.toList(), s.pActual, s.ll, s.memberPA.toList(), s.memberTop5Hit.size, bits(s.memberTop5Hit), s.pyPA?.toList(), s.pyTop5Hit?.size ?: 0,
        s.pyTop5Hit?.let { bits(it) } ?: 0, s.jsK, s.agreeKP, if (s.kHit) 1 else 0, s.pHit
    )

    fun fromList(l: List<Any?>): StepRec {
        val mn = l[21].jint(); val pn = l[24].jint()
        return StepRec(
            l[0].jint(), l[1].jint(), l[2].jints(), l[3].jints(), l[4].jdoubles(), l[5].jint(), l[6].jnum(),
            l[7].jint() == 1, l[8].jint() == 1, l[9].jint(), l[10].jint() == 1, l[11].jint() == 1, l[12].jint() == 1, l[13].jnum(), l[14].jnum(),
            unbits(l[15].jint(), 5), l[16].jints(), l[17].jdoubles(), l[18].jnum(), l[19].jnum(),
            l[20].jdoubles(), unbits(l[22].jint(), mn),
            if (l[23] == null) null else l[23].jdoubles(), if (pn == 0) null else unbits(l[25].jint(), pn), l[26].jnum(), l[27].jint(),
            l.getOrNull(28).jint() == 1, l.getOrNull(29).jint(-1)
        )
    }

    fun encode(r: Resume): String = Json.stringify(mapOf("next" to r.next, "brain" to r.brainState, "hash" to r.hash.toString(), "steps" to r.steps.map { toList(it) }))
    fun decode(s: String): Resume? = try {
        val m = Json.parse(s).jmap()
        Resume(m["next"].jint(), m["brain"].jstr(), m["steps"].jlist().map { fromList(it.jlist()) }, m["hash"].jstr().toLong())
    } catch (e: Exception) { null }
}
