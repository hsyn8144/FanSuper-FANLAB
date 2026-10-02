package fan.lightningroulette.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Deney listesi satırı (veritabanından). */
class ExpRow(val id: Long, val code: String, val hypothesis: String, val params: String, val status: String, val cls: String, val deltaPp: Double, val n: Int, val reason: String, val createdAt: Long)

/** Önbelleğe alınabilir koşu özeti (Brain nesnesi içermez): LAB sekmeleri bundan beslenir. */
class RunSnap(
    val result: ExpResult, val steps: List<StepRec>, val oosStart: Int, val valStart: Int,
    val gates: List<Gate>, val processed: Int, val total: Int, val hash: Long,
    val titles: List<String>, val ids: List<String>, val wK: DoubleArray, val wP: DoubleArray?
) {
    val leakFree: Boolean get() = gates.all { it.ok }

    fun toJson(): String = Json.stringify(mapOf(
        "res" to result.toJson(), "steps" to steps.map { StepIO.toList(it) }, "os" to oosStart, "vs" to valStart,
        "gates" to gates.map { listOf(it.name, if (it.ok) 1 else 0, it.detail) }, "proc" to processed, "tot" to total, "hash" to hash.toString(),
        "titles" to titles, "ids" to ids, "wk" to wK.toList(), "wp" to wP?.toList()
    ))

    companion object {
        fun of(r: ExpRun): RunSnap = RunSnap(
            r.result, r.steps, r.oosStart, r.valStart, r.replay.gates, r.replay.processed, r.replay.total, r.replay.hash,
            r.replay.brain.members.map { it.title }, r.replay.brain.members.map { it.id }, r.replay.brain.hedgeK.w.copyOf(), r.replay.brain.hedgeP?.w?.copyOf()
        )

        fun fromJson(s: String): RunSnap? = try {
            val m = Json.parse(s).jmap()
            RunSnap(
                ExpResult.fromJson(m["res"].jstr())!!, m["steps"].jlist().map { StepIO.fromList(it.jlist()) }, m["os"].jint(), m["vs"].jint(),
                m["gates"].jlist().map { val l = it.jlist(); Gate(l[0].jstr(), l[1].jint() == 1, l[2].jstr()) }, m["proc"].jint(), m["tot"].jint(), m["hash"].jstr().toLong(),
                m["titles"].jlist().map { it.jstr() }, m["ids"].jlist().map { it.jstr() }, m["wk"].jdoubles(), m["wp"]?.jdoubles()
            )
        } catch (e: Exception) { null }
    }
}

/** Büyük JSON değerlerini SQLite satır sınırına (≈2 MB CursorWindow) takılmadan saklamak için GZIP + Base64. */
object Pack {
    fun pack(s: String): String {
        val bo = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bo).use { it.write(s.toByteArray(Charsets.UTF_8)) }
        return java.util.Base64.getEncoder().encodeToString(bo.toByteArray())
    }
    fun unpack(s: String): String {
        val raw = java.util.Base64.getDecoder().decode(s)
        return java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(raw)).use { String(it.readBytes(), Charsets.UTF_8) }
    }
}

/** LAB sekmelerinin girdisi. run: canlı yapılandırmayla yapılan genel walk-forward (Python varsa dahil). */
class LabCtx(
    val values: IntArray, val ts: LongArray, val sectors: Sectors, val run: RunSnap, val cfg: ExpConfig,
    val pyIncluded: Boolean, val pyTitles: List<String>, val determinism: Pair<Long, Long>?, val suites: Suites?,
    val experiments: List<ExpRow>, val dataErrors: Int, val datasetLabel: String, val championLabel: String = "v1.0.0"
) {
    val n: Int get() = values.size
    val oos: List<StepRec> by lazy { run.steps.filter { it.i >= run.oosStart } }
    val valSteps: List<StepRec> by lazy { run.steps.filter { it.i < run.oosStart } }
    val memberTitles: List<String> get() = run.titles
    val memberIds: List<String> get() = run.ids
    val c: Int get() = cfg.cands
    val regime: RegimeResult by lazy { An.regimes(this) }
    val phi: Array<DoubleArray> by lazy { An.phiMatrix(this) }
    val robust: RobustScore by lazy {
        val sens = suites?.sens
        Robustness.compute(run.result, sens?.v?.flatten(), sens?.spike != null,
            regime.rows.drop(3).filter { it.n >= 100 }.map { it.deltaPp }, regime.rows.count { it.n < 100 }, regime.periods, An.phiMean(phi), experiments.size + 1)
    }
}

class RegRow(val name: String, val n: Int, val deltaPp: Double, val se: Double)
class RegimeResult(val rows: List<RegRow>, val periods: List<Double>, val strip: List<Double>, val blocks: List<Double>, val slope: Double, val slopeSe: Double, val drift: Boolean)

/** Analizler: yalnızca veriden hesaplanır; hiçbir sayı sabit/uydurma değildir. */
internal object An {
    fun exc(steps: List<StepRec>, c: Int): DoubleArray = DoubleArray(steps.size) { (if (steps[it].candRank in 1..c) 1.0 else 0.0) - c.toDouble() / Wheel.N }
    fun mean(x: DoubleArray) = Stats.mean(x)
    fun se(x: DoubleArray) = if (x.size < 2) 0.0 else Stats.sd(x) / sqrt(x.size.toDouble())
    fun entropyBits(counts: IntArray): Double {
        var t = 0; for (c in counts) t += c; if (t == 0) return 0.0
        var h = 0.0; for (c in counts) if (c > 0) { val p = c.toDouble() / t; h -= p * ln(p) / ln(2.0) }; return h
    }
    fun cyc(a: Int, b: Int, k: Int): Int { val d = abs(a - b); return min(d, k - d) }
    fun sd(a: Int, b: Int, k: Int): Int { var d = ((a - b) % k + k) % k; if (d > k / 2) d -= k; return d }
    fun shuffle(a: IntArray, r: java.util.Random) { for (i in a.size - 1 downTo 1) { val j = r.nextInt(i + 1); val t = a[i]; a[i] = a[j]; a[j] = t } }
    fun median(x: DoubleArray): Double { if (x.isEmpty()) return 0.0; val s = x.sortedArray(); return s[s.size / 2] }

    // ---------------- 15 sektör testi (Prompt §3) — permütasyon temelli, çoklu test düzeltmeli ----------------
    class SecTestRow(val name: String, val delta: Double, val unit: String, val dec: Int, val p: Double, val n: Int, val cls: String)

    private fun topHitExcess(s: IntArray, ps: DoubleArray, window: Int, lam: Double): Double {
        val k = ps.size; val n = s.size
        if (n <= window + 5) return 0.0
        val cnt = DoubleArray(k); var tot = 0.0; var ex = 0.0; var m = 0
        for (i in 0 until n) {
            if (i >= (if (lam > 0) 20 else window)) {
                var b = 0; for (q in 1 until k) if (cnt[q] > cnt[b] + 1e-12) b = q
                ex += (if (s[i] == b) 1.0 else 0.0) - ps[b]; m++
            }
            if (lam > 0) { for (q in 0 until k) cnt[q] *= (1 - lam); cnt[s[i]] += 1.0 }
            else { cnt[s[i]] += 1.0; if (i >= window) cnt[s[i - window]] -= 1.0 }
        }
        return if (m == 0) 0.0 else ex / m
    }
    private fun transitionExcess(s: IntArray, ps: DoubleArray): Double {
        val k = ps.size; val n = s.size; val te = (n * 0.8).toInt()
        val m = Array(k) { IntArray(k) }
        for (i in 1 until te) m[s[i - 1]][s[i]]++
        var ex = 0.0; var c = 0
        for (i in te until n) {
            val row = m[s[i - 1]]; var b = 0; for (q in 1 until k) if (row[q] > row[b]) b = q
            if (row[b] == 0) continue
            ex += (if (s[i] == b) 1.0 else 0.0) - ps[b]; c++
        }
        return if (c == 0) 0.0 else ex / c
    }
    private fun condEntropy(s: IntArray, k: Int): Double {
        val m = Array(k) { IntArray(k) }; for (i in 1 until s.size) m[s[i - 1]][s[i]]++
        var tot = 0; var h = 0.0
        for (r in 0 until k) { val t = m[r].sum(); if (t == 0) continue; tot += t; h += t * entropyBits(m[r]) }
        return if (tot == 0) 0.0 else h / tot
    }
    private fun windowEntropy(s: IntArray, k: Int, w: Int): Double {
        if (s.size <= w) return 0.0
        val c = IntArray(k); var sum = 0.0; var m = 0
        for (i in s.indices) { c[s[i]]++; if (i >= w) c[s[i - w]]--; if (i >= w - 1) { sum += entropyBits(c); m++ } }
        return sum / m
    }

    fun sectorTests(values: IntArray, sec: Sectors, b: Int = 200): List<SecTestRow> {
        val k = sec.count; val n = values.size
        val s = IntArray(n) { sec.of[values[it]] }
        val ps = DoubleArray(k) { sec.baseline(it) }
        class T(val name: String, val scale: Double, val unit: String, val dec: Int, val f: (IntArray) -> Double)
        fun frac(pred: (IntArray, Int) -> Boolean?, from: Int): (IntArray) -> Double = { a ->
            var c = 0; var t = 0
            for (i in from until a.size) { val r = pred(a, i) ?: continue; t++; if (r) c++ }
            if (t == 0) 0.0 else c.toDouble() / t
        }
        val tests = listOf(
            T("recent frequency (w50)", 100.0, " pp", 2) { topHitExcess(it, ps, 50, 0.0) },
            T("persistence", 100.0, " pp", 2, frac({ a, i -> a[i] == a[i - 1] }, 1)),
            T("recurrence (gap 2)", 100.0, " pp", 2, frac({ a, i -> a[i] == a[i - 2] }, 2)),
            T("transition (1. derece)", 100.0, " pp", 2) { transitionExcess(it, ps) },
            T("reversal", 100.0, " pp", 2, frac({ a, i -> if (a[i - 1] != a[i - 2]) a[i] == a[i - 2] else null }, 2)),
            T("alternation", 100.0, " pp", 2, frac({ a, i -> a[i] == a[i - 2] && a[i - 1] == a[i - 3] && a[i] != a[i - 1] }, 3)),
            T("clustering (±1 sektör)", 100.0, " pp", 2, frac({ a, i -> cyc(a[i], a[i - 1], k) <= 1 }, 1)),
            T("dispersion (≥3 sektör)", 100.0, " pp", 2, frac({ a, i -> cyc(a[i], a[i - 1], k) >= 3 }, 1)),
            T("dwell / return time", 1.0, " spin", 2) { a ->
                val last = IntArray(k) { -1 }; var sum = 0.0; var c = 0
                for (i in a.indices) { if (last[a[i]] >= 0) { sum += i - last[a[i]]; c++ }; last[a[i]] = i }
                if (c == 0) 0.0 else sum / c
            },
            T("momentum (yön sürekliliği)", 100.0, " pp", 2, frac({ a, i -> val d1 = sd(a[i - 1], a[i - 2], k); val d2 = sd(a[i], a[i - 1], k); if (d1 != 0 && d2 != 0) (d1 > 0) == (d2 > 0) else null }, 2)),
            T("decay (λ 0,02)", 100.0, " pp", 2) { topHitExcess(it, ps, 0, 0.02) },
            T("entropy (w50)", 1.0, " bit", 3) { windowEntropy(it, k, 50) },
            T("transition entropy", 1.0, " bit", 3) { condEntropy(it, k) }
        )
        val out = ArrayList<SecTestRow>()
        // frequency: kesin ki-kare (permütasyon sayıları değiştirmez)
        run {
            val cnt = IntArray(k); for (x in s) cnt[x]++
            var chi = 0.0; var dev = 0.0
            for (q in 0 until k) { val e = n * ps[q]; chi += (cnt[q] - e) * (cnt[q] - e) / e; dev = max(dev, abs(cnt[q] / n.toDouble() - ps[q]) * 100) }
            val p = Stats.chiSqP(chi, k - 1)
            out.add(SecTestRow("frequency", dev, " pp", 2, p, n, cls(n, p)))
        }
        val rnd = java.util.Random(77L)
        /** İki aşamalı permütasyon: 200 ile tara; umut veren (p < 0,03) testi 3 000 ile yeniden ölç (Bonferroni için çözünürlük). */
        fun permP(f: (IntArray) -> Double, src: IntArray, obs: Double): DoubleArray {
            var nm = 0.0; var p = 1.0
            for (stage in 0..1) {
                val bb = if (stage == 0) b else 3000
                val tmp = src.copyOf(); val nulls = DoubleArray(bb)
                for (r in 0 until bb) { shuffle(tmp, rnd); nulls[r] = f(tmp) }
                nm = Stats.mean(nulls); var ge = 0; for (x in nulls) if (abs(x - nm) >= abs(obs - nm) - 1e-12) ge++
                p = (ge + 1.0) / (bb + 1)
                if (p >= 0.03) break
            }
            return doubleArrayOf(nm, p)
        }
        for (t in tests) {
            val obs = t.f(s)
            val (nm, p) = permP(t.f, s, obs).let { it[0] to it[1] }
            out.add(SecTestRow(t.name, (obs - nm) * t.scale, t.unit, t.dec, p, n, cls(n, p)))
        }
        // zero ilişkisi (ham sayılar üzerinde)
        run {
            val z = sec.of[0]
            fun st(a: IntArray): Double { var c = 0; var t = 0; for (i in 1 until a.size) if (a[i - 1] == 0) { t++; if (sec.of[a[i]] == z) c++ }; return if (t == 0) 0.0 else c.toDouble() / t }
            val obs = st(values); val (nm, p) = permP(::st, values, obs).let { it[0] to it[1] }
            val n0 = values.count { it == 0 }
            out.add(SecTestRow("zero ilişkisi (S1)", (obs - nm) * 100, " pp", 2, p, n0, cls(n0, p)))
        }
        return out
    }
    /** n < 300 → S; Bonferroni (15 test): p < 0,0033 → B (aday, kanıt değil); aksi halde C. */
    private fun cls(n: Int, p: Double): String = if (n < 300) "S" else if (p < 0.05 / 15) "B" else "C"

    // ---------------- Desen madenciliği (Prompt §7) ----------------
    class PatRow(val family: String, val name: String, val trainN: Int, val trainRate: Double, val base: Double, val oosN: Int, val oosRate: Double, val status: String)
    class FamRow(val family: String, val minedPatterns: Int, val held: Int, val meanOosDelta: Double, val note: String)

    class Family(val name: String, val sym: IntArray, val classes: Int, val len: Int, val baseOf: (Int) -> Double)

    fun families(values: IntArray, sec: Sectors): List<Family> {
        val n = values.size
        fun sym(f: (Int) -> Int) = IntArray(n) { f(values[it]) }
        val offB = intArrayOf(12, 6, 1, 6, 12)
        val offs = IntArray(n) { if (it == 0) 2 else { val o = Wheel.signedOffset(values[it - 1], values[it]); if (o <= -7) 0 else if (o < 0) 1 else if (o == 0) 2 else if (o <= 6) 3 else 4 } }
        fun tb(cat: Int, z: Int): (Int) -> Double = { c -> if (c >= TableCats.classes(cat)) 1.0 / Wheel.N else TableCats.baseline(cat) }
        return listOf(
            Family("Exact · kısa (L2)", values, 37, 2) { 1.0 / Wheel.N },
            Family("Exact · orta (L3)", values, 37, 3) { 1.0 / Wheel.N },
            Family("Sector (L2)", sym { sec.of[it] }, sec.count, 2) { sec.baseline(it) },
            Family("Region (L3)", sym { Regions.of[it] }, 3, 3) { Regions.size(it).toDouble() / Wheel.N },
            Family("Wheel · ofset (L3)", offs, 5, 3) { offB[it].toDouble() / Wheel.N },
            Family("Color (L4)", sym { Wheel.color(it) }, 3, 4) { if (it == 0) 1.0 / Wheel.N else 18.0 / Wheel.N },
            Family("Parity (L4)", sym { if (it == 0) 2 else it % 2 }, 3, 4, tb(1, 2)),
            Family("High/Low (L4)", sym { if (it == 0) 2 else if (it <= 18) 0 else 1 }, 3, 4, tb(2, 2)),
            Family("Dozen (L3)", sym { if (it == 0) 3 else (it - 1) / 12 }, 4, 3, tb(3, 3)),
            Family("Column (L3)", sym { if (it == 0) 3 else (it - 1) % 3 }, 4, 3, tb(4, 3)),
            Family("Karma color+parity (L2)", sym { if (it == 0) 4 else (if (Wheel.RED[it]) 0 else 2) + (it % 2) }, 5, 2) { if (it == 4) 1.0 / Wheel.N else 9.0 / Wheel.N }
        )
    }

    fun minePatterns(values: IntArray, sec: Sectors, trainEnd: Int, minN: Int = 30): Pair<List<FamRow>, List<PatRow>> {
        val n = values.size; val fams = ArrayList<FamRow>(); val rows = ArrayList<PatRow>()
        for (f in families(values, sec)) {
            val cl = f.classes; val L = f.len
            fun key(i: Int): Long { var k = 0L; for (q in i - L until i) k = k * cl + f.sym[q]; return k }
            val cnt = HashMap<Long, IntArray>()
            for (i in L until trainEnd) cnt.getOrPut(key(i)) { IntArray(cl) }[f.sym[i]]++
            class P(val key: Long, val best: Int, val total: Int, val rate: Double, val base: Double, val z: Double)
            val cand = ArrayList<P>()
            for ((k, c) in cnt) {
                val t = c.sum(); if (t < minN) continue
                var b = 0; for (q in 1 until cl) if (c[q] > c[b]) b = q
                val base = f.baseOf(b); val rate = c[b].toDouble() / t
                if (base <= 0.0 || base >= 1.0) continue
                cand.add(P(k, b, t, rate, base, (rate - base) / sqrt(base * (1 - base) / t)))
            }
            var held = 0; var dsum = 0.0
            for (p in cand) {
                var on = 0; var hit = 0
                for (i in max(trainEnd, L) until n) if (key(i) == p.key) { on++; if (f.sym[i] == p.best) hit++ }
                if (on >= 30 && (hit.toDouble() / on - p.base) * 100 >= (p.rate - p.base) * 50 && p.rate > p.base) held++
                if (on > 0) dsum += (hit.toDouble() / on - p.base) * 100
            }
            fams.add(FamRow(f.name, cand.size, held, if (cand.isEmpty()) 0.0 else dsum / cand.size, if (cand.isEmpty()) "örnek yetersiz (n ≥ $minN desen yok)" else ""))
            for (p in cand.sortedByDescending { it.z }.take(2)) {
                var on = 0; var hit = 0
                for (i in max(trainEnd, L) until n) if (key(i) == p.key) { on++; if (f.sym[i] == p.best) hit++ }
                val orate = if (on == 0) 0.0 else hit.toDouble() / on
                val tl = (p.rate - p.base) * 100; val ol = (orate - p.base) * 100
                val st = when { on < 30 -> "Düşük sample"; tl >= 3 && ol <= 0 -> "Overfit şüphesi"; ol < tl / 2 -> "OOS’ta tutmadı"; else -> "OOS’ta tutarlı*" }
                var code = ""; var kk = p.key; val digs = IntArray(L); for (q in L - 1 downTo 0) { digs[q] = (kk % cl).toInt(); kk /= cl }
                code = digs.joinToString("→") { d -> d.toString() }
                rows.add(PatRow(f.name, "$code ⇒ ${p.best}", p.total, p.rate * 100, p.base * 100, on, orate * 100, st))
            }
        }
        return fams to rows
    }

    // ---------------- Markov geçişleri (Prompt §9) ----------------
    class MkCell(val delta: Double, val states: Int, val trans: Int, val h: Double, val coverage: Double, val insufficient: Boolean)
    fun markov(sym: IntArray, classes: Int, degree: Int, trainEnd: Int, baseOf: (Int) -> Double): MkCell {
        val n = sym.size
        val states = Math.pow(classes.toDouble(), degree.toDouble()).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        fun key(i: Int): Int { var k = 0; for (q in i - degree until i) k = k * classes + sym[q]; return k }
        val cnt = HashMap<Int, IntArray>()
        var trans = 0
        for (i in degree until trainEnd) { cnt.getOrPut(key(i)) { IntArray(classes) }[sym[i]]++; trans++ }
        var h = 0.0; for ((_, c) in cnt) h += c.sum() * entropyBits(c); h = if (trans == 0) 0.0 else h / trans
        var ex = 0.0; var m = 0; var tot = 0
        for (i in max(trainEnd, degree) until n) {
            tot++
            val c = cnt[key(i)] ?: continue
            var b = 0; for (q in 1 until classes) if (c[q] > c[b]) b = q
            ex += (if (sym[i] == b) 1.0 else 0.0) - baseOf(b); m++
        }
        return MkCell(if (m == 0) 0.0 else ex / m * 100, states, trans, h, if (tot == 0) 0.0 else m.toDouble() / tot, states.toLong() * 20 > trans)
    }

    // ---------------- Rejimler (Prompt §30) — özellikler yalnızca adımdan ÖNCEKİ veriden ----------------
    fun regimes(c: LabCtx): RegimeResult {
        val oos = c.oos; val k = c.sectors.count; val v = c.values
        if (oos.size < 30) return RegimeResult(emptyList(), emptyList(), emptyList(), emptyList(), 0.0, 1.0, false)
        val s = IntArray(v.size) { c.sectors.of[v[it]] }
        val ex = exc(oos, c.c)
        val m = oos.size
        val clus = DoubleArray(m); val hent = DoubleArray(m); val zero = IntArray(m); val conc = DoubleArray(m)
        for ((j, st) in oos.withIndex()) {
            val i = st.i
            var cc = 0; var tt = 0; for (q in max(2, i - 100) until i) { tt++; if (cyc(s[q], s[q - 1], k) <= 1) cc++ }
            clus[j] = if (tt == 0) 0.0 else cc.toDouble() / tt
            val cnt = IntArray(k); for (q in max(0, i - 100) until i) cnt[s[q]]++; conc[j] = entropyBits(cnt)
            var z = 0; for (q in max(0, i - 100) until i) if (v[q] == 0) z++; zero[j] = z
            val sub = s.copyOfRange(max(0, i - 300), i); hent[j] = if (sub.size < 30) 0.0 else condEntropy(sub, k)
        }
        val mc = median(clus); val mh = median(hent); val mn = median(conc)
        fun row(name: String, sel: (Int) -> Boolean): RegRow {
            val idx = (0 until m).filter(sel); val x = DoubleArray(idx.size) { ex[idx[it]] }
            return RegRow(name, idx.size, mean(x) * 100, se(x) * 100)
        }
        val third = m / 3
        val rows = listOf(
            row("Kısa geçmiş (OOS ilk ⅓)") { it < third }, row("Orta geçmiş (OOS orta ⅓)") { it in third until 2 * third }, row("Uzun geçmiş (OOS son ⅓)") { it >= 2 * third },
            row("Sector clustering (üst yarı)") { clus[it] > mc }, row("Sector dispersion (alt yarı)") { clus[it] <= mc },
            row("Yüksek geçiş entropisi") { hent[it] > mh }, row("Düşük geçiş entropisi") { hent[it] <= mh },
            row("Zero-heavy (son 100’de ≥ 4)") { zero[it] >= 4 }, row("Zero-light (son 100’de ≤ 1)") { zero[it] <= 1 },
            row("Concentrated (düşük sektör entropisi)") { conc[it] <= mn }, row("Dispersed (yüksek sektör entropisi)") { conc[it] > mn }
        )
        val periods = rows.take(3).map { it.deltaPp }
        // şerit: 20'şer adımlık blok ortalama clustering z-skoru → −1/0/+1
        val sdc = Stats.sd(clus).let { if (it == 0.0) 1.0 else it }
        val strip = ArrayList<Double>(); var j = 0
        while (j < m) { val e = min(m, j + 20); var a = 0.0; for (q in j until e) a += clus[q]; a /= (e - j); val z = (a - Stats.mean(clus)) / sdc; strip.add(if (z > 0.5) 1.0 else if (z < -0.5) -1.0 else 0.0); j = e }
        val blocks = ArrayList<Double>(); j = 0
        while (j + 100 <= m) { var a = 0.0; for (q in j until j + 100) a += ex[q]; blocks.add(a / 100 * 100); j += 100 }
        if (j < m && m - j >= 50) { var a = 0.0; for (q in j until m) a += ex[q]; blocks.add(a / (m - j) * 100) }
        val tr = Stats.trend(blocks.toDoubleArray())
        val drift = blocks.size >= 4 && tr[1] > 0 && tr[0] / tr[1] < -2.0 && tr[0] * (blocks.size - 1) < -1.0
        return RegimeResult(rows, periods, strip, blocks, tr[0], tr[1], drift)
    }

    /** Kotlin üyeleri arası ortalama |φ| (hata örtüşmesi) ve matris. */
    fun phiMatrix(c: LabCtx): Array<DoubleArray> {
        val o = c.oos; val mm = if (o.isEmpty()) 0 else o[0].memberTop5Hit.size
        val miss = Array(mm) { a -> BooleanArray(o.size) { !o[it].memberTop5Hit[a] } }
        return Array(mm) { a -> DoubleArray(mm) { b -> if (a == b) 1.0 else Stats.phi(miss[a], miss[b]) } }
    }
    fun phiMean(m: Array<DoubleArray>): Double {
        var s = 0.0; var c = 0
        for (a in m.indices) for (b in a + 1 until m.size) { s += abs(m[a][b]); c++ }
        return if (c == 0) 0.0 else s / c
    }
}

object LabTabs {
    val TABS = listOf("overview" to "Overview", "models" to "Models", "side" to "Side/Table", "wheel" to "Wheel", "sectors" to "Sectors", "neighbors" to "Neighbors", "patterns" to "Patterns",
        "transitions" to "Transitions", "replay" to "Replay", "calibration" to "Calibration", "diversity" to "Diversity", "regime" to "Regime", "counterfactual" to "Counterfactual",
        "ablation" to "Ablation", "robustness" to "Robustness", "experiments" to "Experiments")
    val ROB_TABS = listOf("score" to "Skor", "sens" to "Hassasiyet", "stress" to "Stres", "err" to "Hata matrisi")

    private fun pp(x: Double, d: Int = 1) = S.sg(x, d, " pp")
    private fun tone(x: Double) = if (x > 0.05) "ok" else if (x < -0.05) "bad" else ""
    private fun few(c: LabCtx, min: Int = 100): List<Sec>? =
        if (c.n < min || c.oos.size < 30) listOf(S.text("Yetersiz veri", "Bu sekme için en az $min spin ve 30 OOS adımı gerekir (şu an ${c.n} spin / ${c.oos.size} OOS adımı). Veri → Başlangıç verisi ile örnek veri seti yükleyebilirsin.", "warn")) else null

    fun build(c: LabCtx, tab: String, sub: String = ""): List<Sec> {
        few(c)?.let { return it }
        return when (tab) {
            "overview" -> overview(c); "models" -> models(c); "side" -> side(c); "wheel" -> wheel(c); "sectors" -> sectors(c); "neighbors" -> neighbors(c)
            "patterns" -> patterns(c); "transitions" -> transitions(c); "replay" -> replay(c); "calibration" -> calibration(c); "diversity" -> diversity(c); "regime" -> regime(c)
            "counterfactual" -> counterfactual(c); "ablation" -> ablation(c); "robustness" -> robustness(c, sub.ifEmpty { "score" }); "experiments" -> experiments(c)
            else -> emptyList()
        }
    }

    fun warnFlags(c: LabCtx): List<String> {
        val r = c.run.result; val out = ArrayList<String>()
        if (!r.leakFree) out.add("Leakage şüphesi: ${Codes.LEAK}")
        if (r.ciLo <= 0.0 && r.ciHi >= 0.0) out.add("Baseline’dan anlamlı ayrışma yok.")
        if (r.deltaPp <= 0) out.add("OOS performansı tabanı geçmiyor.")
        if (c.suites?.sens == null) out.add("Parameter stability yok (hassasiyet taraması çalıştırılmadı).")
        else if (c.suites.sens.spike != null) out.add("OVERFIT RISK: başarı tek parametre noktasında.")
        if (r.n < 300 || c.regime.rows.any { it.n < 100 }) out.add("Düşük sample. (bazı rejimlerde n < 100)")
        if (r.ece > 0.05) out.add("Calibration zayıf (ECE ${S.f(r.ece, 3)}).")
        if (c.regime.drift) out.add("MODEL DRIFT: isabet trendi düşüyor.")
        out.add("Yüksek hit oranı tek başına avantaj kanıtı değildir.")
        return out
    }

    // ───────── Overview
    private fun overview(c: LabCtx): List<Sec> {
        val r = c.run.result; val rs = c.robust; val seg = LabRunner.segmentLen(c.n)
        val last = c.experiments.firstOrNull()
        val tiles = S.tiles(listOf(
            listOf("Dataset", S.th(c.n), "${c.datasetLabel} · ${c.n - 2 * seg} / $seg / $seg", ""),
            listOf("Güncel model", c.championLabel, "Champion", ""),
            listOf("Son deney", last?.code?.takeLast(10) ?: "—", if (last == null) "henüz deney yok" else "${last.hypothesis.take(22)} · sınıf ${last.cls}", ""),
            listOf("Robustness", "${rs.score} / 100", "sınıf ${rs.cls} · garanti değildir", if (rs.score < 40) "warn" else "")))
        val ms = r.metrics
        val rg = max(2.0, Math.ceil(ms.maxOf { max(abs(it.lo - it.base), abs(it.hi - it.base)) }) + 1)
        val fo = S.forest("OOS · TABANDAN FARK (pp, %95 CI) · n = ${r.n}", ms.map { it.name }, ms.map { it.delta }, ms.map { it.lo - it.base }, ms.map { it.hi - it.base }, rg,
            "● gözlenen fark · çizgi = %95 CI · sarı dikey = tesadüf tabanı. Exact ve Candidate ayrı satırlardır.")
        val total = c.experiments.size + (if (c.suites != null) 17 else 0)
        val st = S.kv("İSTATİSTİK ÖZETİ", listOf(
            listOf("Bootstrap", "1 000 örnek · %95 CI", "", ""),
            listOf("Permütasyon (Candidate-${c.c})", "p = ${S.f(r.permP, 3)}", "", ""),
            listOf("Aranan deney sayısı", S.th(total), "", "data-snooping: en iyi sonuç kanıt sayılmaz"),
            listOf("Sınıf (A–F, O, L, S)", rs.cls, "", "Overview sınıfı robustness skoru ile birlikte okunur")))
        return listOf(tiles, fo, st, S.flags("UYARI BAYRAKLARI", warnFlags(c)))
    }

    // ───────── Models
    private fun memberClass(delta: Double, se: Double, n: Int): String { val z = if (se > 0) delta / se else 0.0; return if (n < 300) "S" else if (z >= 3) "B" else if (z <= -3) "D" else "C" }
    private fun models(c: LabCtx): List<Sec> {
        val o = c.oos; val n = o.size; val base = 5.0 / Wheel.N
        val rows = ArrayList<List<String>>(); val wK = c.run.wK
        for ((j, t) in c.memberTitles.withIndex()) {
            val hit = o.count { it.memberTop5Hit[j] }.toDouble() / n; val d = (hit - base) * 100; val se = sqrt(base * (1 - base) / n) * 100
            val ls = Stats.mean(DoubleArray(n) { ln(max(o[it].memberPA[j], 1e-12) * Wheel.N) })
            rows.add(listOf(t, pp(d), S.sg(ls, 4, ""), S.pc(wK.getOrElse(j) { 0.0 } * 100, 0), memberClass(d, se, n)))
        }
        val out = ArrayList<Sec>()
        out.add(S.table("🔵 KOTLIN MECLİSİ", listOf("Model", "Δ top-5", "Log-skor", "Ağırlık", "Sınıf"), rows, "Hedef: gerçek sayının modelin ilk 5 sayısında olması (komşusuz); taban 5/37. Log-skor: ln(37·p) ortalaması.", al = "lrrrc"))
        val py = o.firstOrNull()?.pyTop5Hit
        if (py != null && c.pyIncluded) {
            val wP = c.run.wP
            val prow = ArrayList<List<String>>()
            for (j in py.indices) {
                val hit = o.count { it.pyTop5Hit!![j] }.toDouble() / n; val d = (hit - base) * 100; val se = sqrt(base * (1 - base) / n) * 100
                val ls = Stats.mean(DoubleArray(n) { ln(max(o[it].pyPA!![j], 1e-12) * Wheel.N) })
                prow.add(listOf(c.pyTitles.getOrElse(j) { "Python #${j + 1}" }, pp(d), S.sg(ls, 4, ""), S.pc((wP?.getOrNull(j) ?: 0.0) * 100, 0), memberClass(d, se, n)))
            }
            out.add(S.table("🐍 PYTHON MECLİSİ", listOf("Model", "Δ top-5", "Log-skor", "Ağırlık", "Sınıf"), prow, al = "lrrrc"))
        } else out.add(S.text("🐍 PYTHON MECLİSİ", "Bu LAB koşusuna Python meclisi dahil edilmedi (kapalı/hazır değil). Kotlin meclisi tek başına çalıştı.", "info"))
        // feature grupları: üyeler + tablo kategorileri (ayrı test)
        val fg = ArrayList<List<String>>()
        fun add(name: String, d: Double) = fg.add(listOf(name, pp(d), tone(d), ""))
        val id = c.memberIds
        fun mem(key: String, label: String) { val j = id.indexOf(key); if (j >= 0) add(label, (o.count { it.memberTop5Hit[j] }.toDouble() / n - base) * 100) }
        mem("kml", "raw history (ML)"); mem("wheel", "wheel"); mem("neighbor", "neighbors"); mem("sector", "sector"); mem("region", "region"); mem("frequency", "frequency"); mem("transition", "transition"); mem("pattern", "pattern")
        for ((q, nm) in listOf("Color", "Parity", "High/Low", "Dozen", "Column").withIndex()) add(nm.lowercase(), (o.count { it.tableHit[q] }.toDouble() / n - TableCats.baseline(q)) * 100)
        c.suites?.ablation?.firstOrNull { it.first.contains("Recency") }?.let { add("recency/decay (çıkarınca)", it.second.delta) }
        out.add(S.kv("FEATURE GRUPLARI · ayrı test (Δ, pp)", fg, "Sadece o grup değerlendirildiğinde tabandan fark."))
        out.add(S.text("", "Aynı hatayı yapan modeller bağımsız kanıt sayılmaz — bkz. Diversity.", "info"))
        return out
    }

    // ───────── Side / Table
    private fun side(c: LabCtx): List<Sec> {
        val r = c.run.result; val o = c.oos; val n = o.size
        val names = listOf("Color", "Parity", "High/Low", "Dozen", "Column")
        val ms = names.mapNotNull { nm -> r.metrics.firstOrNull { it.name == nm } }
        val f1 = S.forest("TABLE KATEGORİLERİ · TABANDAN FARK (pp)", ms.map { it.name }, ms.map { it.delta }, ms.map { it.lo - it.base }, ms.map { it.hi - it.base }, 6.0, "Table, sayı tahmininden bağımsız modellerle ölçülür; sıfır hiçbir sınıfa girmez.")
        fun tableAvg(l: List<StepRec>) = if (l.isEmpty()) 0.0 else l.sumOf { s -> (0 until 5).sumOf { q -> (if (s.tableHit[q]) 1.0 else 0.0) - TableCats.baseline(q) } / 5 } / l.size * 100
        fun wheelD(l: List<StepRec>) = if (l.isEmpty()) 0.0 else (l.count { it.candRank in 1..c.c }.toDouble() / l.size - c.c.toDouble() / Wheel.N) * 100
        val p5m = An.median(DoubleArray(n) { o[it].p5 }); val tpm = An.median(DoubleArray(n) { o[it].tableP.average() })
        fun wHi(s: StepRec) = s.p5 >= p5m
        fun tHi(s: StepRec) = s.tableP.average() >= tpm
        fun agree(s: StepRec) = s.centers.isNotEmpty() && TableCats.classOf(0, s.centers[0]) >= 0 && TableCats.classOf(0, s.centers[0]) == s.tableCls[0]
        val zeroAfter = o.filter { it.i > 0 && c.values[it.i - 1] == 0 }
        val sc = listOf("Wheel only" to o, "Table only" to o, "Wheel + Table" to o,
            "Wheel yüksek / Table düşük" to o.filter { wHi(it) && !tHi(it) }, "Wheel düşük / Table yüksek" to o.filter { !wHi(it) && tHi(it) },
            "İki yüksek" to o.filter { wHi(it) && tHi(it) }, "İki düşük" to o.filter { !wHi(it) && !tHi(it) },
            "Agreement (renk)" to o.filter { agree(it) }, "Disagreement (renk)" to o.filter { !agree(it) }, "Zero sonrası" to zeroAfter)
        val rows = sc.map { (nm, l) ->
            val w = if (nm == "Table only") "—" else pp(wheelD(l)); val t = if (nm == "Wheel only") "—" else pp(tableAvg(l))
            listOf(nm, S.th(l.size), w, t, if (l.size < 100) "Düşük sample" else "")
        }
        val dim = sc.mapIndexedNotNull { i, p -> if (p.second.size < 100) i else null }
        val t2 = S.table("WHEEL + TABLE SENARYOLARI", listOf("Durum", "N", "Wheel Δ", "Table Δ", ""), rows, "Wheel Δ = Candidate-${c.c} farkı; Table Δ = 5 kategorinin ortalama farkı. “Yüksek/düşük” = medyana göre.", dim = dim, al = "lrrrl")
        val wc = o.filter { it.candRank in 1..c.c }; val tc = o.filter { s -> s.tableHit.count { it } >= 3 }
        val twoHi = o.filter { wHi(it) && tHi(it) }; val mixed = o.filter { wHi(it) != tHi(it) }
        fun tcRate(l: List<StepRec>) = if (l.isEmpty()) 0.0 else l.sumOf { s -> s.tableHit.count { it } }.toDouble() / (l.size * 5) * 100
        val kv = S.kv("KOŞULLU ÖLÇÜM", listOf(
            listOf("Wheel doğru → Table doğru (ort. %)", "${S.pc(tcRate(wc))}  n=${wc.size}", "", ""),
            listOf("Table ≥3/5 doğru → Wheel (cand-${c.c})", "${S.pc(if (tc.isEmpty()) 0.0 else tc.count { it.candRank in 1..c.c }.toDouble() / tc.size * 100)}  n=${tc.size}", "", ""),
            listOf("İki yüksek güven → Table (ort. %)", "${S.pc(tcRate(twoHi))}  n=${twoHi.size}", "", ""),
            listOf("Biri yüksek / biri düşük → Table (ort. %)", "${S.pc(tcRate(mixed))}  n=${mixed.size}", "", "")))
        val phi = Stats.phi(BooleanArray(n) { o[it].candRank !in 1..c.c }, BooleanArray(n) { o[it].tableHit.count { h -> h } < 3 })
        val note = S.text("", "Wheel–Table hata korelasyonu φ = ${S.f(phi, 2)}. Table yalnızca Wheel bilgisini tekrar ediyorsa bağımsız teyit sayılmaz; baseline’dan anlamlı ayrışma ayrıca aranır.", "info")
        return listOf(f1, t2, kv, note)
    }

    // ───────── Wheel
    private fun wheel(c: LabCtx): List<Sec> {
        val v = c.values; val n = v.size; val pairs = n - 1
        val cnt = IntArray(19); for (i in 1 until n) cnt[Wheel.circ(v[i - 1], v[i])]++
        val exp = List(19) { d -> if (d == 0) pairs / 37.0 else pairs * 2 / 37.0 }
        val hist = S.bars("DAİRESEL MESAFE · ardışık spinler (0–18)", List(19) { it.toString() }, cnt.map { it.toDouble() }, exp, "", "sarı çizgi: beklenen sayı (d=0: ${S.f(exp[0], 0)}, d≥1: ${S.f(exp[1], 0)}); gözlenen d=0: ${cnt[0]}")
        val kr = (1..3).map { k -> val hit = (1 until n).count { Wheel.circ(v[it - 1], v[it]) <= k }.toDouble() / pairs * 100; val b = (2 * k + 1) * 100.0 / 37; listOf("k$k (↔)", "${2 * k + 1}", S.pc(hit), S.pc(b), pp(hit - b)) }
        val kk = S.table("k1 / k2 / k3 İSABETİ", listOf("Aralık", "Cep", "Gözlenen", "Taban", "Fark"), kr, "Bir sonraki sayı, son sayının k komşu aralığında mı? Taban = aralıktaki cep sayısı / 37.", al = "lrrrr")
        val last = v.copyOfRange(max(0, n - 200), n); val dens = IntArray(37); for (x in last) dens[Wheel.POS[x]]++
        val heat = S.heat("YEREL YOĞUNLUK · son ${last.size} spin (çark sırası, saat yönü →)", listOf("sıklık"), Wheel.ORDER.map { it.toString() }, listOf(dens.map { it.toDouble() }), 0.0, max(1, dens.max()).toDouble(), false, 0,
            note = "Koyu = az, açık = çok. “Sık geldi” tek başına gerekçe değildir.")
        val zr = ArrayList<List<String>>()
        for (j in 1..3) { var c1 = 0; var sum = 0.0; for (i in 0 until n - j) if (v[i] == 0) { c1++; sum += Wheel.circ(0, v[i + j]) }
            zr.add(listOf("+$j spin", "$c1", if (c1 == 0) "—" else S.f(sum / c1, 2), S.f(342.0 / 37, 2))) }
        val zc = v.count { it == 0 }
        val zero = S.table("ZERO ANALİZİ", listOf("Zero sonrası", "N", "Ort. mesafe (zero’dan)", "Beklenen"), zr, al = "lrrr")
        val zf = S.kv("", listOf(listOf("zero frekansı", S.pc(zc * 100.0 / n, 2), "", "beklenen ${S.pc(100.0 / 37, 2)} · n=$zc")))
        return listOf(hist, kk, heat, zero, zf)
    }

    // ───────── Sectors
    private fun sectors(c: LabCtx): List<Sec> {
        val sec = c.sectors; val k = sec.count; val v = c.values; val n = v.size
        val cnt = IntArray(k); for (x in v) cnt[sec.of[x]]++
        val bars = S.bars("SEKTÖR FREKANSI · boyut düzeltmeli", List(k) { "S${it + 1}" }, cnt.map { it * 100.0 / n }, List(k) { sec.baseline(it) * 100 }, "%", "Gözlenen % (çubuk) ve beklenen % (sarı çizgi). S1 5 cepli olduğu için beklenen daha yüksektir.")
        val m = Array(k) { IntArray(k) }; for (i in 1 until n) m[sec.of[v[i - 1]]][sec.of[v[i]]]++
        val pct = List(k) { r -> val t = max(1, m[r].sum()); List(k) { q -> m[r][q] * 100.0 / t } }
        val lo = pct.minOf { it.min() }; val hi = pct.maxOf { it.max() }
        val heat = S.heat("SEKTÖR → SEKTÖR GEÇİŞ MATRİSİ (satır: önceki, sütun: sonraki, %)", List(k) { "S${it + 1}" }, List(k) { "S${it + 1}" }, pct, lo, hi, false, 0, note = "Beklenen ≈ sütuna göre %${S.f(sec.baseline(1) * 100, 1)}–%${S.f(sec.baseline(0) * 100, 1)}.")
        val tests = An.sectorTests(v, sec)
        val rows = tests.map { listOf(it.name, S.sg(it.delta, it.dec, it.unit), S.f(it.p, 3), it.cls) }
        val tt = S.table("TESTLER · permütasyon (200) · çoklu test düzeltmeli", listOf("Test", "Δ", "p", "Sınıf"), rows, "15 testte p < 0,0033 (Bonferroni) olmadıkça B verilmez; n < 300 → S.", al = "lrrc")
        return listOf(bars, heat, tt, S.text("", "“Sık geldi” tek başına tahmin gerekçesi kabul edilmez.", "warn"))
    }

    // ───────── Neighbors
    private fun neighbors(c: LabCtx): List<Sec> {
        val o = c.oos; val n = o.size
        val rows = ArrayList<List<String>>()
        for (k in 1..3) for (d in listOf(Dir.BI, Dir.L, Dir.R)) {
            var hit = 0; for (s in o) if (s.centers.isNotEmpty() && Wheel.span(s.centers[0], k, d).contains(s.actual)) hit++
            val size = if (d == Dir.BI) 2 * k + 1 else k + 1; val b = size * 100.0 / 37; val ob = hit * 100.0 / n
            rows.add(listOf("k$k ${if (d == Dir.BI) "↔" else d.code}", "$size", S.pc(ob), S.pc(b), pp(ob - b)))
        }
        val kd = S.table("k × YÖN TABLOSU · en üst adayın merkezi", listOf("Aralık", "Cep", "Gözlenen", "Taban", "Fark"), rows, "↔ iki yön · L saat yönünün tersi · R saat yönü. Taban = aralıktaki cep sayısı / 37.", al = "lrrrr")
        val ms = c.run.result.metrics
        val cr = ArrayList<List<String>>()
        for (cc in 3..5) ms.firstOrNull { it.name == "Candidate-$cc" }?.let { m -> cr.add(listOf("$cc aday", S.pc(m.obs), S.pc(m.base), pp(m.delta), "[${S.f(m.lo - m.base)}; ${S.f(m.hi - m.base)}]")) }
        val avgCov = if (n == 0) 0.0 else o.sumOf { it.coverage }.toDouble() / n
        val ct = S.table("ADAY SAYISI (3 / 4 / 5)", listOf("Aday", "Gözlenen", "Kapsama tabanı", "Fark", "%95 CI"), cr, "Ortalama kapsama: ${S.f(avgCov, 1)} cep / 37 (komşular dahil). Taban = kapsama / 37.", al = "lrrrr")
        val nb = ms.firstOrNull { it.name == "Neighbor" }
        val nh = S.kv("NEIGHBOR HIT", listOf(listOf("Gözlenen", S.pc(nb?.obs ?: 0.0), "", "sonuç herhangi bir adayın komşu aralığında"), listOf("Kapsama düzeltmeli taban", S.pc(nb?.base ?: 0.0), "", "birleşik kapsama / 37"), listOf("Fark", pp(nb?.delta ?: 0.0), tone(nb?.delta ?: 0.0), "")))
        return listOf(kd, ct, nh, S.text("", "“5 aday daha çok isabet ediyor” tek başına başarı değildir; kapsama da büyüdü.", "warn"))
    }

    // ───────── Patterns
    private fun patterns(c: LabCtx): List<Sec> {
        val (fams, rows) = An.minePatterns(c.values, c.sectors, c.run.oosStart)
        val ft = S.table("DESEN AİLELERİ · eğitim/OOS", listOf("Aile", "Desen (n ≥ 30)", "OOS’ta tutan", "Ort. OOS Δ"), fams.map { listOf(it.family, "${it.minedPatterns}", "${it.held}", if (it.minedPatterns == 0) "—" else pp(it.meanOosDelta)) },
            "Desenler yalnızca EĞİTİM bölümünde bulunur; OOS ayrı ölçülür. Boş aile = örnek yetersiz.", al = "lrrr")
        val pr = rows.map { listOf("${it.family} · ${it.name}", "${it.trainN}", S.pc(it.trainRate), S.pc(it.base), "${it.oosN}", S.pc(it.oosRate), it.status) }
        val dim = rows.mapIndexedNotNull { i, r -> if (r.status != "OOS’ta tutarlı*") i else null }
        val pt = S.table("BULUNAN DESENLER · in-sample vs OOS", listOf("Desen", "n", "Eğitim %", "Taban %", "OOS n", "OOS %", "Durum"), pr, "Sonraki sonuç oranı = desen sonrası en sık gelen sınıfın oranı. * Tutarlı görünen bile tek başına kanıt değildir (çoklu test).", dim = dim, al = "lrrrrrl")
        return listOf(ft, pt, S.text("Geçmiş ≠ gelecek", "Desen geçmişte bulundu diye predictive kabul edilmez; desen sonrası sonuç, baseline ve OOS performansı hesaplanır. In-sample ile OOS isabeti arasındaki fark çöküyorsa desen şanstır.", "info"))
    }

    // ───────── Transitions
    private fun transitions(c: LabCtx): List<Sec> {
        val v = c.values; val n = v.size; val te = c.run.oosStart; val sec = c.sectors
        data class Ty(val name: String, val sym: IntArray, val cl: Int, val base: (Int) -> Double)
        val types = listOf(
            Ty("Number", v, 37) { 1.0 / Wheel.N }, Ty("Sector", IntArray(n) { sec.of[v[it]] }, sec.count) { sec.baseline(it) }, Ty("Region", IntArray(n) { Regions.of[v[it]] }, 3) { Regions.size(it).toDouble() / Wheel.N },
            Ty("Color", IntArray(n) { Wheel.color(v[it]) }, 3) { if (it == 0) 1.0 / 37 else 18.0 / 37 }, Ty("Parity", IntArray(n) { if (v[it] == 0) 2 else v[it] % 2 }, 3) { if (it == 2) 1.0 / 37 else 18.0 / 37 },
            Ty("High/Low", IntArray(n) { if (v[it] == 0) 2 else if (v[it] <= 18) 0 else 1 }, 3) { if (it == 2) 1.0 / 37 else 18.0 / 37 },
            Ty("Dozen", IntArray(n) { if (v[it] == 0) 3 else (v[it] - 1) / 12 }, 4) { if (it == 3) 1.0 / 37 else 12.0 / 37 }, Ty("Column", IntArray(n) { if (v[it] == 0) 3 else (v[it] - 1) % 3 }, 4) { if (it == 3) 1.0 / 37 else 12.0 / 37 })
        val grid = List(3) { MutableList(types.size) { 0.0 } }; val marks = ArrayList<List<Int>>(); val ent = ArrayList<List<String>>()
        for ((ci, t) in types.withIndex()) for (d in 1..3) {
            val cell = An.markov(t.sym, t.cl, d, te, t.base)
            grid[d - 1][ci] = cell.delta
            if (cell.insufficient) marks.add(listOf(d - 1, ci))
            if (t.name in listOf("Number", "Sector", "Color")) ent.add(listOf("${t.name} · ${d}. derece", S.th(cell.states), S.th(cell.trans), S.f(cell.h, 3), S.f(ln(t.cl.toDouble()) / ln(2.0), 3), if (cell.insufficient) "S" else "C"))
        }
        val rg = max(1.0, Math.ceil(grid.maxOf { r -> r.maxOf { abs(it) } }))
        val hm = S.heat("DERECE × TÜR · OOS’ta tabandan fark (pp)", listOf("1. derece", "2. derece", "3. derece"), types.map { it.name }, grid, -rg, rg, true, 1, marks,
            "Eğitim: OOS öncesi tüm veri; test: OOS. S işaretli hücrede durum başına ortalama < 20 geçiş vardır (yetersiz sample). Number 3. derece: 50 653 durum.")
        val et = S.table("ENTROPİ VE SAMPLE SIZE", listOf("Tür · derece", "Durum", "Geçiş", "H (bit)", "Maks.", "Sınıf"), ent, "Durum sayısı arttıkça örnek yetersiz kalır ve S işaretlenir.", al = "lrrrrc")
        val s = types[1].sym; val xs = ArrayList<Double>(); var a = 0
        while (a + 400 <= te) { val sub = s.copyOfRange(a, a + 400); val m = Array(sec.count) { IntArray(sec.count) }; for (i in 1 until sub.size) m[sub[i - 1]][sub[i]]++
            var tot = 0; var h = 0.0; for (r in m.indices) { val t = m[r].sum(); tot += t; h += t * An.entropyBits(m[r]) }; xs.add(h / tot); a += 100 }
        val ln = if (xs.size >= 3) S.line("ROLLING GEÇİŞ ENTROPİSİ · sektör, pencere 400 (bit)", xs.indices.map { "${it + 1}" }, listOf(xs), ymin = (xs.min() - 0.05), ymax = (xs.max() + 0.05), note = "Belirgin düşüş rejim analizi için işaret olabilir (Regime sekmesi).") else S.text("", "Rolling entropi için yeterli veri yok.", "info")
        return listOf(hm, et, ln)
    }

    // ───────── Replay
    private fun replay(c: LabCtx): List<Sec> {
        val rp = c.run
        val steps = S.text("WALK-FORWARD", "TRAIN → PREDICT → LOCK → REVEAL → EVALUATE → UPDATE.\nHer adımda geçmiş fiziksel olarak kopyalanıp kesilir (PAST | CUT | FUTURE); gelecek veriye erişim mümkün değildir. Rastgele shuffle ile zaman serisi bölme yoktur. Bölme: Train ${c.n - 2 * LabRunner.segmentLen(c.n)} · Validation ${c.valSteps.size} · OOS ${c.oos.size}.", "")
        val gates = S.kv("LEAKAGE KAPILARI", rp.gates.map { listOf(it.name, if (it.ok) "GEÇTİ" else "KALDI", if (it.ok) "ok" else "bad", it.detail) } + listOf(listOf("Sonuç", if (rp.leakFree) "temiz" else Codes.LEAK, if (rp.leakFree) "ok" else "bad", "")))
        val det = c.determinism
        val dk = if (det == null) S.text("DETERMİNİZM", "Henüz çalıştırılmadı. “Determinizm testi” aynı veri + parametre + seed ile iki bağımsız koşu yapıp özetleri karşılaştırır.", "info")
        else S.kv("DETERMİNİZM", listOf(listOf("Koşu 1 özeti", java.lang.Long.toHexString(det.first), "", ""), listOf("Koşu 2 özeti", java.lang.Long.toHexString(det.second), "", ""),
            listOf("Sonuç", if (det.first == det.second) "AYNI ✓" else "FARKLI · ${Codes.REPRO}", if (det.first == det.second) "ok" else "bad", "aynı dataset + model + parametre + seed + kod → aynı sonuç")))
        val pr = S.kv("İLERLEME", listOf(listOf("İşlenen / toplam", "${rp.processed} / ${rp.total}", "", ""), listOf("Kayıtlı adım", "${rp.steps.size}", "", "Validation + OOS"), listOf("Özet (hash)", java.lang.Long.toHexString(rp.hash), "", "")))
        return listOf(steps, gates, dk, pr)
    }

    // ───────── Calibration
    private class Axis(val name: String, val p: DoubleArray, val y: BooleanArray, val base: Double)
    private fun axes(c: LabCtx): List<Axis> {
        val o = c.oos; val n = o.size
        val l = ArrayList<Axis>()
        l.add(Axis("Exact (en üst aday)", DoubleArray(n) { o[it].pCand.firstOrNull() ?: 0.0 }, BooleanArray(n) { o[it].exact }, 1.0 / Wheel.N))
        l.add(Axis("Cand-${c.c}", DoubleArray(n) { o[it].p5 }, BooleanArray(n) { o[it].candRank in 1..c.c }, c.c.toDouble() / Wheel.N))
        for ((q, nm) in listOf("Color", "Parity", "High/Low", "Dozen", "Column").withIndex()) l.add(Axis(nm, DoubleArray(n) { o[it].tableP[q] }, BooleanArray(n) { o[it].tableHit[q] }, TableCats.baseline(q)))
        return l
    }
    private fun calibration(c: LabCtx): List<Sec> {
        val ax = axes(c); val out = ArrayList<Sec>()
        val rows = ax.map { a ->
            val n = a.p.size; var br = 0.0; var ll = 0.0
            for (i in 0 until n) { val y = if (a.y[i]) 1.0 else 0.0; br += (a.p[i] - y) * (a.p[i] - y); ll -= ln(max(if (a.y[i]) a.p[i] else 1 - a.p[i], 1e-9)) }
            val e = Stats.ece(Stats.calibration(a.p, a.y)); val bb = a.base * (1 - a.base)
            listOf(a.name, S.f(br / n, 4), S.f(bb, 4), S.f(ll / n, 4), S.f(e, 3))
        }
        out.add(S.table("SKORLAR · Brier, Log Loss, ECE", listOf("Eksen", "Brier", "Rastgele taban", "Log Loss", "ECE"), rows, "Rastgele taban = sabit taban olasılığın Brier’ı. Küçük olasılıklı eksenlerde (Exact) bucket’lar boş kalır; bu normaldir.", al = "lrrrr"))
        for (idx in listOf(1, 2)) {
            val a = ax[idx]; val b = Stats.calibration(a.p, a.y)
            val used = (0 until 10).filter { b.n[it] > 0 }
            out.add(S.reliab("GÜVENİLİRLİK · ${a.name}", used.map { b.meanP(it) }, used.map { b.rate(it) }, used.map { b.n[it] }, "x: tahmin edilen olasılık · y: gözlenen oran · sarı köşegen = mükemmel kalibrasyon · nokta büyüklüğü = örnek sayısı"))
            val br = (0 until 10).map { listOf("${it * 10}–${it * 10 + 10}", if (b.n[it] == 0) "—" else "${b.n[it]}", if (b.n[it] == 0) "—" else S.pc(b.meanP(it) * 100), if (b.n[it] == 0) "—" else S.pc(b.rate(it) * 100)) }
            out.add(S.table("BUCKET · ${a.name}", listOf("Bucket %", "n", "Ort. tahmin", "Gözlenen"), br, "Kullanılmayan bucket’lar “—”.", al = "lrrr"))
        }
        val hi = ax.sumOf { a -> a.p.indices.count { a.p[it] >= 0.7 } }
        out.add(S.text("%70+ GÜVEN", if (hi == 0) "%70 ve üzeri güven alan tahmin yok — bu aralıkta örnek bulunmadığı için uzun dönem gerçek oranı hakkında bir şey söylenemez." else "%70+ güvenli $hi tahmin var; gerçek oranı bucket tablolarında ayrı izlenir.", "info"))
        return out
    }

    // ───────── Diversity
    private fun diversity(c: LabCtx): List<Sec> {
        val o = c.oos; val n = o.size; val out = ArrayList<Sec>()
        val withP = o.filter { it.agreeKP >= 0 }
        if (withP.size >= 30) {
            val ag = withP.count { it.agreeKP == 1 }.toDouble() / withP.size * 100
            out.add(S.kv("AGREEMENT (Kotlin ↔ Python top-1)", listOf(listOf("Aynı top-1", S.pc(ag), "", "n=${withP.size}"), listOf("Ayrışma (disagreement)", S.pc(100 - ag), "", ""))))
            val kk = withP.count { it.kHit && it.pHit == 1 }; val ko = withP.count { it.kHit && it.pHit == 0 }; val po = withP.count { !it.kHit && it.pHit == 1 }; val nn = withP.size - kk - ko - po
            val pk = withP.count { it.kHit }.toDouble() / withP.size; val pp2 = withP.count { it.pHit == 1 }.toDouble() / withP.size; val m = withP.size
            out.add(S.table("İSABET MATRİSİ · top-5", listOf("Durum", "Gözlenen", "Bağımsız olsalardı"), listOf(
                listOf("İkisi de isabet", "$kk", S.f(pk * pp2 * m, 1)), listOf("Yalnız Kotlin", "$ko", S.f(pk * (1 - pp2) * m, 1)), listOf("Yalnız Python", "$po", S.f((1 - pk) * pp2 * m, 1)), listOf("İkisi de ıskaladı", "$nn", S.f((1 - pk) * (1 - pp2) * m, 1))), al = "lrr"))
        } else out.add(S.text("KOTLIN ↔ PYTHON", "Python meclisi bu koşuda yok ya da yeterli adım üretmedi; yalnızca Kotlin üyeleri arasındaki çeşitlilik gösterilir.", "info"))
        val phi = c.phi; val t = c.memberTitles.map { it.substringBefore(" ").take(9) }
        out.add(S.heat("HATA KORELASYONU (φ) · Kotlin üyeleri", t, t, phi.map { it.toList() }, -0.3, 0.3, true, 2, note = "Yüksek korelasyon = aynı hata = bağımsız kanıt değil. Ortalama |φ| = ${S.f(An.phiMean(phi), 3)}"))
        val js = Stats.mean(DoubleArray(n) { o[it].jsK })
        out.add(S.kv("JENSEN–SHANNON", listOf(listOf("Ort. ikili JS ayrışması", S.f(js, 4), "", "üye olasılık dağılımları arasında (çeşitlilik ölçüsü)"))))
        return out
    }

    // ───────── Regime
    private fun regime(c: LabCtx): List<Sec> {
        val rg = c.regime; val out = ArrayList<Sec>()
        out.add(S.heat("REJİM ŞERİDİ · her blok 20 OOS adımı (+ = clustering, − = dispersion)", listOf("rejim"), rg.strip.indices.map { "${it + 1}" }, listOf(rg.strip), -1.0, 1.0, true, 0, note = "Rejimler ayrı analiz edilir; ortalama sonuç rejim farklarını gizleyebilir. Özellikler yalnızca adımdan ÖNCEKİ spinlerden hesaplanır."))
        val rows = rg.rows.map { listOf(it.name, S.th(it.n), pp(it.deltaPp), "±${S.f(it.se * 1.96)}", if (it.n < 100) "Düşük sample" else "") }
        out.add(S.table("REJİM TABLOSU · Candidate-${c.c} farkı", listOf("Rejim", "N", "Δ pp", "%95 ±", ""), rows, "n < 100 olan satırlar “Düşük sample”.", dim = rg.rows.mapIndexedNotNull { i, r -> if (r.n < 100) i else null }, al = "lrrrl"))
        if (rg.blocks.size >= 3) out.add(S.line("MODEL DRIFT · 100 adımlık bloklarda Δ (pp)", rg.blocks.indices.map { "${it + 1}" }, listOf(rg.blocks), hline = 0.0, note = "Eğim ${S.f(rg.slope, 2)} pp/blok (se ${S.f(rg.slopeSe, 2)}). " + if (rg.drift) "MODEL DRIFT işaretlendi." else "Belirgin düşüş yok."))
        val same = rg.rows.drop(3).filter { it.n >= 100 }
        val agree = same.count { (it.deltaPp >= 0) == (c.run.result.deltaPp >= 0) }
        out.add(S.kv("REJİM TUTARLILIĞI", listOf(listOf("Aynı yönde rejim", "$agree / ${same.size}", "", "Robustness skoruna girer"))))
        return out
    }

    // ───────── Counterfactual / Ablation (Suites gerekir)
    private fun needSuites(what: String): Sec = S.text("Henüz çalıştırılmadı", "$what, “Robustness paketi” (ablation + counterfactual + hassasiyet + stres) çalıştırılınca hesaplanır. Arka planda çalışır, canlı modeli ve kilitli tahmini değiştirmez.", "info")
    private fun sig(p: Paired) = if (p.lo > 0) "anlamlı +" else if (p.hi < 0) "anlamlı −" else "0’ı kapsıyor"

    private fun counterfactual(c: LabCtx): List<Sec> {
        val su = c.suites ?: return listOf(needSuites("Karşı-olgusal analiz"))
        if (su.counterfactual.isEmpty()) return listOf(needSuites("Karşı-olgusal analiz"))
        val rows = su.counterfactual.map { (nm, p) -> listOf(nm, pp(p.delta), "[${S.f(p.lo)}; ${S.f(p.hi)}]", pp(p.raw), sig(p)) }
        val t = S.table("KARŞI-OLGUSAL · FULL’a göre Candidate-${c.c} farkı", listOf("Senaryo", "Δ düz.", "%95 CI", "Ham Δ", "Yorum"), rows,
            "Δ düz. = kapsama düzeltmeli, eşleştirilmiş bootstrap. Ham Δ = düzeltmesiz isabet farkı (3/4 aday satırlarında kapsama yüzünden büyüktür).", al = "lrrrl")
        val nsig = su.counterfactual.count { it.second.lo > 0 || it.second.hi < 0 }
        val yo = if (nsig == 0) S.text("Yorum", "Hiçbir karşı-olgusal fark anlamlı değil: aralıkların hepsi 0’ı kapsıyor. Aralık 0’ı kapsıyorsa bileşenin katkısı kanıtlanmamıştır.", "info")
        else S.text("Yorum", "$nsig karşı-olgusal fark %95 aralığında 0’ı dışarıda bırakıyor; ${su.counterfactual.size} karşılaştırma yapıldığı için çoklu test etkisi göz önüne alınmalıdır.", "warn")
        return listOf(t, yo)
    }

    private fun ablation(c: LabCtx): List<Sec> {
        val su = c.suites ?: return listOf(needSuites("Ablation"))
        val full = su.full ?: return listOf(needSuites("Ablation"))
        if (su.ablation.isEmpty()) return listOf(needSuites("Ablation"))
        val hd = full.headline
        val ref = S.kv("FULL REFERANSI", listOf(listOf("Candidate-${c.c} farkı", pp(full.deltaPp), tone(full.deltaPp), "%95 CI [${S.f(full.ciLo)}; ${S.f(full.ciHi)}]"), listOf("Gözlenen / taban", "${S.pc(hd.obs)} / ${S.pc(hd.base)}", "", "n = ${full.n} OOS"), listOf("Sınıf", full.cls, "", "Kotlin-only referans (Python dışı)")))
        val sorted = su.ablation.sortedBy { it.second.delta }
        val fo = S.forest("GRUP ÇIKARMA · FULL’a göre değişim (pp, %95 CI)", sorted.map { it.first }, sorted.map { it.second.delta }, sorted.map { it.second.lo }, sorted.map { it.second.hi }, max(2.0, Math.ceil(sorted.maxOf { max(abs(it.second.lo), abs(it.second.hi)) }) + 1),
            "Negatif = grup çıkınca isabet düştü (olası katkı). Aralık 0’ı kapsıyorsa katkı kanıtlanmamıştır.")
        val rows = sorted.mapIndexed { i, (nm, p) -> listOf("${i + 1}", nm, pp(p.delta), "[${S.f(p.lo)}; ${S.f(p.hi)}]", sig(p)) }
        val t = S.table("KATKI SIRALAMASI", listOf("#", "Çıkarılan", "Δ", "%95 CI", "Yorum"), rows, "Çıkarıldığında en çok düşüşü yaratan gruplar üstte.", al = "llrrl")
        val ns = su.ablation.count { it.second.lo > 0 || it.second.hi < 0 }
        return listOf(ref, fo, t, S.text("", if (ns == 0) "Tüm aralıklar 0’ı kapsadığından “kanıtlı katkı” yok." else "$ns grup çıkarmada fark %95 aralığının dışında; çoklu test (${su.ablation.size}) nedeniyle yalnızca aday sayılır.", "info"))
    }

    // ───────── Robustness
    private fun robustness(c: LabCtx, sub: String): List<Sec> = when (sub) {
        "sens" -> robSens(c); "stress" -> robStress(c); "err" -> robErr(c); else -> robScore(c)
    }
    private fun robScore(c: LabCtx): List<Sec> {
        val rs = c.robust
        val g = S.gauge("ROBUSTNESS SKORU", rs.score, rs.cls, "Skor bir garanti değil, kanıtın ne kadar tutarlı olduğunun özetidir.")
        val pos = S.kv("POZİTİF BİLEŞENLER", rs.pos.map { listOf(it.name, "${S.f(it.value, 1)} / ${S.f(it.max, 0)}", if (it.value >= it.max * 0.6) "ok" else "", it.note) })
        val neg = S.kv("NEGATİF BİLEŞENLER (puandan düşülür)", rs.neg.map { listOf(it.name, "−${S.f(it.value, 1)}", if (it.value > 0) "bad" else "ok", it.note) })
        return listOf(g, pos, neg, S.text("Not", "Robustness skoru en yüksek hit oranını aramaz; Final model seçimi ayrı kapılardan geçer (Champion/Challenger).", "info"))
    }
    private fun robSens(c: LabCtx): List<Sec> {
        val sg = c.suites?.sens ?: return listOf(needSuites("Parametre hassasiyeti"))
        val mx = max(2.0, Math.ceil(sg.v.maxOf { r -> r.maxOf { abs(it) } }))
        val mk = sg.spike?.let { listOf(it) } ?: emptyList()
        val hm = S.heat("PARAMETRE HASSASİYETİ · Candidate-${c.c} farkı (pp)", sg.wins.map { "w${it}" }, sg.ks.map { "k$it" }, sg.v, -mx, mx, true, 1, mk, "Satır = window, sütun = k. Altın çerçeve = komşularından kopuk tek nokta başarısı.")
        val ki = sg.ks.indexOf(2).let { if (it < 0) 0 else it }
        val ln = S.line("KESİT · k${sg.ks[ki]} için window boyunca Δ (pp)", sg.wins.map { "$it" }, listOf(sg.v.map { it[ki] }), hline = 0.0, note = "Düz bir bant = parametreye duyarsız (iyi); tek tepe = overfit riski.")
        val pos = sg.v.flatten().count { it >= 0 }; val tot = sg.v.flatten().size
        val broad = sg.v.flatten().count { it > 0.5 } >= tot * 0.6
        val dec = if (sg.spike != null) S.text("KARAR", "OVERFIT RISK: başarı tek parametre noktasında (w${sg.wins[sg.spike[0]]}, k${sg.ks[sg.spike[1]]}).", "bad")
        else if (broad) S.text("KARAR", "ROBUSTNESS ADAYI: başarı geniş bir parametre aralığında (kanıt değil, aday).", "ok")
        else S.text("KARAR", "Geniş aralıkta başarı yok; tek nokta başarısı da yok. Hücrelerin $pos / $tot kadarı ≥ 0.", "info")
        return listOf(hm, ln, dec)
    }
    private fun robStress(c: LabCtx): List<Sec> {
        val st = c.suites?.stress ?: return listOf(needSuites("Stres testi"))
        val rows = st.map { listOf(it.name, it.what, it.outcome, if (it.ok) "SİM ✓" else "SİM ✗") }
        return listOf(S.table("STRES SENARYOLARI · yapay veri", listOf("Senaryo", "Ne yapıldı", "Uygulama davranışı", ""), rows, "SİM: yapay stres sonucu gerçek performans olarak raporlanmaz; yalnızca dayanıklılık (çökme / yanlış alarm / bayrak) için okunur.", al = "lllc"),
            S.text("", if (st.all { it.ok }) "Tüm stres senaryoları çökmeden tamamlandı." else "Bazı senaryolarda hata var — satırlara bak.", if (st.all { it.ok }) "ok" else "bad"))
    }
    private fun robErr(c: LabCtx): List<Sec> {
        val o = c.oos; val n = o.size; val half = n / 2
        class R(val name: String, val cnt: Int, val den: Int, val first: Double, val second: Double)
        fun mk(name: String, den: (StepRec) -> Int, f: (StepRec) -> Int): R {
            fun rate(l: List<StepRec>): Double { val d = l.sumOf(den); return if (d == 0) 0.0 else l.sumOf(f).toDouble() / d * 100 }
            return R(name, o.sumOf(f), o.sumOf(den), rate(o.take(half)), rate(o.drop(half)))
        }
        val cc = c.c; val pAbove = o.count { it.p5 >= 0.7 }
        val list = listOf(
            mk("Wrong exact", { 1 }, { if (it.exact) 0 else 1 }), mk("Wrong candidate", { 1 }, { if (it.candRank in 1..cc) 0 else 1 }), mk("Wrong sector", { 1 }, { if (it.sectorHit) 0 else 1 }),
            mk("Wrong region", { 1 }, { if (it.regionHit) 0 else 1 }), mk("Wrong neighbor", { 1 }, { if (it.neighbor) 0 else 1 }), mk("Wrong table (5 kategori)", { 5 }, { s -> s.tableHit.count { !it } }),
            mk("Overconfident miss (p ≥ %70)", { if (it.p5 >= 0.7) 1 else 0 }, { if (it.p5 >= 0.7 && it.candRank !in 1..cc) 1 else 0 }),
            mk("Underconfident hit (p < %10)", { 1 }, { if (it.p5 < 0.10 && it.candRank in 1..cc) 1 else 0 }),
            mk("Model disagreement", { 1 }, { if (it.agreeKP == 0) 1 else 0 })
        )
        val rows = list.map { r -> listOf(r.name, S.th(r.cnt), if (r.den == 0) "—" else S.pc(r.cnt * 100.0 / r.den), if (r.den == 0) "—" else pp(r.second - r.first)) }.toMutableList()
        val bad = c.regime.rows.filter { it.n >= 100 && it.deltaPp + 1.96 * it.se < 0 }.size
        rows.add(rows.size - 1, listOf("Regime failure (anlamlı − rejim)", "$bad / ${c.regime.rows.size}", "—", "—"))
        rows.add(listOf("Data error (içe aktarma)", S.th(c.dataErrors), S.pc(c.dataErrors * 100.0 / max(1, c.n + c.dataErrors), 2), "—"))
        return listOf(S.table("HATA SINIFLARI · OOS $n adım", listOf("Sınıf", "Sayı", "Oran", "Eğilim (2. yarı − 1. yarı)"), rows, "Aşırı güvenli ıska: ${if (pAbove == 0) "%70+ güvenli tahmin olmadığından 0; yine de izlenir." else "%70+ güvenli $pAbove tahmin var."} Veri hataları (0–36 dışı, yinelenen vb.) modelden ayrı sınıflandırılır.", al = "lrrr"))
    }

    // ───────── Experiments (data-snooping panosu)
    private fun experiments(c: LabCtx): List<Sec> {
        val r = c.run.result; val ex = c.experiments; val done = ex.filter { it.status == "DONE" }
        val ds = done.map { it.deltaPp }.sorted()
        val sens = c.suites?.sens?.v?.flatten()
        val rows = listOf(
            listOf("Toplam deney", S.th(ex.size), "", "başarısızlar dahil — silinmez"),
            listOf("Arama uzayı (benzersiz parametre)", S.th(ex.map { it.params }.toSet().size), "", ""),
            listOf("En iyi / medyan Δ", if (ds.isEmpty()) "—" else "${pp(ds.last())} / ${pp(ds[ds.size / 2])}", "", "en iyi sonuç kanıt sayılmaz (seçim yanlılığı)"),
            listOf("Tekrarlanabilirlik", c.determinism?.let { if (it.first == it.second) "AYNI ✓" else "FARKLI ✗" } ?: "—", if (c.determinism != null && c.determinism.first == c.determinism.second) "ok" else "", "Replay → determinizm testi"),
            listOf("OOS Δ ve %95 CI", "${pp(r.deltaPp)}  [${S.f(r.ciLo)}; ${S.f(r.ciHi)}]", "", "n = ${r.n}"),
            listOf("Parametre stabilitesi", if (sens == null) "—" else "${sens.count { it >= 0 }} / ${sens.size} hücre ≥ 0", "", "Robustness → Hassasiyet"))
        return listOf(S.kv("DATA-SNOOPING PANOSU", rows, "Çok sayıda deney denenince şans eseri iyi görünen sonuç çıkması beklenir; bu pano o riski görünür tutar."))
    }
}

