package fan.lightningroulette.core

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

/** Hakem (meta-ensemble) analizleri: Kotlin/Python ağırlık testi (VALIDATION), konformal küme boyutu (Prompt §11, §22, §66). */
object Referee {
    class WeightRow(val wKotlinPct: Int, val logLoss: Double)
    class Result(val rows: List<WeightRow>, val kotlinOnly: Double, val pythonOnly: Double?, val baseline: Double, val conformalSize: Int, val conformalCover: Double, val n: Int, val hasPython: Boolean)

    fun analyse(steps: List<StepRec>, valStart: Int, oosStart: Int): Result {
        val v = steps.filter { it.i in valStart until oosStart }
        val base = ln(Wheel.N.toDouble())
        if (v.isEmpty()) return Result(emptyList(), base, null, base, Wheel.N, 1.0, 0, false)
        val ll: (Double) -> Double = { p -> -ln(max(p, 1e-12)) }
        val kOnly = v.map { ll(it.kPA) }.average()
        val hasP = v.all { it.pPA >= 0.0 }
        val rows = if (!hasP) emptyList() else (20..80 step 10).map { w -> WeightRow(w, v.map { ll(w / 100.0 * it.kPA + (1 - w / 100.0) * it.pPA) }.average()) }
        val pOnly = if (hasP) v.map { ll(it.pPA) }.average() else null
        val ranks = v.map { it.rank }.filter { it > 0 }.sorted()
        var size = Wheel.N; var cover = 1.0
        if (ranks.isNotEmpty()) {
            val idx = (ceil(0.9 * ranks.size).toInt() - 1).coerceIn(0, ranks.size - 1)
            size = ranks[idx]; cover = ranks.count { it <= size }.toDouble() / ranks.size
        }
        return Result(rows, kOnly, pOnly, base, size, cover, v.size, hasP)
    }

    fun secs(c: LabCtx): List<Sec> {
        val r = analyse(c.run.steps, c.run.valStart, c.run.oosStart)
        val out = ArrayList<Sec>()
        if (r.n == 0) return listOf(S.text("Hakem", "Validation adımı yok (veri çok az). Daha fazla spin ekle.", "info"))
        if (r.hasPython) {
            val best = r.rows.minByOrNull { it.logLoss }!!
            out.add(S.line("AĞIRLIK TESTİ · VALIDATION log loss (düşük iyi)", r.rows.map { "${it.wKotlinPct}/${100 - it.wKotlinPct}" }, listOf(r.rows.map { it.logLoss }), hline = r.baseline,
                note = "Kesikli çizgi = rastgele taban (ln 37 = ${S.f(r.baseline, 4)}). En iyi: Kotlin ${best.wKotlinPct}% / Python ${100 - best.wKotlinPct}% → ${S.f(best.logLoss, 4)}. " +
                    if (best.logLoss >= r.baseline) "Hiçbiri tabandan iyi değil." else "Tabandan fark küçüktür; OOS’ta doğrulanmadan kanıt sayılmaz."))
            out.add(S.kv("TEK BAŞINA", listOf(listOf("Yalnız Kotlin", S.f(r.kotlinOnly, 4), "", "log loss"), listOf("Yalnız Python", S.f(r.pythonOnly ?: 0.0, 4), "", "log loss"), listOf("Rastgele taban", S.f(r.baseline, 4), "", "ln 37"))))
        } else out.add(S.text("Ağırlık testi", "Bu LAB koşusuna Python dahil edilmedi; yalnız Kotlin log loss = ${S.f(r.kotlinOnly, 4)} (taban ${S.f(r.baseline, 4)}). Ayarlar › Python / LAB’dan “LAB’a Python dahil et” açılırsa 20/80 … 80/20 testi görünür.", "info"))
        out.add(S.kv("OOS KİLİDİ", listOf(listOf("Ağırlık seçimi", "VALIDATION", "ok", "test (OOS) setinde seçim yapılmaz"), listOf("OOS kullanım sayacı", "${c.experiments.count { it.status == "DONE" }}", "", "aynı OOS’ta tekrar tekrar model seçimi yasaktır"))))
        out.add(S.kv("KONFORMAL KÜME · %90 kapsam", listOf(listOf("Gereken küme boyutu", "${r.conformalSize} / ${Wheel.N}", if (r.conformalSize >= 30) "warn" else "", "kapsama ${S.pc(r.conformalCover * 100)} · n = ${r.n}"), listOf("Rastgele taban", S.f(0.9 * Wheel.N, 1), "", "bilgilendirici olmak için tabandan belirgin küçük olmalı")),
            if (r.conformalSize >= 30) "≈ ${r.conformalSize}/37 sayı gerekiyor: küme bilgilendirici değil." else "Küme tabandan küçük görünüyor; OOS’ta doğrulanmalı."))
        out.add(S.text("Kalibrasyon", "Sıcaklık ölçekleme (τ, her 10 adımda bir yeniden uydurulur) ve bucket kalibrasyonu; ayrıntı LAB › Calibration.", "info"))
        return out
    }
}
