package fan.superai.v13

import fan.superai.engine.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** v1.3 çekirdek testleri — saf JVM. Doğruluk iddia edilmez (gerçek veri neredeyse rastgele); yalnızca davranış sözleşmeleri. */
class V13CoreTest {
    private val cfg = EngineConfig()

    private fun records(n: Int, seed: Int = 1, from: Int = 0): List<FanRecord> {
        val r = Random(seed)
        return List(n) { FanRecord.of((from + it + 1).toLong(), 1_700_000_000L + (from + it) * 20L, 1 + r.nextInt(4)) }
    }

    private fun run(recs: List<FanRecord>, brain: FanBrain = FanBrain(), startAt: Int = 0, ctx: Boolean = false) =
        ReplayEngine.run(recs, brain, cfg, null, startAt = startAt, keepContext = ctx)

    private fun sum(a: DoubleArray) = a.sum()

    @Test fun numberAlwaysInRangeAndSingleDistribution() {
        val res = run(records(120))
        assertEquals(120, res.steps.size)
        for (s in res.steps) {
            assertTrue(s.predNumber in 1..4)
            assertEquals(4, s.numberProbs.size)
            assertEquals(1.0, sum(s.numberProbs), 1e-6)
            assertEquals(1.0, sum(s.joint), 1e-6)
            assertEquals(1.0, sum(s.bsProbs), 1e-6)
            assertEquals(1.0, sum(s.oeProbs), 1e-6)
            assertEquals(s.joint[0] + s.joint[1], s.bsProbs[0], 1e-6)
            assertEquals(s.joint[0] + s.joint[2], s.oeProbs[0], 1e-6)
        }
    }

    @Test fun sideHasTwoComponentsAndIsNotDerivedFromNumber() {
        val brain = FanBrain()
        run(records(60), brain)
        val p = brain.predict(emptyList(), 1_800_000_000L)
        assertNotNull(p.bigSmall); assertNotNull(p.oddEven)
        assertEquals(CombinedSide.of(p.bigSmall, p.oddEven), p.side)
        // yan modeller rakam girdisi olmadan da çalışır (rakamdan türetilmez)
        assertTrue(p.contributions.any { it.axis == Axis.BS })
        assertTrue(p.contributions.any { it.axis == Axis.OE })
        assertTrue(p.explanation.sideDetail.isNotEmpty())
    }

    @Test fun predictionLockIsStableAndSurvivesSnapshotRestore() {
        val brain = FanBrain()
        run(records(80), brain)
        val a = brain.predict(emptyList(), 1_800_000_000L)
        val b = brain.predict(emptyList(), 1_800_000_999L)
        assertTrue("kilit açıkken aynı tahmin dönmeli", a === b)
        val copy = FanBrain()
        copy.restore(brain.snapshot())
        val c = copy.open!!.prediction
        assertEquals(a.predictionLockId, c.predictionLockId)
        assertEquals(a.lockHash, c.lockHash)
        assertEquals(a.number, c.number)
        assertEquals(a.side, c.side)
    }

    @Test fun leakageFuturePrefixInvariance() {
        val n = 90
        val base = records(130, seed = 5)
        val altered = base.take(n) + records(40, seed = 99, from = n)
        val r1 = run(base); val r2 = run(altered)
        assertEquals(0L, r1.leakViolations)
        for (i in 0 until n) {
            assertEquals("adım $i gelecekten etkilendi", r1.steps[i].lockId, r2.steps[i].lockId)
        }
        // Gelecek gerçekten farklıysa sonrası da farklılaşabilir (testin anlamlı olması için)
        assertTrue((n until 130).any { r1.steps[it].lockId != r2.steps[it].lockId })
    }

    @Test fun replayOrderAndMetadataAreChronological() {
        val recs = records(100)
        val res = run(recs)
        assertTrue(res.orderOk)
        assertEquals(0L, res.leakViolations)
        assertTrue(res.leakChecks > 0)
        for ((i, s) in res.steps.withIndex()) {
            assertEquals(i, s.sequence)
            assertEquals(if (i == 0) 0L else recs[i - 1].recordId, s.lastKnownRecordId)
            assertTrue(s.predictionTimestamp <= s.timestamp)
        }
        val shuffled = recs.take(10).reversed() + recs.drop(10)
        assertFalse(run(shuffled).orderOk)
    }

    @Test fun replayIsReproducible() {
        val recs = records(110, seed = 3)
        val a = run(recs); val b = run(recs)
        assertEquals(a.steps.map { it.lockId }, b.steps.map { it.lockId })
        assertEquals(a.steps.map { it.ev.numberHit }, b.steps.map { it.ev.numberHit })
    }

    @Test fun incrementalMatchesFullReplay() {
        val recs = records(120, seed = 11)
        val full = run(recs)
        val brain = FanBrain()
        run(recs.take(70), brain)
        val tail = run(recs, brain, startAt = 70)
        assertEquals(50, tail.steps.size)
        for (i in 0 until 50) assertEquals(full.steps[70 + i].lockId, tail.steps[i].lockId)
    }

    @Test fun persistedStateContinuesIdentically() {
        val recs = records(130, seed = 21)
        val a = FanBrain(); run(recs.take(80), a)
        val parts = a.exportParts()
        val b = FanBrain(); b.importParts(parts.associate { it.full to it.bytes })
        assertEquals(80, b.count)
        // a'nın kilidi kayıttan sonra aynı yeniden üretilmeli
        a.predict(emptyList(), 1_800_000_000L)
        val b2 = FanBrain(); b2.importParts(a.exportParts().associate { it.full to it.bytes })
        assertEquals(a.open!!.prediction.predictionLockId, b2.open!!.prediction.predictionLockId)
        val ra = run(recs, a, startAt = 80); val rb = run(recs, b2, startAt = 80)
        assertEquals(ra.steps.map { it.lockId }, rb.steps.map { it.lockId })
    }

    @Test fun undoRestoresSameLockedPrediction() {
        val recs = records(60, seed = 8)
        val brain = FanBrain()
        brain.recordUndo = true
        var lockAt59: String? = null
        for ((i, r) in recs.withIndex()) {
            val p = brain.predict(emptyList(), r.timestamp - 1)
            if (i == 59) lockAt59 = p.predictionLockId
            brain.onActual(r)
        }
        assertTrue(brain.undoTo(59))
        assertEquals(59, brain.count)
        assertEquals(lockAt59, brain.open!!.prediction.predictionLockId)
    }

    @Test fun counterfactualDoesNotChangeLiveState() {
        val brain = FanBrain()
        val res = run(records(100), brain, ctx = true)
        val ctx = res.steps.last().ctx!!
        val before = brain.snapshot()
        val out = Counterfactual.runAll(brain.alphabet, ctx)
        assertTrue(out.size >= 4)
        assertEquals("Normal ensemble", out[0].scenario)
        assertFalse(out[0].numberChanged)
        assertTrue(before.contentEquals(brain.snapshot()))
        assertTrue(out.all { it.number in 1..4 })
    }

    @Test fun bootstrapAndPermutationAreSane() {
        val res = run(records(200, seed = 2))
        val sum = ReplayStats.summarize(res, skip = 50, bootstrap = 200, permutations = 200)
        assertEquals(150, sum.n)
        for (m in sum.metrics) { assertTrue(m.lo <= m.hi); assertTrue(m.value >= m.lo - 0.1 && m.value <= m.hi + 0.1) }
        for (p in sum.permutations) assertTrue(p.pValue > 0.0 && p.pValue <= 1.0)
        assertEquals(4, sum.permutations.size)
        assertEquals(0.25, sum.metrics.first { it.name == "Rakam Top-1" }.baseline, 1e-9)
        assertEquals(0.5, sum.metrics.first { it.name == "Büyük/Küçük" }.baseline, 1e-9)
    }

    @Test fun regimesAreNamedAndPerformanceTracked() {
        val brain = FanBrain()
        run(records(160), brain)
        assertTrue(brain.regime.name() in V13.REGIME_NAMES)
        assertEquals(160, brain.stats.total)
        assertTrue(brain.ens[Axis.NUMBER.ordinal].rows().isNotEmpty())
        assertTrue(brain.guard.violations == 0L)
    }

    // ------------------------------------------------------------------ v1.4: tek/çift (pairMode)

    @Test fun pairModeControlsHowManyCandidatesAreShown() {
        val brain = FanBrain()
        run(records(120, seed = 31), brain)
        // Açık kilit varken aynı tahmin döner: her mod için ayrı beyin.
        val single = FanBrain(); run(records(120, seed = 31), single)
        val p2 = single.predict(emptyList(), 1_800_000_000L, 2)
        assertNull("her zaman tek", p2.numberSecondary)
        assertEquals("${p2.number}", p2.numberLabel)
        val pair = FanBrain(); run(records(120, seed = 31), pair)
        val p1 = pair.predict(emptyList(), 1_800_000_000L, 1)
        assertNotNull("her zaman çift", p1.numberSecondary)
        assertEquals("${p1.number}/${p1.numberSecondary}", p1.numberLabel)
        assertNotEquals(p1.number, p1.numberSecondary)
    }

    @Test fun automaticPairModeUsesConformalScores() {
        val brain = FanBrain()
        run(records(140, seed = 12), brain)
        assertTrue("değerlendirmeler konformal skor üretmeli", brain.stats.pairScores.count >= 30)
        val auto = FanBrain(); run(records(140, seed = 12), auto)
        val p = auto.predict(emptyList(), 1_800_000_000L, 0)
        // Otomatik karar, tek/çift seçeneklerinden biri olmalı ve skorlarla tutarlı olmalı.
        val single = p.numberSecondary == null
        val scores = auto.stats.pairScores.values().sorted()
        val q = scores[(scores.size * 0.5).toInt().coerceAtMost(scores.size - 1)]
        val conformalSingle = (0..3).count { 1 - p.numberDistribution.probabilities[it] <= q } <= 1
        assertEquals("otomatik karar konformal kurala uymalı", conformalSingle, single)
    }

    @Test fun groupNumberMixesFeedTheOverlayRows() {
        val brain = FanBrain()
        val f = brain.predict(listOf(
            ModelOutput("k_a", "Kotlin · a", Group.KOTLIN, number = doubleArrayOf(.6, .2, .1, .1)),
            ModelOutput("p_a", "Python · a", Group.PYTHON, number = doubleArrayOf(.1, .1, .2, .6))
        ), 1_800_000_000L, 2)
        assertEquals(4, f.kotlinNumberProbs!!.size)
        assertEquals(1.0, f.kotlinNumberProbs!!.sum(), 1e-9)
        assertEquals(1.0, f.pythonNumberProbs!!.sum(), 1e-9)
        assertEquals(0, fan.superai.v13.Mx.argmax(f.kotlinNumberProbs!!))
        assertEquals(3, fan.superai.v13.Mx.argmax(f.pythonNumberProbs!!))
        // Python modeli yoksa satır null kalır (overlay "--" gösterir), Kotlin'e kopyalanmaz.
        val kOnly = FanBrain().predict(listOf(
            ModelOutput("k_a", "Kotlin · a", Group.KOTLIN, number = doubleArrayOf(.6, .2, .1, .1))
        ), 1_800_000_000L, 2)
        assertNotNull(kOnly.kotlinNumberProbs)
        assertNull(kOnly.pythonNumberProbs)
    }

    @Test fun explanationIsBuiltFromRealComputation() {
        val brain = FanBrain()
        run(records(100), brain)
        val p = brain.predict(emptyList(), 1_800_000_000L)
        val e = p.explanation
        assertTrue(e.sideDetail.any { it.contains("Birleşik") })
        assertTrue(e.numberPositive.size + e.numberNegative.size > 0)
        // Aynı kilit geri yüklenince aynı metin
        val copy = FanBrain(); copy.restore(brain.snapshot())
        assertEquals(e.toText(), copy.open!!.prediction.explanation.toText())
    }
}
