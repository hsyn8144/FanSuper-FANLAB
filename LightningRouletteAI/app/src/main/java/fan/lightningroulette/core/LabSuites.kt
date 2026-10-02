package fan.lightningroulette.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class Paired(val delta: Double, val lo: Double, val hi: Double, val raw: Double) {
    fun toMap(): Map<String, Any?> = mapOf("d" to delta, "lo" to lo, "hi" to hi, "raw" to raw)
    companion object { fun fromMap(m: Map<String, Any?>) = Paired(m["d"].jnum(), m["lo"].jnum(), m["hi"].jnum(), m["raw"].jnum()) }
}
class SensGrid(val wins: List<Int>, val ks: List<Int>, val v: List<List<Double>>, val spike: List<Int>?) {
    fun toMap(): Map<String, Any?> = mapOf("w" to wins, "k" to ks, "v" to v, "spike" to spike)
    companion object { fun fromMap(m: Map<String, Any?>) = SensGrid(m["w"].jints().toList(), m["k"].jints().toList(), m["v"].jlist().map { it.jdoubles().toList() }, m["spike"].jlist().let { if (it.isEmpty()) null else it.map { x -> x.jint() } }) }
}
class StressRow(val name: String, val what: String, val outcome: String, val ok: Boolean) {
    fun toMap(): Map<String, Any?> = mapOf("n" to name, "w" to what, "o" to outcome, "ok" to ok)
    companion object { fun fromMap(m: Map<String, Any?>) = StressRow(m["n"].jstr(), m["w"].jstr(), m["o"].jstr(), m["ok"].jbool()) }
}

/** Ablation/counterfactual/hassasiyet/stres paketi sonuçları (veritabanında önbelleklenir). */
class Suites(
    val full: ExpResult?, val ablation: List<Pair<String, Paired>>, val counterfactual: List<Pair<String, Paired>>,
    val sens: SensGrid?, val stress: List<StressRow>?, val stamp: Long
) {
    fun toJson(): String = Json.stringify(mapOf(
        "full" to full?.toMap(), "abl" to ablation.map { mapOf("n" to it.first, "p" to it.second.toMap()) }, "cf" to counterfactual.map { mapOf("n" to it.first, "p" to it.second.toMap()) },
        "sens" to sens?.toMap(), "stress" to stress?.map { it.toMap() }, "stamp" to stamp))
    companion object {
        fun fromJson(s: String): Suites? = try {
            val m = Json.parse(s).jmap()
            Suites(m["full"]?.let { ExpResult.fromJson(Json.stringify(it)) },
                m["abl"].jlist().map { it.jmap()["n"].jstr() to Paired.fromMap(it.jmap()["p"].jmap()) },
                m["cf"].jlist().map { it.jmap()["n"].jstr() to Paired.fromMap(it.jmap()["p"].jmap()) },
                m["sens"]?.let { SensGrid.fromMap(it.jmap()) }, m["stress"]?.let { l -> l.jlist().map { StressRow.fromMap(it.jmap()) } }, m["stamp"].jlong())
        } catch (e: Exception) { null }
    }
}

object LabSuites {
    private fun excess(run: ExpRun, c: Int): DoubleArray {
        val oos = run.steps.filter { it.i >= run.oosStart }
        return DoubleArray(oos.size) { (if (oos[it].candRank in 1..c) 1.0 else 0.0) - c.toDouble() / Wheel.N }
    }
    private fun rawRate(run: ExpRun, c: Int): Double {
        val oos = run.steps.filter { it.i >= run.oosStart }
        return if (oos.isEmpty()) 0.0 else oos.count { it.candRank in 1..c }.toDouble() / oos.size
    }

    /** b − a (kapsama düzeltmeli, eşleştirilmiş bootstrap) — pp */
    fun paired(a: ExpRun, b: ExpRun, seed: Long = 5L): Paired {
        val ea = excess(a, a.result.cfg.cands); val eb = excess(b, b.result.cfg.cands)
        val m = min(ea.size, eb.size)
        val d = DoubleArray(m) { eb[it] - ea[it] }
        val ci = Stats.bootstrapCI(d, 1000, seed)
        return Paired(ci[0] * 100, ci[1] * 100, ci[2] * 100, (rawRate(b, b.result.cfg.cands) - rawRate(a, a.result.cfg.cands)) * 100)
    }

    fun spikeOf(v: List<List<Double>>): List<Int>? {
        for (i in v.indices) for (j in v[i].indices) {
            if (v[i][j] < 1.5) continue
            var calm = true
            for ((di, dj) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val a = i + di; val b = j + dj
                if (a in v.indices && b in v[a].indices && v[a][b] >= 0.5) calm = false
            }
            if (calm) return listOf(i, j)
        }
        return null
    }

    /** İlerleme: (tamamlanan, toplam, ad). cancel: true dönerse mevcut sonuçlarla biter. */
    fun runAll(values: IntArray, ts: LongArray, base: ExpConfig, sectors: Sectors, doStress: Boolean, cancel: (() -> Boolean)?, progress: ((Int, Int, String) -> Unit)?): Suites {
        val feats = base.features
        val plan = ArrayList<Pair<String, ExpConfig>>()
        plan.add("FULL" to base.with(hypothesis = "FULL"))
        for (g in listOf("frequency", "pattern", "sector", "region", "neighbor", "transition", "table", "ML")) plan.add("− ${g.replaceFirstChar { it.uppercase() }}" to base.with(hypothesis = "− $g", features = feats - g))
        plan.add("− Recency" to base.with(hypothesis = "− recency", decay = 0.0))
        plan.add("k1" to base.with(hypothesis = "k1", k = 1)); plan.add("k3" to base.with(hypothesis = "k3", k = 3))
        plan.add("3 aday" to base.with(hypothesis = "3 aday", cands = 3)); plan.add("4 aday" to base.with(hypothesis = "4 aday", cands = 4))
        plan.add("Wheel only" to base.with(hypothesis = "wheel only", features = setOf("wheel")))
        plan.add("Wheel+Table" to base.with(hypothesis = "wheel+table", features = setOf("wheel", "table")))
        val wins = listOf(0.75, 0.875, 1.0, 1.125, 1.25).map { max(20, ((base.window * it) / 5).roundToInt() * 5) }.distinct()
        val ks = listOf(1, 2, 3)
        val total = plan.size + wins.size * ks.size + (if (doStress) 8 else 0)
        var done = 0
        val runs = HashMap<String, ExpRun>()
        for ((name, cfg) in plan) {
            if (cancel?.invoke() == true) break
            progress?.invoke(done, total, name)
            runs[name] = LabRunner.run(values, ts, cfg, sectors, null, cancel)
            done++
        }
        val full = runs["FULL"]
        val abl = ArrayList<Pair<String, Paired>>(); val cf = ArrayList<Pair<String, Paired>>()
        if (full != null) {
            for (nm in listOf("− Frequency", "− Pattern", "− Sector", "− Region", "− Neighbor", "− Transition", "− Table", "− Ml", "− Recency")) runs[nm]?.let { abl.add((if (nm == "− Ml") "− ML" else nm) to paired(full, it)) }
            runs["Wheel+Table"]?.let { wt -> runs["Wheel only"]?.let { w -> cf.add("Wheel+Table → Wheel" to paired(wt, w)) } }
            for ((lab, nm) in listOf("ML kapalı" to "− Ml", "Pattern kapalı" to "− Pattern", "Frequency kapalı" to "− Frequency", "Transition kapalı" to "− Transition", "Recent kapalı" to "− Recency", "k1 (FULL: k-oto)" to "k1", "k3 (FULL: k-oto)" to "k3"))
                runs[nm]?.let { cf.add(lab to paired(full, it)) }
            runs["3 aday"]?.let { cf.add("3 aday (kapsama düz.)" to paired(full, it)) }
            runs["4 aday"]?.let { cf.add("4 aday (kapsama düz.)" to paired(full, it)) }
        }
        // parametre hassasiyeti ızgarası
        val grid = ArrayList<List<Double>>()
        var cancelled = cancel?.invoke() == true
        for (w in wins) {
            val row = ArrayList<Double>()
            for (k in ks) {
                if (cancelled || cancel?.invoke() == true) { cancelled = true; row.add(0.0); continue }
                progress?.invoke(done, total, "w$w · k$k")
                row.add(LabRunner.run(values, ts, base.with(hypothesis = "w$w k$k", window = w, k = k), sectors, null, cancel).result.deltaPp)
                done++
            }
            grid.add(row)
        }
        val sens = if (cancelled) null else SensGrid(wins, ks, grid, spikeOf(grid))
        val stress = if (doStress && !cancelled) stress(values, ts, base, sectors) { i, nm -> progress?.invoke(done + i, total, nm) } else null
        progress?.invoke(total, total, "bitti")
        return Suites(full?.result, abl, cf, sens, stress, System.currentTimeMillis())
    }

    // ---- STRES: yapay senaryolar GERÇEK performans olarak raporlanmaz (Prompt §41)
    private fun sticky(n: Int, seed: Long, stay: Double, sec: Sectors = Sectors.DEFAULT): IntArray {
        val r = java.util.Random(seed); val out = IntArray(n); var s = r.nextInt(sec.count)
        for (i in 0 until n) { if (r.nextDouble() > stay) s = r.nextInt(sec.count); out[i] = sec.members(s)[r.nextInt(sec.size(s))] }
        return out
    }
    private fun tsFor(n: Int) = LongArray(n) { 1_700_000_000L + it * 60L }

    fun stress(values: IntArray, ts: LongArray, base: ExpConfig, sectors: Sectors, progress: ((Int, String) -> Unit)?): List<StressRow> {
        val out = ArrayList<StressRow>()
        val quick = base.with(window = min(base.window, 200), features = base.features - "ML")
        fun safe(name: String, what: String, body: () -> String) {
            progress?.invoke(out.size, name)
            try { out.add(StressRow(name, what, body(), true)) } catch (e: Exception) { out.add(StressRow(name, what, "HATA: ${e.message}", false)) }
        }
        val tail = min(values.size, 1500)
        val v0 = values.copyOfRange(values.size - tail, values.size)
        safe("Missing", "%5 spin silindi") {
            val r = java.util.Random(1); val keep = v0.indices.filter { r.nextDouble() > 0.05 }.map { v0[it] }.toIntArray()
            val run = LabRunner.run(keep, tsFor(keep.size), quick, sectors); "çökme yok · n=${keep.size} · sınıf ${run.result.cls}"
        }
        safe("Duplicate", "%3 kayıt yinelendi") {
            val r = java.util.Random(2); val spins = ArrayList<Spin>()
            for (i in v0.indices) { val s = Spin(i.toLong(), v0[i], 1_700_000_000L + i * 60L); spins.add(s); if (r.nextDouble() < 0.03) spins.add(s.copy(id = -1)) }
            val dd = Importer.dedupeExact(spins); "${spins.size - dd.size} / ${spins.size - v0.size} yinelenen yakalandı"
        }
        safe("Short dataset", "n = 200") {
            val v = v0.copyOfRange(0, min(200, v0.size)); val run = LabRunner.run(v, tsFor(v.size), quick, sectors); "“Düşük sample” bayrağı · sınıf ${run.result.cls}"
        }
        safe("Large dataset", "20 000 sentetik spin") {
            val r = java.util.Random(3); val v = IntArray(20000) { r.nextInt(37) }
            val t0 = System.nanoTime(); val rr = ReplayEngine(quick.brain(sectors)).run(v, tsFor(v.size), 19000, 19000)
            "replay ${(System.nanoTime() - t0) / 1_000_000_000L} sn · ${rr.steps.size} adım"
        }
        safe("Zero-heavy", "zero oranı %10") {
            val r = java.util.Random(4); val v = IntArray(v0.size) { if (r.nextDouble() < 0.10) 0 else v0[it] }
            val run = LabRunner.run(v, tsFor(v.size), quick, sectors); "zero oranı ${S.pc(v.count { it == 0 }.toDouble() / v.size * 100, 1)} · tahmin sürdü · sınıf ${run.result.cls}"
        }
        safe("Sector clustering", "yapay kümelenme (kalma olasılığı 0,5)") {
            val v = sticky(1500, 5, 0.5, sectors); val same = (1 until v.size).count { sectors.of[v[it]] == sectors.of[v[it - 1]] }.toDouble() / (v.size - 1)
            val run = LabRunner.run(v, tsFor(v.size), quick, sectors); "persistence ${S.pc(same * 100, 1)} (beklenen %11) · sınıf ${run.result.cls}"
        }
        safe("Sector dispersion", "yapay dağılma (kalma olasılığı 0)") {
            val v = sticky(1500, 6, 0.0, sectors); val run = LabRunner.run(v, tsFor(v.size), quick, sectors); "çökme yok · sınıf ${run.result.cls}"
        }
        safe("Sudden regime change", "#1 000'de ani değişim") {
            val a = IntArray(1000) { java.util.Random(7L + it).nextInt(37) }; val b = sticky(800, 8, 0.6, sectors)
            val v = a + b
            val win = 150; var detect = -1
            for (i in 1000 + 20 until v.size) {
                var c = 0; for (j in i - 40 until i) if (sectors.of[v[j]] == sectors.of[v[j - 1]]) c++
                if (c.toDouble() / 40 > 0.30) { detect = i - 1000; break }
            }
            if (win > 0 && detect >= 0) "değişim tespit edildi · gecikme $detect spin" else throw IllegalStateException("değişim tespit edilemedi")
        }
        return out
    }
}
