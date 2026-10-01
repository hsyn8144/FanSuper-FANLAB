package fan.superai.v13

import fan.superai.engine.Council
import fan.superai.engine.EngineConfig
import fan.superai.engine.FanEngine
import fan.superai.engine.History
import kotlin.math.ln
import kotlin.random.Random

/** Python tarafının bir adım için ürettiği ortak biçimli çıktılar. */
class PySideMember(val id: String, val name: String, val bs: DoubleArray, val oe: DoubleArray, val comb: DoubleArray)

class PythonStepData(
    val numberIds: List<String>, val numberNames: List<String>, val number: List<DoubleArray>,
    val side: List<PySideMember>
) {
    fun outputs(): List<ModelOutput> {
        val out = ArrayList<ModelOutput>()
        for (i in numberIds.indices) out += ModelOutput("py_${numberIds[i]}", "Python · ${numberNames[i]}", Group.PYTHON, number = number[i])
        for (m in side) out += ModelOutput(m.id, "Python yan · ${m.name}", Group.PYTHON, bs = m.bs, oe = m.oe, comb = m.comb)
        return out
    }
}

/** Replay için Python verisi: kayıt başına bir adım (bilinmiyorsa null). */
class PythonReplayData(val steps: List<PythonStepData?>)

class ReplayStep(
    val sequence: Int, val recordId: Long, val timestamp: Long, val predictionTimestamp: Long, val lastKnownRecordId: Long,
    val actualNumber: Int, val actual: IntArray, val predNumber: Int, val predSide: CombinedSide,
    val numberProbs: DoubleArray, val bsProbs: DoubleArray, val oeProbs: DoubleArray, val joint: DoubleArray,
    val regime: Int, val ev: EvaluationResult, val lockId: String, val ctx: PredictionContext?
)

class ReplayResult(
    val steps: List<ReplayStep>, val startedAt: Long, val finishedAt: Long, val processed: Int, val total: Int,
    val errors: List<String>, val leakChecks: Long, val leakViolations: Long, val cancelled: Boolean,
    val orderOk: Boolean, val includesPython: Boolean, val alphabet: Alphabet
)

interface ReplaySink {
    fun onStep(step: ReplayStep, pred: FinalPrediction, brain: FanBrain)
    fun onFinish(result: ReplayResult, brain: FanBrain)
}

/**
 * Tarihsel replay. Her adımda: yalnızca geçmiş → tahmin → LOCK → gerçek sonuç açılır → değerlendirme → model güncellemesi.
 * Kotlin meclisi KENDİ örneğiyle ve kayıtların yalnızca önek kopyalarıyla beslenir; yani gelecek veri fiziksel
 * olarak erişilebilir değildir. Sandbox brain canlı durumdan tamamen ayrıdır.
 */
object ReplayEngine {
    fun run(
        records: List<FanRecord>, brain: FanBrain, engineCfg: EngineConfig, python: PythonReplayData?,
        startAt: Int = 0, nowMs: () -> Long = { System.currentTimeMillis() },
        progress: ((Int, Int) -> Unit)? = null, cancel: (() -> Boolean)? = null,
        sink: ReplaySink? = null, keepContext: Boolean = false
    ): ReplayResult {
        val t0 = nowMs()
        val total = records.size
        val errors = ArrayList<String>()
        var checks = 0L; var violations = 0L
        fun chk(ok: Boolean, msg: () -> String) {
            checks++
            if (!ok) { violations++; if (errors.size < 30) errors += "${ErrorCodes.LEAK_FUTURE} ${msg()}"; FanLog.event(FanLog.LEAKAGE_CHECK, msg()) }
        }
        // Sıra doğrulaması: kronolojik olmalı
        var orderOk = true
        for (i in 1 until total) if (records[i].recordId <= records[i - 1].recordId || records[i].timestamp < records[i - 1].timestamp) {
            orderOk = false
            if (errors.size < 30) errors += "${ErrorCodes.SEQUENCE} kayıt sırası bozuk: #${records[i - 1].recordId} → #${records[i].recordId}"
            break
        }
        FanLog.event(FanLog.REPLAY_STARTED, "$total kayıt, başlangıç=$startAt, python=${python != null}")
        val alphabet = brain.alphabet
        val vals = IntArray(total) { alphabet.idx(records[it].number) }
        val times = LongArray(total) { records[it].timestamp }
        val council = Council("Kotlin", FanEngine.kotlinMembers(), engineCfg)
        val steps = ArrayList<ReplayStep>()
        val prevQuiet = FanLog.quiet; val prevUndo = brain.recordUndo
        FanLog.quiet = true; brain.recordUndo = false
        var processed = startAt; var cancelled = false
        try {
            for (i in 0 until total) {
                if (cancel?.invoke() == true) { cancelled = true; break }
                // Gelecek fiziksel olarak yok: yalnızca [0, i) kopyalanır
                val hist = History(vals.copyOf(i), times.copyOf(i), i)
                council.predict(hist)
                if (i >= startAt) {
                    chk(hist.values.size == i) { "geçmiş boyutu ${hist.values.size} ≠ $i (adım $i)" }
                    chk(brain.count == i) { "brain bilinen kayıt ${brain.count} ≠ $i" }
                    val inputs = ArrayList<ModelOutput>()
                    for (v in council.memberViews()) inputs += ModelOutput("k_${v.id}", "Kotlin · ${v.name}", Group.KOTLIN, number = v.pred)
                    python?.steps?.getOrNull(i)?.let { inputs += it.outputs() }
                    val last = records.getOrNull(i - 1)
                    val pred = brain.predict(inputs, last?.timestamp ?: records[i].timestamp)
                    chk(pred.predictionSequence == i) { "tahmin sırası ${pred.predictionSequence} ≠ $i" }
                    chk(pred.lastKnownRecordId == (last?.recordId ?: 0L)) { "lastKnownRecordId ${pred.lastKnownRecordId} (adım $i)" }
                    chk(pred.predictionTimestamp <= records[i].timestamp) { "tahmin zamanı gerçek sonuçtan sonra (adım $i)" }
                    val ctx = brain.currentContext()
                    val ev = brain.onActual(records[i])
                    val step = ReplayStep(i, records[i].recordId, records[i].timestamp, pred.predictionTimestamp, pred.lastKnownRecordId,
                        records[i].number, IntArray(4) { alphabet.actual(Axis.values()[it], records[i].number) },
                        pred.number, pred.side, pred.numberDistribution.probabilities, pred.sideDistribution.bigSmall,
                        pred.sideDistribution.oddEven, pred.sideDistribution.combined,
                        V13.REGIME_NAMES.indexOf(pred.regime), ev, pred.predictionLockId, if (keepContext) ctx else null)
                    steps += step
                    sink?.onStep(step, pred, brain)
                    processed = i + 1
                }
                council.update(History(vals.copyOf(i + 1), times.copyOf(i + 1), i + 1))
                if (i % 25 == 0) progress?.invoke(i + 1, total)
            }
        } finally { FanLog.quiet = prevQuiet; brain.recordUndo = prevUndo }
        progress?.invoke(processed, total)
        val result = ReplayResult(steps, t0, nowMs(), processed, total, errors, checks + brain.guard.checks, violations + brain.guard.violations,
            cancelled, orderOk, python != null, alphabet)
        FanLog.event(FanLog.LEAKAGE_CHECK, "replay denetimi: ${result.leakChecks} kontrol, ${result.leakViolations} ihlal")
        FanLog.event(FanLog.REPLAY_COMPLETED, "işlenen=$processed/$total, tahmin=${steps.size}, hata=${errors.size}")
        sink?.onFinish(result, brain)
        return result
    }
}

// ---------------------------------------------------------------------------------------------------
class MetricCI(val name: String, val value: Double, val lo: Double, val hi: Double, val baseline: Double, val higherIsBetter: Boolean)
class PermTest(val name: String, val observed: Double, val pValue: Double, val percentile: Double, val baseline: Double)

class ReplaySummary(
    val n: Int, val skipped: Int, val metrics: List<MetricCI>, val permutations: List<PermTest>,
    val causalBaseline: Map<String, Double>
)

/** Bootstrap güven aralıkları, permutation testleri ve baseline'lar — replay sonuçları üzerinden. */
object ReplayStats {
    private fun brierOf(p: DoubleArray, a: Int): Double = Mx.brier(p, a)

    fun summarize(r: ReplayResult, skip: Int = 50, bootstrap: Int = 1000, permutations: Int = 1000, seed: Int = 8144): ReplaySummary {
        val steps = r.steps.drop(skip.coerceAtMost(maxOf(0, r.steps.size - 1)).coerceAtLeast(0))
        val n = steps.size
        val size = r.alphabet.size
        if (n == 0) return ReplaySummary(0, skip, emptyList(), emptyList(), emptyMap())
        // Adım başına metrikler
        val names = listOf("Rakam Top-1", "Rakam Top-2", "Rakam LogLoss", "Rakam Brier",
            "Büyük/Küçük", "Tek/Çift", "Birleşik yan", "Yan LogLoss", "Yan Brier")
        val baselines = doubleArrayOf(1.0 / size, minOf(1.0, 2.0 / size), ln(size.toDouble()), 1.0 - 1.0 / size,
            0.5, 0.5, 0.25, ln(4.0), 0.75)
        val higher = booleanArrayOf(true, true, false, false, true, true, true, false, false)
        val m = names.size
        val data = Array(m) { DoubleArray(n) }
        for ((i, s) in steps.withIndex()) {
            val a = s.actual
            val o2 = Mx.top2(s.numberProbs)
            data[0][i] = if (Mx.argmax(s.numberProbs) == a[0]) 1.0 else 0.0
            data[1][i] = if (o2[0] == a[0] || o2[1] == a[0]) 1.0 else 0.0
            data[2][i] = Mx.logLoss(s.numberProbs, a[0])
            data[3][i] = brierOf(s.numberProbs, a[0])
            data[4][i] = if (Mx.argmax(s.bsProbs) == a[1]) 1.0 else 0.0
            data[5][i] = if (Mx.argmax(s.oeProbs) == a[2]) 1.0 else 0.0
            data[6][i] = if (s.predSide.index == a[3]) 1.0 else 0.0
            data[7][i] = Mx.logLoss(s.joint, a[3])
            data[8][i] = brierOf(s.joint, a[3])
        }
        val rnd = Random(seed)
        val boots = Array(m) { DoubleArray(bootstrap) }
        val sums = DoubleArray(m)
        repeat(bootstrap) { b ->
            java.util.Arrays.fill(sums, 0.0)
            repeat(n) { val j = rnd.nextInt(n); for (q in 0 until m) sums[q] += data[q][j] }
            for (q in 0 until m) boots[q][b] = sums[q] / n
        }
        val metrics = names.indices.map { q ->
            boots[q].sort()
            MetricCI(names[q], data[q].average(), boots[q][(bootstrap * 0.025).toInt().coerceIn(0, bootstrap - 1)],
                boots[q][(bootstrap * 0.975).toInt().coerceIn(0, bootstrap - 1)], baselines[q], higher[q])
        }
        // Permutation: tahminler sabit, gerçek sonuç sırası karıştırılır
        val perm = ArrayList<PermTest>()
        val targets = listOf(Triple("Rakam", 0, 0), Triple("Büyük/Küçük", 1, 4), Triple("Tek/Çift", 2, 5), Triple("Birleşik yan", 3, 6))
        for ((nm, ai, mi) in targets) {
            val actuals = IntArray(n) { steps[it].actual[ai] }
            val preds = IntArray(n) { i ->
                val s = steps[i]
                when (ai) { 0 -> Mx.argmax(s.numberProbs); 1 -> Mx.argmax(s.bsProbs); 2 -> Mx.argmax(s.oeProbs); else -> s.predSide.index }
            }
            val observed = data[mi].average()
            var ge = 0
            repeat(permutations) {
                for (i in n - 1 downTo 1) { val j = rnd.nextInt(i + 1); val t = actuals[i]; actuals[i] = actuals[j]; actuals[j] = t }
                var hit = 0
                for (i in 0 until n) if (preds[i] == actuals[i]) hit++
                if (hit.toDouble() / n >= observed - 1e-12) ge++
            }
            val p = (ge + 1.0) / (permutations + 1.0)
            perm += PermTest(nm, observed, p, 1.0 - p, baselines[mi])
        }
        // Nedensel çoğunluk baseline'ı (her adımda o ana kadarki en sık sınıf)
        val causal = LinkedHashMap<String, Double>()
        val all = r.steps
        for ((nm, ai) in listOf("Rakam" to 0, "Büyük/Küçük" to 1, "Tek/Çift" to 2, "Birleşik yan" to 3)) {
            val counts = IntArray(r.alphabet.k(Axis.values()[ai]))
            var hit = 0; var cnt = 0
            for ((i, s) in all.withIndex()) {
                if (i >= skip) { cnt++; if (Mx.argmaxInt(counts) == s.actual[ai]) hit++ }
                counts[s.actual[ai]]++
            }
            causal[nm] = if (cnt == 0) 0.0 else hit.toDouble() / cnt
        }
        return ReplaySummary(n, skip, metrics, perm, causal)
    }
}

// ---------------------------------------------------------------------------------------------------
class Scenario(val name: String, val groupFactor: Map<Group, Double> = emptyMap(),
               val modelFactor: Map<String, Double> = emptyMap(), val equal: Boolean = false)

class CfResult(val scenario: String, val number: Int, val side: CombinedSide, val numberProbs: DoubleArray,
               val joint: DoubleArray, val numberChanged: Boolean, val sideChanged: Boolean)

/** Karşı-olgusal analiz: yalnızca simülasyon; canlı state'e ASLA yazmaz. */
object Counterfactual {
    fun scenarios(ctx: PredictionContext): List<Scenario> {
        val out = ArrayList<Scenario>()
        out += Scenario("Normal ensemble")
        out += Scenario("Python yok", mapOf(Group.PYTHON to 0.0))
        out += Scenario("Kotlin yok", mapOf(Group.KOTLIN to 0.0))
        out += Scenario("Eşit ağırlık", equal = true)
        for (ax in listOf(Axis.NUMBER, Axis.BS, Axis.COMB)) {
            val a = ctx.axis(ax)
            if (a.size == 0) continue
            val top = (0 until a.size).maxByOrNull { a.weights[it] } ?: continue
            val nm = a.names[top]
            val tag = when (ax) { Axis.NUMBER -> "Rakam"; Axis.BS -> "Büyük/Küçük"; else -> "Birleşik" }
            out += Scenario("$tag: $nm ağırlığı +%10", modelFactor = mapOf(a.ids[top] to 1.10))
            out += Scenario("$tag: $nm ağırlığı −%10", modelFactor = mapOf(a.ids[top] to 0.90))
        }
        return out
    }

    fun apply(alphabet: Alphabet, ctx: PredictionContext, sc: Scenario): CfResult {
        val fin = Array(4) { ai ->
            val ax = Axis.values()[ai]; val a = ctx.axis(ax); val k = alphabet.k(ax)
            if (a.size == 0) Mx.uniform(k) else {
                val w = DoubleArray(a.size) { i ->
                    if (sc.equal) 1.0 else a.weights[i] * (sc.groupFactor[a.groups[i]] ?: 1.0) * (sc.modelFactor[a.ids[i]] ?: 1.0)
                }
                val s = w.sum()
                if (s <= 0) Mx.uniform(k) else {
                    for (i in w.indices) w[i] /= s
                    Pool.shape(k, Pool.mix(k, a.dists, w), a.tau)
                }
            }
        }
        val d = Decision.decide(fin, ctx.blendProduct)
        val base = if (sc.name == "Normal ensemble") null else baseline(alphabet, ctx)
        val side = CombinedSide.of(BigSmall.fromIndex(d.bs), OddEven.fromIndex(d.oe))
        return CfResult(sc.name, alphabet.number(d.numberIdx), side, fin[0], d.joint,
            base != null && base.number != alphabet.number(d.numberIdx), base != null && base.side != side)
    }

    private class Base(val number: Int, val side: CombinedSide)

    private fun baseline(alphabet: Alphabet, ctx: PredictionContext): Base {
        val fin = Array(4) { ai ->
            val ax = Axis.values()[ai]; val a = ctx.axis(ax); val k = alphabet.k(ax)
            if (a.size == 0) Mx.uniform(k) else Pool.shape(k, Pool.mix(k, a.dists, a.weights), a.tau)
        }
        val d = Decision.decide(fin, ctx.blendProduct)
        return Base(alphabet.number(d.numberIdx), CombinedSide.of(BigSmall.fromIndex(d.bs), OddEven.fromIndex(d.oe)))
    }

    fun runAll(alphabet: Alphabet, ctx: PredictionContext): List<CfResult> = scenarios(ctx).map { apply(alphabet, ctx, it) }

    /** Replay boyunca (bağlamı saklanmış adımlarda) bir senaryonun rakam/yan isabeti. */
    class CfHistory(val scenario: String, val n: Int, val numberAcc: Double, val sideAcc: Double)

    fun overReplay(r: ReplayResult, sc: Scenario, skip: Int = 50): CfHistory {
        var n = 0; var nh = 0; var sh = 0
        for ((i, s) in r.steps.withIndex()) {
            val ctx = s.ctx ?: continue
            if (i < skip) continue
            val res = apply(r.alphabet, ctx, sc)
            n++
            if (res.number == s.actualNumber) nh++
            if (res.side.index == s.actual[3]) sh++
        }
        return CfHistory(sc.name, n, if (n == 0) 0.0 else nh.toDouble() / n, if (n == 0) 0.0 else sh.toDouble() / n)
    }
}
