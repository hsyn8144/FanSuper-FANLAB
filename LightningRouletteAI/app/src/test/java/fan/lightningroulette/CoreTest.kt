package fan.lightningroulette

import fan.lightningroulette.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private fun randomSpins(n: Int, seed: Long): IntArray { val r = java.util.Random(seed); return IntArray(n) { r.nextInt(37) } }
    private fun times(n: Int): LongArray = LongArray(n) { 1_700_000_000L + it * 60L }

    @Test fun wheelOrderIsPermutationAndSpans() {
        assertEquals(37, Wheel.ORDER.toSet().size)
        assertEquals(37, Wheel.POS.toSet().size)
        assertEquals(listOf(21, 2, 25, 17, 34, 6, 27), Wheel.span(17, 3).toList())
        assertEquals(listOf(19, 4, 21, 2, 25), Wheel.span(21, 2).toList())
        assertEquals(listOf(2, 25, 17), Wheel.span(17, 2, Dir.L).toList())
        assertEquals(listOf(17, 34, 6), Wheel.span(17, 2, Dir.R).toList())
        assertEquals(22, Wheel.cw(17, 29)); assertEquals(15, Wheel.ccw(17, 29)); assertEquals(15, Wheel.circ(17, 29))
        assertEquals(0, Wheel.span(0, 0)[0])
    }

    @Test fun sectorsAndRegionsCoverAllPockets() {
        val s = Sectors.DEFAULT
        assertEquals(9, s.count)
        assertEquals(5, s.size(0)); for (i in 1 until 9) assertEquals(4, s.size(i))
        var tot = 0; for (i in 0 until 9) tot += s.size(i); assertEquals(37, tot)
        assertEquals(37, Regions.size(0) + Regions.size(1) + Regions.size(2))
        assertEquals(17, Regions.size(0)); assertEquals(12, Regions.size(1)); assertEquals(8, Regions.size(2))
        assertEquals(1, s.of[21])            // S2
        assertEquals(0, Regions.of[21])      // VOISINS
    }

    @Test fun tableClassesFollowTheRealLayout() {
        assertEquals(0, TableCats.classOf(TableCats.COLOR, 21)); assertEquals(1, TableCats.classOf(TableCats.PARITY, 21))
        assertEquals(1, TableCats.classOf(TableCats.HIGHLOW, 21)); assertEquals(1, TableCats.classOf(TableCats.DOZEN, 21))
        assertEquals(2, TableCats.classOf(TableCats.COLUMN, 21))
        for (c in 0 until 5) assertEquals(-1, TableCats.classOf(c, 0))
        assertEquals(1, TableCats.classOf(TableCats.COLOR, 17))   // siyah
        assertEquals(0, TableCats.classOf(TableCats.HIGHLOW, 18)) // 18 son küçük, 19 ilk büyük
        assertEquals(1, TableCats.classOf(TableCats.HIGHLOW, 19))
        assertEquals(listOf("1–12", "13–24", "25–36"), (0..2).map { TableCats.optionLabel(TableCats.DOZEN, it) })
        assertEquals(listOf("Sütun 1", "Sütun 2", "Sütun 3"), (0..2).map { TableCats.optionLabel(TableCats.COLUMN, it) })
        for (cat in 0 until 5) {
            val k = TableCats.classes(cat)
            assertEquals("0 · yeşil", TableCats.optionLabel(cat, k))
            assertEquals(1.0 / 37.0, TableCats.baseline(cat, k), 1e-12)
            assertEquals(if (k == 2) 18.0 / 37.0 else 12.0 / 37.0, TableCats.baseline(cat, 0), 1e-12)
        }
    }

    @Test fun tablePredictionsKeepEverySideAndZeroProbability() {
        val table = TableEngine(Params(window = 100))
        var history = IntArray(0)
        repeat(100) {
            table.predict(history)
            table.learn(history, 1) // sürekli kırmızı sonuç: öğrenme yalnızca gerçek sonuçtan sonra
            history += 1
        }
        val calls = table.predict(history)
        assertEquals(5, calls.size)
        for (call in calls) {
            val k = TableCats.classes(call.cat)
            assertEquals(k + 1, call.probs.size) // bahis sınıfları + ayrı 0
            assertEquals(1.0, call.probs.sum(), 1e-9)
            assertEquals(call.probs[call.cls], call.p, 1e-12)
            assertEquals(TableCats.baseline(call.cat), call.base, 1e-12)
            assertEquals(call.probs[k], call.zeroProbability, 1e-12)
        }
        val color = calls.first { it.cat == TableCats.COLOR }
        assertTrue("kırmızı geçmişi modelin tüm yan bahislerini göstermeli", color.probability(0) > 0.0)
        assertTrue("siyah alternatifi de dağılımda bulunmalı", color.probability(1) > 0.0)
        assertTrue("0 hiçbir dış bahis sınıfına girmese de olasılığı tutulmalı", color.zeroProbability > 0.0)
        assertTrue("öğrenilen kırmızı dağılımı sabit eşit tabana çökmesin", color.probability(0) > color.probability(1))
        val dozen = calls.first { it.cat == TableCats.DOZEN }
        assertEquals(4, dozen.probs.size) // 3 düzine + 0
        val column = calls.first { it.cat == TableCats.COLUMN }
        assertEquals(4, column.probs.size) // 3 sütun + 0
    }

    @Test fun jsonRoundTrip() {
        val m = mapOf("a" to 1, "b" to listOf(1.5, 2.0, "x\"y"), "c" to mapOf("d" to true, "e" to null), "f" to doubleArrayOf(0.25, 3.0))
        val back = Json.parse(Json.stringify(m)).jmap()
        assertEquals(1, back["a"].jint()); assertEquals(1.5, back["b"].jlist()[0].jnum(), 1e-12)
        assertEquals("x\"y", back["b"].jlist()[2].jstr()); assertTrue(back["c"].jmap()["d"].jbool())
        assertEquals(0.25, back["f"].jdoubles()[0], 1e-12)
    }

    @Test fun candidatesAreBetween3And5AndDiverse() {
        val h = randomSpins(400, 1)
        for (n in 3..5) {
            val b = Brain(BrainConfig(nCand = n))
            val p = b.predict(h, null)
            assertEquals(n, p.candidates.size)
            assertTrue(p.candidates.map { it.sector }.toSet().size >= 2)
            for (i in 1 until p.candidates.size) assertTrue(p.candidates[i - 1].p >= p.candidates[i].p - 1e-12)
            assertEquals(1.0, p.pFinal.sum(), 1e-9)
            assertEquals(1.0, p.pKotlin.sum(), 1e-9)
        }
    }

    @Test fun recommendationsRespondToHistoryInsteadOfUsingFixedNumbers() {
        val config = BrainConfig(nCand = 5)
        val afterOne = Brain(config).predict(IntArray(500) { 1 }, null)
        val afterThirtyTwo = Brain(config).predict(IntArray(500) { 32 }, null)

        assertEquals("one-sided history should change the top recommendation", 1, afterOne.candidates.first().n)
        assertEquals("a different one-sided history should change it again", 32, afterThirtyTwo.candidates.first().n)
        assertTrue(afterOne.pFinal[1] > afterOne.pFinal[32])
        assertTrue(afterThirtyTwo.pFinal[32] > afterThirtyTwo.pFinal[1])
    }

    @Test fun exactAndCandidateHitsAreSeparate() {
        val h = randomSpins(300, 2)
        val b = Brain(BrainConfig())
        val p = b.predict(h, null)
        val top = p.candidates[0].n; val second = p.candidates[1].n
        val e1 = Evaluator.evaluate(p, top)
        assertTrue(e1.exact); assertTrue(e1.candidate); assertEquals(1, e1.candRank)
        val e2 = Evaluator.evaluate(p, second)
        assertFalse(e2.exact); assertTrue(e2.candidate); assertEquals(2, e2.candRank)
        assertTrue(e2.neighbor)
        val miss = (0 until 37).first { x -> p.candidates.none { it.n == x } }
        val e3 = Evaluator.evaluate(p, miss)
        assertFalse(e3.exact); assertFalse(e3.candidate); assertEquals(0, e3.candRank)
    }

    @Test fun replayCutsFutureAndHasNoLeakage() {
        val a = randomSpins(700, 3)
        val b = a.copyOf(); val r = java.util.Random(99); for (i in 450 until b.size) b[i] = r.nextInt(37)   // yalnızca GELECEK farklı
        val ra = ReplayEngine(BrainConfig()).run(a, times(a.size), 50, 50, to = 450)
        val rb = ReplayEngine(BrainConfig()).run(b, times(b.size), 50, 50, to = 450)
        assertEquals(ra.hash, rb.hash)
        assertEquals(400, ra.steps.size)
        assertTrue(ra.leakFree)
        // gelecekteki verinin farklı olması 450'ye kadarki hiçbir tahmini değiştirmemeli
        for (i in ra.steps.indices) assertEquals(ra.steps[i].centers.toList(), rb.steps[i].centers.toList())
    }

    @Test fun replayIsDeterministic() {
        val a = randomSpins(500, 4)
        val h1 = ReplayEngine(BrainConfig()).run(a, times(a.size), 50, 150).hash
        val h2 = ReplayEngine(BrainConfig()).run(a, times(a.size), 50, 150).hash
        assertEquals(h1, h2)
        val h3 = ReplayEngine(BrainConfig(window = 100)).run(a, times(a.size), 50, 150).hash
        assertTrue(h1 != h3)
    }

    @Test fun badTimestampsRaiseSequenceError() {
        val a = randomSpins(120, 5); val t = times(a.size); t[80] = t[10]
        try { ReplayEngine(BrainConfig()).run(a, t, 50, 50); org.junit.Assert.fail("LR-E-SEQ-001 bekleniyordu") }
        catch (e: LrError) { assertEquals(Codes.SEQ, e.code) }
    }

    @Test fun randomDataStaysNearBaseline() {
        val a = randomSpins(3000, 6)
        val res = ReplayEngine(BrainConfig()).run(a, times(a.size), 100, 1000)
        val n = res.steps.size
        val hit5 = res.steps.count { it.cand }.toDouble() / n
        val se = Math.sqrt(5.0 / 37 * (1 - 5.0 / 37) / n)
        assertTrue("candidate-5 isabeti tabandan çok uzak: $hit5", Math.abs(hit5 - 5.0 / 37) < 4 * se)
        val ex = res.steps.count { it.exact }.toDouble() / n
        assertTrue("exact isabeti tabandan çok uzak: $ex", Math.abs(ex - 1.0 / 37) < 4 * Math.sqrt(1.0 / 37 * 36 / 37 / n))
    }

    @Test fun hedgeKeepsFloorAndSumsToOne() {
        val h = Hedge(5)
        val ps = List(5) { uniform37() }
        for (t in 0 until 200) h.update(ps, t % 37)
        assertEquals(1.0, h.w.sum(), 1e-9)
        for (w in h.w) assertTrue(w >= 0.02 / 5 - 1e-12)
    }

    @Test fun brainStateRoundTrip() {
        val h = randomSpins(400, 7)
        val b = Brain(BrainConfig())
        val r = ReplayEngine(BrainConfig()).run(h, times(h.size), 50, 50, brain = b)
        val js = r.brain.stateJson()
        val b2 = Brain(BrainConfig())
        assertTrue(b2.restore(js))
        for (i in b.hedgeK.w.indices) assertEquals(b.hedgeK.w[i], b2.hedgeK.w[i], 1e-9)
        assertEquals(b.tau, b2.tau, 1e-9)
        assertFalse(Brain(BrainConfig(features = setOf("wheel", "sector"))).restore(js))
    }
}

class ResumeTest {
    private fun spins(n: Int): IntArray { val r = java.util.Random(31); return IntArray(n) { r.nextInt(37) } }
    private fun times(n: Int): LongArray = LongArray(n) { 1_700_000_000L + it * 60L }

    @Test fun checkpointResumeGivesSameResultAsUninterruptedRun() {
        val a = spins(600); val t = times(a.size)
        val full = ReplayEngine(BrainConfig()).run(a, t, 50, 100)
        var snap: Resume? = null
        // yarıda kes: 300 adımda iptal et
        var count = 0
        val part = ReplayEngine(BrainConfig()).run(a, t, 50, 100, cancel = { count++ >= 300 }, checkpointEvery = 100, onCheckpoint = { snap = it })
        assertTrue(part.cancelled)
        val saved = StepIO.decode(StepIO.encode(snap!!))!!          // JSON gidiş-dönüş
        val resumed = ReplayEngine(BrainConfig()).run(a, t, 50, 100, resume = saved)
        assertEquals(full.hash, resumed.hash)
        assertEquals(full.steps.size, resumed.steps.size)
    }
}
