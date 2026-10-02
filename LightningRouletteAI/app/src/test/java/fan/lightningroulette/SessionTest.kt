package fan.lightningroulette

import fan.lightningroulette.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePy(val mode: String = "ok") : PyHost {
    var calls = 0
    override fun predict(window: IntArray, offset: Int): PyOut {
        calls++
        if (mode == "throw") throw IllegalStateException("python çöktü")
        val n = window.size + offset
        val r = java.util.Random(n.toLong())
        val ids = listOf("lstm", "hmm")
        val probs = ids.map { _ -> val x = DoubleArray(37) { 0.5 + r.nextDouble() }; val s = x.sum(); DoubleArray(37) { x[it] / s } }
        if (mode == "badsize") return PyOut(ids, listOf(DoubleArray(36) { 1.0 / 36 }, DoubleArray(36) { 1.0 / 36 }))
        if (mode == "badsum") return PyOut(ids, probs.map { p -> DoubleArray(37) { p[it] * 2 } })
        return PyOut(ids, probs)
    }
}

class SessionTest {
    private val cfg = BrainConfig(window = 120)
    private fun rnd(n: Int, seed: Long): List<Int> { val r = java.util.Random(seed); return List(n) { r.nextInt(37) } }
    private fun open(store: MemStore, py: PyHost? = null, c: BrainConfig = cfg, clock: () -> Long = { 1_800_000_000_000L }): Session { val s = Session(store, c, py, clock); s.open(); return s }

    @Test fun learningThresholdAndLockImmutability() {
        val st = MemStore(); st.seed(rnd(49, 1)); val s = open(st)
        assertNull("eşik altında tahmin yok", s.predictNext())
        val r = s.enter(7, 49)
        assertTrue(r is EnterResult.Entered)
        val a = s.predictNext(); assertNotNull(a); assertEquals(50, a!!.refCount)
        val b = s.predictNext(); assertEquals(a.code, b!!.code); assertEquals(a.createdAt, b.createdAt)
        assertNull("aynı spin için ikinci tahmin kaydedilemez (LR-E-LOCK-002)", st.savePrediction(50, 1L, "{}", "OFF", "x"))
        assertEquals(1, st.preds.size)
        assertTrue("3–5 aday", a.candidates.size in 3..5)
        val before = st.preds[50]!!.json
        s.enter(11, 50)
        assertEquals("kilitli tahmin sonradan değişmez", before, st.preds[50]!!.json)
    }

    @Test fun enterEvaluatesLearnsAndLocksNext() {
        val st = MemStore(); st.seed(rnd(60, 2)); val s = open(st)
        val p = s.predictNext()!!
        val steps0 = s.brain.steps
        val r = s.enter(17, 60) as EnterResult.Entered
        assertEquals(61, s.size); assertEquals(61, st.spinList.size)
        val ev = r.eval; val evaluated = r.evaluated; val nx = r.next
        assertNotNull(ev); assertEquals(p.code, evaluated!!.code); assertEquals(17, ev!!.actual)
        assertEquals(steps0 + 1, s.brain.steps)
        assertEquals(61, nx!!.refCount); assertTrue(nx.code != p.code)
        assertEquals(1, st.evals.size)
        assertTrue(r.spin.ts > st.spinList[58].ts)
        assertEquals("exact ve candidate ayrı", ev.exact, p.candidates[0].n == 17)
    }

    @Test fun doubleEnterAndInvalidValuesWriteNothing() {
        val st = MemStore(); st.seed(rnd(60, 3)); val s = open(st); s.predictNext()
        val ok = s.enter(5, 60); assertTrue(ok is EnterResult.Entered)
        val dup = s.enter(5, 60); assertTrue("çift ENTER yoksayılır", dup is EnterResult.Ignored)
        assertEquals(61, st.spinList.size)
        for (bad in listOf(37, -1, 99)) { val r = s.enter(bad, 61); assertTrue(r is EnterResult.Rejected); assertEquals(Codes.REC, (r as EnterResult.Rejected).code) }
        assertEquals(61, st.spinList.size); assertEquals(61, s.size); assertTrue(st.hasLog(Codes.REC))
    }

    @Test fun undoBringsBackSameLockedPredictionAndState() {
        val st = MemStore(); st.seed(rnd(60, 4)); val s = open(st)
        val a = s.predictNext()!!; val blobBefore = s.brain.fullStateJson()
        val r1 = s.enter(5, 60) as EnterResult.Entered
        val removed = s.undoLast(); assertEquals(5, removed!!.value)
        assertEquals(60, s.size); assertEquals(60, st.spinList.size); assertEquals(0, st.evals.size)
        assertEquals("aynı kilitli tahmin geri gelir", a.code, s.view!!.code); assertEquals(a.createdAt, s.view!!.createdAt)
        assertEquals(blobBefore, s.brain.fullStateJson())
        assertNull("sonraki spine ait tahmin silindi", st.preds[61])
        val r2 = s.enter(5, 60) as EnterResult.Entered
        assertEquals(r1.next!!.candidates.map { it.n }, r2.next!!.candidates.map { it.n })
        // ikinci kez geri al: tek seviye anlık görüntü yok → yeniden kurulum, yine tutarlı
        s.undoLast(); s.undoLast()
        assertEquals(59, s.size); assertEquals(59, st.spinList.size)
        assertEquals(59, s.predictNext()!!.refCount); assertTrue(st.preds.keys.all { it <= 59 })
        assertEquals(s.brain.fullStateJson(), open(st).brain.fullStateJson())
    }

    @Test fun restartGivesIdenticalStateAndPrediction() {
        val st = MemStore(); st.seed(rnd(60, 5)); val s1 = open(st)
        s1.predictNext()
        for (v in rnd(30, 6)) s1.enter(v, s1.size)
        val s2 = open(st)
        assertEquals(s1.brain.fullStateJson(), s2.brain.fullStateJson())
        assertEquals(s1.view!!.code, s2.view!!.code)
        assertEquals(s1.size, s2.size)
        val a = s1.enter(21, s1.size) as EnterResult.Entered; val b = s2.enter(21, s2.size) as EnterResult.Entered
        assertEquals(a.next!!.candidates.map { it.n }, b.next!!.candidates.map { it.n })
        assertEquals(s1.brain.fullStateJson(), s2.brain.fullStateJson())
        assertEquals("yeniden başlatma sonrası öğrenme için üye olasılıkları yeniden hesaplandı", a.eval!!.exact, b.eval!!.exact)
    }

    @Test fun restartEquivalenceAtEveryRefitBoundary() {
        // Kotlin ML üyesi 25 adımda bir tahmin sırasında yeniden uydurur: durum kaydı bu sınırlarda gerideymiş (hata). Sınırları tara.
        for (count in listOf(1, 14, 15, 16, 24, 25, 26, 40)) {
            val st = MemStore(); st.seed(rnd(60, 30L + count)); val s1 = open(st); s1.predictNext()
            for (v in rnd(count, 40L + count)) s1.enter(v, s1.size)
            val s2 = open(st)
            assertEquals("yeniden başlatma sonrası durum ($count spin)", s1.brain.fullStateJson(), s2.brain.fullStateJson())
            assertEquals(s1.view!!.code, s2.view!!.code)
            // geri al sonrası da tutarlı: iki oturum aynı anlık görüntüye döner
            s1.undoLast(); val s3 = open(st)
            assertEquals("geri alma sonrası durum ($count spin)", s1.brain.fullStateJson(), s3.brain.fullStateJson())
        }
    }

    @Test fun tailCatchUpProcessesOnlyNewRecordsAndMatchesContinuousRun() {
        val base = rnd(100, 7); val tail = rnd(5, 8)
        val stA = MemStore(); stA.seed(base); val sA = open(stA); sA.predictNext()
        val stB = MemStore(); stB.seed(base); val sB = open(stB); sB.predictNext()
        for (v in tail) sA.enter(v, sA.size)
        for (v in tail) stB.addSpin(v, 1_800_000_000_000L + stB.spinList.size, "LIVE", "REAL")     // çökme: kayıt var, öğrenme yok
        val sB2 = open(stB)
        assertEquals(105, sB2.learned); assertTrue(stB.hasLog("").not())
        assertEquals(sA.brain.fullStateJson(), sB2.brain.fullStateJson())
        assertEquals(sA.brain.steps, sB2.brain.steps)
    }

    @Test fun pythonFailureFallsBackToKotlinAndIsReported() {
        val st = MemStore(); st.seed(rnd(70, 9)); val c = BrainConfig(window = 120, kotlinWeight = 0.5)
        for (mode in listOf("throw", "badsize", "badsum")) {
            val st2 = MemStore(); st2.seed(rnd(70, 9)); val s = open(st2, FakePy(mode), c)
            val p = s.predictNext()!!
            assertEquals("ERROR", p.pyStatus); assertNull(p.pPython); assertTrue(p.candidates.size in 3..5)
            assertTrue("hata kodu yazıldı: $mode", st2.hasLog(Codes.PY))
        }
        val ok = open(st, FakePy("ok"), c).predictNext()!!
        assertEquals("OK", ok.pyStatus); assertNotNull(ok.pPython); assertTrue(ok.votes.any { it.council == "P" })
        val off = open(MemStore().also { it.seed(rnd(70, 9)) }, FakePy("ok"), BrainConfig(window = 120, kotlinWeight = 1.0)).predictNext()!!
        assertEquals("OFF", off.pyStatus)
    }

    @Test fun failedTransactionKeepsEverythingConsistent() {
        val st = MemStore(); st.seed(rnd(60, 10)); val s = open(st); s.predictNext()
        val blob = s.brain.fullStateJson(); val code = s.view!!.code
        st.failTx = true
        val err = try { s.enter(9, 60); null } catch (e: LrError) { e.code }
        assertEquals(Codes.DB, err)
        assertEquals(60, st.spinList.size); assertEquals(60, s.size); assertEquals(blob, s.brain.fullStateJson()); assertEquals(0, st.evals.size)
        assertEquals(code, s.view!!.code)
        assertTrue(s.enter(9, 60) is EnterResult.Entered); assertEquals(61, st.spinList.size)
    }

    @Test fun timestampsStayMonotonicWhenClockGoesBack() {
        val st = MemStore(); st.seed(rnd(60, 11)); var t = 1_900_000_000_000L
        val s = open(st, null, cfg) { t }
        val a = (s.enter(1, 60) as EnterResult.Entered).spin
        t = 1_000L
        val b = (s.enter(2, 61) as EnterResult.Entered).spin
        assertTrue(b.ts > a.ts)
    }

    @Test fun sampleDatasetFlowEndToEnd() {
        val st = MemStore(); for (sp in SampleData.generate(1200)) st.addSpin(sp.value, sp.ts, sp.source, sp.tsType)
        val s = open(st)
        assertEquals(1200, s.size); assertNotNull(s.predictNext())
        val before = s.brain.steps
        repeat(10) { assertTrue(s.enter(it * 3, s.size) is EnterResult.Entered) }
        assertEquals(before + 10, s.brain.steps); assertEquals(10, st.evals.size)
        val s2 = open(st); assertEquals(s.brain.fullStateJson(), s2.brain.fullStateJson())
        s2.fullRebuild(); assertEquals(1210, s2.size); assertNotNull(s2.predictNext())
    }

    @Test fun brainRestoreIsExactIncludingCalibrationHistory() {
        val v = rnd(220, 12).toIntArray(); val t = LongArray(v.size) { 1_700_000_000_000L + it * 1000L }
        val full = ReplayEngine(cfg).run(v, t, 0, 0, v.size)
        val part = ReplayEngine(cfg).run(v, t, 0, 0, 150)
        val b2 = Brain(cfg); assertTrue(b2.restoreFull(part.brain.fullStateJson()))
        assertEquals(part.brain.fullStateJson(), b2.fullStateJson())
        val res = ReplayEngine(cfg).run(v, t, 150, 150, v.size, brain = b2)
        assertEquals(full.brain.fullStateJson(), res.brain.fullStateJson())
    }
}
