package fan.lightningroulette.engine

import fan.lightningroulette.core.*

class TestResult(val group: String, val name: String, val status: String, val detail: String, val code: String = "")   // status: pass | fail | skip

/** Uygulama içi tanılama (Ayarlar › Tanılama): gerçek çekirdek kodunu küçük, deterministik senaryolarda çalıştırır. */
object SelfTest {
    private fun rnd(n: Int, seed: Long): IntArray { val r = java.util.Random(seed); return IntArray(n) { r.nextInt(37) } }
    private fun tsOf(n: Int) = LongArray(n) { 1_700_000_000_000L + it * 30_000L }

    private fun t(group: String, name: String, code: String = "", body: () -> String): TestResult = try {
        val d = body(); TestResult(group, name, "pass", d)
    } catch (e: SkipTest) { TestResult(group, name, "skip", e.message ?: "")
    } catch (e: Throwable) { TestResult(group, name, "fail", (e.message ?: e.javaClass.simpleName), if (e is LrError) e.code else code) }

    private class SkipTest(m: String) : RuntimeException(m)
    private fun check(c: Boolean, msg: String) { if (!c) throw IllegalStateException(msg) }

    /** Son çalıştırmanın sonuçları (e2e testi ve arayüz dışı tanı için). */
    @Volatile var last: List<TestResult> = emptyList()

    val GROUPS = listOf("Veri bütünlüğü", "Canlı giriş", "Sızıntı ve replay", "Kalıcılık", "İstatistik", "Aktarım", "Model kuralları")

    fun run(onResult: (TestResult) -> Unit): List<TestResult> {
        val out = ArrayList<TestResult>()
        fun add(r: TestResult) { out.add(r); last = out.toList(); onResult(r) }
        val d = Engine.dataset
        val spins = if (d != null) Engine.dao.spins(d.id) else emptyList()

        // ── Veri bütünlüğü
        add(t(GROUPS[0], "Spin değerleri 0–36", Codes.REC) { check(spins.all { it.value in 0..36 }, "aralık dışı değer var"); "${spins.size} kayıt tarandı" })
        add(t(GROUPS[0], "Zaman damgaları geriye gitmiyor", Codes.SEQ) { for (i in 1 until spins.size) check(spins[i - 1].tsMs <= spins[i].tsMs, "#${spins[i].id} zamanı geriye gidiyor"); "sıra tutarlı" })
        add(t(GROUPS[0], "Tahmin kilidi benzersiz ve sınır içinde", Codes.LOCK2) {
            if (d == null) throw SkipTest("dataset yok")
            val ps = Engine.dao.lastPredictions(d.id, 500); check(ps.map { it.refCount }.toSet().size == ps.size, "yinelenen refCount"); check(ps.all { it.refCount <= spins.size }, "refCount spin sayısını aşıyor"); "${ps.size} tahmin"
        })
        add(t(GROUPS[0], "Değerlendirmeler mevcut spinlere bağlı", Codes.DB) {
            if (d == null) throw SkipTest("dataset yok")
            val ids = spins.map { it.id }.toHashSet(); val ev = Engine.dao.lastEvaluations(d.id, 300); check(ev.all { it.spinId in ids }, "sahipsiz değerlendirme"); "${ev.size} değerlendirme"
        })

        // ── Canlı giriş
        fun session(n: Int, py: PyHost? = null): Pair<Session, MemStore> { val st = MemStore(); rnd(n, 5).forEachIndexed { i, v -> st.addSpin(v, 1_700_000_000_000L + i * 1000L, "TEST", "REAL") }; val s = Session(st, BrainConfig(window = 120), py); s.open(); return s to st }
        add(t(GROUPS[1], "0–36 dışı giriş reddedilir", Codes.REC) { val (s, st) = session(60); for (b in listOf(37, -1, 99)) check(s.enter(b, s.size) is EnterResult.Rejected, "$b kabul edildi"); check(st.spinList.size == 60, "kayıt yazıldı"); "37, −1, 99 reddedildi" })
        add(t(GROUPS[1], "Çift ENTER ikinci kayıt oluşturmaz", Codes.REC) { val (s, st) = session(60); check(s.enter(5, 60) is EnterResult.Entered, "ilk ENTER"); check(s.enter(5, 60) is EnterResult.Ignored, "çift ENTER yoksayılmadı"); check(st.spinList.size == 61, "spin sayısı"); "yoksayıldı" })
        add(t(GROUPS[1], "Baştaki sıfır normalize edilir (07 → 7)", Codes.IMP) { val r = Importer.analyse("07\n7\n", "x.txt"); check(r.spins.all { it.value == 7 }, "07 ≠ 7"); "07 → 7" })

        // ── Sızıntı ve replay
        add(t(GROUPS[2], "Gelecek değer geçmiş tahmini değiştirmez", Codes.LEAK) {
            val a = rnd(300, 11); val b = a.copyOf(); val r = java.util.Random(99); for (i in 250 until 300) b[i] = r.nextInt(37)
            val cfg = BrainConfig(window = 120); val ts = tsOf(300)
            val ra = ReplayEngine(cfg).run(a, ts, 50, 50, 300); val rb = ReplayEngine(cfg).run(b, ts, 50, 50, 300)
            for (i in 0 until 200) check(ra.steps[i].pActual == rb.steps[i].pActual && ra.steps[i].centers.contentEquals(rb.steps[i].centers), "adım ${ra.steps[i].i} gelecekten etkilendi")
            "ilk 200 adım birebir aynı"
        })
        add(t(GROUPS[2], "Bozuk zaman sırası LR-E-SEQ-001 verir", Codes.SEQ) {
            val ts = tsOf(120); ts[80] = ts[10]
            try { ReplayEngine(BrainConfig(window = 120)).run(rnd(120, 3), ts, 50, 50, 120); throw IllegalStateException("hata verilmedi") } catch (e: LrError) { check(e.code == Codes.SEQ, "kod ${e.code}"); e.code }
        })
        add(t(GROUPS[2], "Replay deterministik", Codes.REPRO) { val v = rnd(220, 4); val ts = tsOf(220); val c = BrainConfig(window = 120); val h1 = ReplayEngine(c).run(v, ts, 50, 50, 220).hash; val h2 = ReplayEngine(c).run(v, ts, 50, 50, 220).hash; check(h1 == h2, "özetler farklı"); java.lang.Long.toHexString(h1) })

        // ── Kalıcılık
        add(t(GROUPS[3], "Yeniden başlatma = kesintisiz durum", Codes.STATE1) {
            val (s1, st) = session(60); s1.predictNext(); for (v in rnd(15, 6)) s1.enter(v, s1.size)
            val s2 = Session(st, BrainConfig(window = 120)); s2.open()
            check(s1.brain.fullStateJson() == s2.brain.fullStateJson(), "model durumu farklı"); "15 spin sonrası aynı"
        })
        add(t(GROUPS[3], "Geri alma aynı kilitli tahmini döndürür", Codes.STATE2) {
            val (s, _) = session(60); val a = s.predictNext()!!; s.enter(3, 60); s.undoLast(); check(s.view?.code == a.code && s.view?.createdAt == a.createdAt, "tahmin değişti"); a.code
        })

        // ── İstatistik
        add(t(GROUPS[4], "Bootstrap güven aralığı ortalamayı kapsar") { val x = DoubleArray(300) { if (it % 7 == 0) 1.0 else 0.0 }; val ci = Stats.bootstrapCI(x, 500, 3); check(Stats.mean(x) in (ci[1] - 1e-9)..(ci[2] + 1e-9), "ortalama aralık dışı"); "ort ${S.f(Stats.mean(x), 3)} ∈ [${S.f(ci[1], 3)}; ${S.f(ci[2], 3)}]" })
        add(t(GROUPS[4], "Rastgele veride kanıt iddia edilmez (çoğu koşu sınıf C/D)", Codes.EXP) {
            // Tek bir rastgele koşu tesadüfen B çıkabilir (≈ %2–3); bu yüzden 3 bağımsız tohumda en fazla 1 B kabul edilir, A asla.
            val cfg = ExpConfig("tanılama", window = 120, features = BrainConfig.ALL_FEATURES - "ML")
            val res = listOf(21L, 22L, 23L).map { LabRunner.run(rnd(600, it), tsOf(600), cfg).result }
            check(res.none { it.cls == "A" }, "sınıf A: " + res.joinToString { it.cls })
            check(res.count { it.cls == "B" } <= 1, "çoğu koşu B: " + res.joinToString { it.cls })
            res.joinToString(" · ") { "${it.cls} (Δ ${S.sg(it.deltaPp)})" }
        })

        // ── Aktarım
        add(t(GROUPS[5], "Dışa aktar → içe aktar (CSV · JSON · TXT)", Codes.EXP) {
            val src = SampleData.generate(150)
            for (f in listOf("csv", "json", "txt")) {
                val txt = when (f) { "csv" -> Exporter.csv(src); "json" -> Exporter.json(src); else -> Exporter.txt(src) }
                val r = Importer.analyse(txt, "t.$f", null, "AUTO", "NEW_DATASET"); check(r.spins.map { it.value } == src.map { it.value } && r.invalid == 0, "$f farklı")
            }
            "3 biçim eşleşti"
        })
        add(t(GROUPS[5], "Sentetik zaman damgası işaretlenir", Codes.IMP) { val r = Importer.analyse("1 2 3 4 5", "x.txt"); check(r.syntheticTs && r.spins.all { it.tsType == "SYNTHETIC_IMPORT" }, "işaret yok"); "SYNTHETIC_IMPORT" })

        // ── Model kuralları
        add(t(GROUPS[6], "Aday sayısı 3–5 ve Exact ≠ Candidate ayrı", Codes.STATE1) {
            val v = rnd(200, 8); val b = Brain(BrainConfig(window = 120)); val p = b.predict(v, null)
            check(p.candidates.size in 3..5, "aday sayısı ${p.candidates.size}"); val e = Evaluator.evaluate(p, p.candidates.last().n); check(!e.exact || p.candidates.size == 1, "exact/candidate karıştı"); check(e.candidate, "aday kümesinde değil"); "${p.candidates.size} aday"
        })
        add(t(GROUPS[6], "Table 5 kategori", Codes.STATE1) { val p = Brain(BrainConfig(window = 120)).predict(rnd(200, 9), null); check(p.table.size == 5, "kategori ${p.table.size}"); "COLOR · PARITY · HIGH/LOW · DOZEN · COLUMN" })
        add(t(GROUPS[6], "Python çıktısı doğrulanır", Codes.PY) {
            check(PyValidator.check(PyOut(listOf("a"), listOf(DoubleArray(36) { 1.0 / 36 }))) != null, "36 boyut kabul edildi")
            check(PyValidator.check(PyOut(listOf("a"), listOf(DoubleArray(37) { 2.0 / 37 }))) != null, "toplam 2 kabul edildi")
            check(PyValidator.check(PyOut(listOf("a"), listOf(DoubleArray(37) { 1.0 / 37 }))) == null, "geçerli çıktı reddedildi"); "şema · aralık · toplam"
        })
        add(t(GROUPS[6], "Python meclisi (bu cihazda)", Codes.PY) {
            val b = Engine.py ?: throw SkipTest("Python kapalı/hazır değil (Kotlin-only)")
            // Canlı meclisin durumuna dokunmaz: Python tarafında ayrı bir Council örneğiyle kendi kendine sınama.
            check(b.selfTest() == "OK", "Python selftest başarısız"); "8 üye × 37 olasılık geçerli (canlı durum etkilenmedi)"
        })
        return out
    }
}
