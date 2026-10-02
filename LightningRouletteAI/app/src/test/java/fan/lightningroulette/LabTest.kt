package fan.lightningroulette

import fan.lightningroulette.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportTest {
    private fun spinsOf(vararg v: Int) = v.mapIndexed { i, x -> Spin(i.toLong(), x, 1_700_000_000_000L + i * 1000L) }

    @Test fun txtReportCountsAndReasons() {
        val r = Importer.analyse("5 12 0 36\n40, abc 7\n# yorum\n00", "a.txt", nowMs = 1_800_000_000_000L)
        assertEquals("TXT", r.format)
        assertEquals(8, r.total); assertEquals(5, r.valid); assertEquals(3, r.invalid); assertEquals(0, r.duplicate); assertEquals(5, r.newCount)
        assertEquals(listOf(5, 12, 0, 36, 7), r.spins.map { it.value })
        assertTrue(r.invalidRows.any { it.reason!!.contains("aralık") }); assertTrue(r.invalidRows.any { it.reason!!.contains("Amerikan") })
        assertTrue(r.syntheticTs); assertTrue(r.spins.all { it.tsType == "SYNTHETIC_IMPORT" })
        for (i in 1 until r.spins.size) assertTrue(r.spins[i - 1].ts <= r.spins[i].ts)
    }

    @Test fun csvHeaderDelimiterAndTimestamps() {
        val csv = "numara;zaman\n5;2026-01-02T03:04:05Z\n7;2026-01-02T03:04:35Z\n9;02.01.2026 03:05:10\nx;2026-01-02T03:06:00Z\n"
        val r = Importer.analyse(csv, "d.csv")
        assertEquals("CSV", r.format); assertEquals(4, r.total); assertEquals(3, r.valid); assertEquals(1, r.invalid)
        assertEquals(listOf(5, 7, 9), r.spins.map { it.value })
        assertTrue(r.spins.all { it.tsType == "REAL" }); assertFalse(r.syntheticTs)
        assertEquals(java.time.Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(), r.spins[0].ts)
    }

    @Test fun jsonFormatsAndExportRoundTrip() {
        val a = Importer.analyse("[1,2,3,36,0]", "x.json"); assertEquals(5, a.newCount)
        val b = Importer.analyse("{\"spins\":[{\"value\":5,\"ts\":1700000000000},{\"value\":6,\"ts\":1700000001000}]}")
        assertEquals("JSON", b.format); assertEquals(listOf(5, 6), b.spins.map { it.value }); assertTrue(b.spins.all { it.tsType == "REAL" })
        val bad = try { Importer.analyse("{\"x\":1}"); null } catch (e: LrError) { e.code }
        assertEquals(Codes.IMP, bad)
        val src = SampleData.generate(300)
        for (fmt in listOf("csv", "json", "txt")) {
            val text = when (fmt) { "csv" -> Exporter.csv(src); "json" -> Exporter.json(src); else -> Exporter.txt(src) }
            val r = Importer.analyse(text, "e.$fmt", mode = "NEW_DATASET")
            assertEquals("$fmt değerler", src.map { it.value }, r.spins.map { it.value }); assertEquals(0, r.invalid)
        }
        assertEquals(Importer.datasetHash(src), Importer.datasetHash(SampleData.generate(300)))
    }

    @Test fun overlapDetectionAndOrder() {
        val r0 = java.util.Random(5); val all = List(160) { r0.nextInt(37) }
        val existing = all.take(100).mapIndexed { i, v -> Spin(i.toLong(), v, 1_700_000_000_000L + i * 1000L) }
        val rep = Importer.analyse(all.drop(70).joinToString(" "), "o.txt", existing = existing, nowMs = 1_800_000_000_000L)
        assertEquals(30, rep.overlap); assertEquals(30, rep.duplicate); assertEquals(60, rep.newCount)
        assertEquals(all.drop(100), rep.spins.map { it.value })
        assertTrue(rep.spins.first().ts > existing.last().ts)
        val whole = Importer.analyse(all.take(100).joinToString(" "), "o.txt", existing = existing)
        assertEquals(0, whole.newCount); assertEquals(100, whole.duplicate)
        val inner = Importer.analyse(all.subList(20, 60).joinToString(" "), "o.txt", existing = existing)
        assertEquals(0, inner.newCount)
        val nf = Importer.analyse("1 2 3", order = "NEW_FIRST"); assertEquals(listOf(3, 2, 1), nf.spins.map { it.value })
        val dup = Importer.analyse("value,ts\n5,1700000000000\n5,1700000000000\n6,1700000001000\n", "d.csv")
        assertEquals(1, dup.duplicate); assertEquals(2, dup.newCount)
        val ded = Importer.dedupeExact(listOf(Spin(1, 5, 10), Spin(2, 5, 10), Spin(3, 6, 11)))
        assertEquals(2, ded.size)
    }

    @Test fun sampleDatasetIsDeterministicSyntheticAndFair() {
        val a = SampleData.generate(); val b = SampleData.generate()
        assertEquals(2000, a.size); assertEquals(a.map { it.value }, b.map { it.value })
        assertTrue(a.all { it.tsType == "SYNTHETIC_IMPORT" && it.source == "SAMPLE" })
        assertTrue(a.all { it.value in 0..36 })
        val cnt = IntArray(37); for (s in a) cnt[s.value]++
        assertTrue("her cep en az bir kez gelmeli", cnt.all { it > 20 })
    }
}

class LabTest {
    private fun rnd(n: Int, seed: Long): IntArray { val r = java.util.Random(seed); return IntArray(n) { r.nextInt(37) } }
    private fun tsOf(n: Int) = LongArray(n) { 1_700_000_000_000L + it * 30_000L }
    private val quick = ExpConfig("test", window = 120, k = 0, cands = 5, features = BrainConfig.ALL_FEATURES - "ML")

    @Test fun runOnRandomDataStaysNearBaselineAndIsDeterministic() {
        val v = rnd(1400, 11); val t = tsOf(v.size)
        val a = LabRunner.run(v, t, quick); val b = LabRunner.run(v, t, quick)
        assertEquals("aynı koşu aynı özet", a.result.hash, b.result.hash)
        val r = a.result
        assertTrue(r.leakFree); assertEquals("DONE", r.status)
        assertEquals(LabRunner.segmentLen(v.size), r.n)
        assertTrue("Exact + Candidate + Table ölçütleri var", r.metrics.map { it.name }.containsAll(listOf("Exact", "Candidate-3", "Candidate-4", "Candidate-5", "Neighbor", "Sector", "Region", "Color", "Parity", "High/Low", "Dozen", "Column")))
        assertTrue("rastgele veride fark küçük: ${r.deltaPp}", Math.abs(r.deltaPp) < 4.0)
        assertTrue("rastgele veri A/B olmamalı: ${r.cls}", r.cls != "A")
        assertTrue(r.permP in 0.0..1.0)
        val back = ExpResult.fromJson(r.toJson()); assertNotNull(back)
        assertEquals(r.cls, back!!.cls); assertEquals(r.deltaPp, back.deltaPp, 1e-9); assertEquals(r.hash, back.hash); assertEquals(r.cfg.paramHash(), back.cfg.paramHash())
    }

    @Test fun classificationRules() {
        assertEquals("S", LabRunner.classify(100, 5.0, 3.0, 7.0, 0.001, 5.0, true, 80))
        assertEquals("L", LabRunner.classify(2000, 5.0, 3.0, 7.0, 0.001, 5.0, false, 80))
        assertEquals("O", LabRunner.classify(2000, 0.1, -1.0, 1.0, 0.4, 4.0, true, 50))
        assertEquals("F", LabRunner.classify(2000, -3.0, -5.0, -1.0, 0.99, -2.0, true, 10))
        assertEquals("A", LabRunner.classify(2000, 3.0, 1.0, 5.0, 0.002, 2.5, true, 75))
        assertEquals("B", LabRunner.classify(2000, 3.0, 1.0, 5.0, 0.002, 2.5, true, 30))
        assertEquals("C", LabRunner.classify(2000, 0.2, -1.0, 1.5, 0.5, 0.1, true, 30))
    }

    @Test fun allSixteenTabsBuildAndSerialize() {
        val v = rnd(1300, 3); val t = tsOf(v.size)
        val run = LabRunner.run(v, t, quick)
        val ctx = LabCtx(v, t, Sectors.DEFAULT, run, quick, false, emptyList(), null, null, emptyList(), 0, "Test")
        assertEquals(16, LabTabs.TABS.size)
        for ((id, _) in LabTabs.TABS) {
            val secs = LabTabs.build(ctx, id)
            assertTrue("$id boş", secs.isNotEmpty())
            val back = S.decode(S.encode(secs)); assertEquals("$id JSON", secs.size, back.size)
        }
        for ((id, _) in LabTabs.ROB_TABS) assertTrue(LabTabs.build(ctx, "robustness", id).isNotEmpty())
        assertTrue(ctx.robust.score in 0..100)
        val small = LabTabs.build(LabCtx(v.copyOf(40), t.copyOf(40), Sectors.DEFAULT, LabRunner.run(v.copyOf(40), t.copyOf(40), quick), quick, false, emptyList(), null, null, emptyList(), 0, "x"), "overview")
        assertEquals("text", small[0].type)
    }

    @Test fun plantedSectorPersistenceIsDetectedButNotOverClaimed() {
        val sec = Sectors.DEFAULT; val r = java.util.Random(9); var s = 0
        val v = IntArray(3000) { if (r.nextDouble() > 0.45) s = r.nextInt(sec.count); sec.members(s)[r.nextInt(sec.size(s))] }
        val rows = An.sectorTests(v, Sectors.DEFAULT)
        val pers = rows.first { it.name == "persistence" }
        assertTrue("persistence tespit edilmeli (p=${pers.p})", pers.p < 0.0033 && pers.cls == "B" && pers.delta > 3)
        val rnd = An.sectorTests(rnd(3000, 21), Sectors.DEFAULT)
        assertTrue("rastgele veride Bonferroni sonrası B beklenmez", rnd.count { it.cls == "B" } <= 1)
    }

    @Test fun suitesProduceAblationSensitivityAndStress() {
        val v = rnd(900, 5); val t = tsOf(v.size)
        val cfg = ExpConfig("suite", window = 80, features = BrainConfig.ALL_FEATURES - "ML")
        val su = LabSuites.runAll(v, t, cfg, Sectors.DEFAULT, true, null, null)
        assertNotNull(su.full); assertTrue(su.ablation.size >= 8); assertTrue(su.counterfactual.size >= 6)
        assertNotNull(su.sens); assertTrue(su.stress!!.size == 8 && su.stress.all { it.ok })
        val back = Suites.fromJson(su.toJson()); assertNotNull(back); assertEquals(su.ablation.size, back!!.ablation.size)
        val ctx = LabCtx(v, t, Sectors.DEFAULT, LabRunner.run(v, t, cfg), cfg, false, emptyList(), null, su, emptyList(), 0, "T")
        for (id in listOf("counterfactual", "ablation")) assertTrue(LabTabs.build(ctx, id).size >= 2)
        for ((id, _) in LabTabs.ROB_TABS) assertTrue(LabTabs.build(ctx, "robustness", id).isNotEmpty())
    }

    @Test fun cancelAndResumeThroughRunner() {
        val v = rnd(900, 8); val t = tsOf(v.size)
        val full = LabRunner.run(v, t, quick)
        var snap: Resume? = null; var calls = 0
        LabRunner.run(v, t, quick, cancel = { calls++ > 400 }, checkpointEvery = 100, onCheckpoint = { snap = StepIO.decode(StepIO.encode(it)) })
        assertNotNull(snap)
        val resumed = LabRunner.run(v, t, quick, resume = snap)
        assertEquals(full.result.hash, resumed.result.hash)
    }
}

