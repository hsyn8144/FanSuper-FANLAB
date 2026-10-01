package fan.superai.v13

/**
 * Tahmin açıklaması. Metinlerin HEPSİ gerçek hesaplardan (tahmin anındaki bağlam + durum) üretilir;
 * uydurma açıklama yoktur. Kilit geri yüklenirken aynı bağlam aynı metni üretir.
 */
class Explainer(
    private val b: FanBrain, private val ctx: PredictionContext, private val fin: Array<DoubleArray>,
    private val d: Decision, private val mixes: Array<DoubleArray>, private val combined: CombinedSide,
    private val contributions: List<ModelContribution>
) {
    private fun pct(x: Double) = "%${(x * 100).toInt()}"

    private val a = b.alphabet
    private val chosen = intArrayOf(d.numberIdx, d.bs, d.oe, combined.index)

    private fun label(axis: Axis, cls: Int): String = when (axis) {
        Axis.NUMBER -> "${a.number(cls)}"
        Axis.BS -> BigSmall.fromIndex(cls).tr
        Axis.OE -> OddEven.fromIndex(cls).tr
        Axis.COMB -> CombinedSide.fromIndex(cls).display
    }

    /** Bir grubun (Kotlin/Python) kendi içinde ağırlıklı karışımı. null → grup yok. */
    private fun groupMix(axis: Axis, g: Group): DoubleArray? {
        val ai = ctx.axis(axis)
        val idx = (0 until ai.size).filter { ai.groups[it] == g }
        if (idx.isEmpty()) return null
        val k = a.k(axis)
        val w = DoubleArray(idx.size) { ai.weights[idx[it]] }
        val s = w.sum().let { if (it <= 0) 1.0 else it }
        val ds = Array(idx.size) { ai.dists[idx[it]] }
        return Pool.mix(k, ds, DoubleArray(idx.size) { w[it] / s })
    }

    private fun disagreement(axis: Axis, cls: Int): Double {
        val ai = ctx.axis(axis)
        if (ai.size == 0) return 0.0
        var n = 0; for (i in 0 until ai.size) if (Mx.argmax(ai.dists[i]) != cls) n++
        return n.toDouble() / ai.size
    }

    fun build(): PredictionExplanation {
        val np = ArrayList<String>(); val nn = ArrayList<String>()
        val sp = ArrayList<String>(); val sn = ArrayList<String>(); val sd = ArrayList<String>()
        numberEvidence(np, nn)
        sideEvidence(sp, sn, sd)
        return PredictionExplanation(np, nn, sp, sn, sd)
    }

    private fun numberEvidence(pos: MutableList<String>, neg: MutableList<String>) {
        val ax = Axis.NUMBER; val ni = chosen[0]; val num = a.number(ni)
        if (ctx.axis(ax).size == 0) { neg += "Rakam modeli çıktısı yok (tahmin düz dağılımdan)"; return }
        for (g in Group.values()) {
            val gm = groupMix(ax, g) ?: continue
            val f = Mx.argmax(gm)
            if (f == ni) pos += "${g.tr} modelleri $num rakamını öne çıkarıyor (grup olasılığı ${pct(gm[f])})"
            else neg += "${g.tr} modelleri ${a.number(f)} rakamını öne çıkarıyor (grup ${pct(gm[f])}), nihai $num"
        }
        val dis = disagreement(ax, ni)
        if (dis > 0.5) neg += "Model anlaşmazlığı yüksek: modellerin ${pct(dis)}'i farklı rakam seçiyor"
        else pos += "Modellerin ${pct(1 - dis)}'i aynı rakamı ($num) seçiyor"
        val ne = Mx.normEntropy(fin[0])
        if (ne > 0.97) neg += "Entropi yüksek (${pct(ne)}): dağılım neredeyse düz"
        else if (ne < 0.9) pos += "Entropi düşük (${pct(ne)}): dağılım belirgin"
        val t2 = Mx.top2(fin[0]); val margin = fin[0][t2[0]] - fin[0][t2[1]]
        if (margin < 0.02) neg += "İlk iki aday arasındaki fark çok küçük (${pct(margin)})"
        // Rejim uyumu: bu rejimdeki gerçek sayı isabeti
        val rp = b.regime.perf[ctx.regime]; val rn = rp[0].toInt()
        val base = 1.0 / a.size
        if (rn >= 20) {
            val rate = rp[1] / rp[0]
            if (rate > base) pos += "${V13.REGIME_NAMES[ctx.regime]} rejimi uyumlu: sayı isabeti ${pct(rate)} (n=$rn) > şans ${pct(base)}"
            else neg += "${V13.REGIME_NAMES[ctx.regime]} rejiminde sayı isabeti ${pct(rate)} (n=$rn) şansın (${pct(base)}) altında/eşit"
        } else neg += "${V13.REGIME_NAMES[ctx.regime]} rejiminde yeterli örnek yok (n=$rn)"
        val ft = b.ens[0].finalTracker
        if (ft.cal.total >= 50) {
            val gap = ft.cal.gap()
            if (gap < 0.08) pos += "Son kalibrasyon kabul edilebilir (boşluk ${pct(gap)})"
            else neg += "Kalibrasyon boşluğu yüksek (${pct(gap)})"
        } else neg += "Kalibrasyon için örnek az (n=${ft.cal.total})"
        if (ft.roll.count >= 30) {
            if (ft.recent > base) pos += "Son ${ft.roll.count} tahminde nihai isabet ${pct(ft.recent)} > şans ${pct(base)}"
            else neg += "Son ${ft.roll.count} tahminde nihai isabet ${pct(ft.recent)} şansın altında/eşit"
        }
        // En çok katkı veren modeller (gerçek ağırlık × olasılık payı)
        val top = contributions.filter { it.axis == ax }.sortedByDescending { it.share }.take(3)
        if (top.isNotEmpty()) pos += "En büyük katkılar: " + top.joinToString(", ") { "${it.name} ${pct(it.share)}" }
    }

    private fun axisLine(axis: Axis, out: MutableList<String>) {
        val ci = chosen[axis.ordinal]
        val parts = ArrayList<String>()
        for (g in Group.values()) {
            val gm = groupMix(axis, g) ?: continue
            val f = Mx.argmax(gm)
            parts += "${g.tr} favori = ${label(axis, f)} (${pct(gm[f])})"
        }
        out += "${label(axis, ci)}: " + (if (parts.isEmpty()) "model yok" else parts.joinToString(", ")) +
            " · nihai ${pct(if (axis == Axis.BS) d.marginalBs[ci] else d.marginalOe[ci])}"
    }

    private fun sideEvidence(pos: MutableList<String>, neg: MutableList<String>, det: MutableList<String>) {
        for (ax in listOf(Axis.BS, Axis.OE)) {
            axisLine(ax, det)
            val ci = chosen[ax.ordinal]
            val name = if (ax == Axis.BS) "Büyük/Küçük" else "Tek/Çift"
            for (g in Group.values()) {
                val gm = groupMix(ax, g) ?: continue
                val f = Mx.argmax(gm)
                if (f == ci) pos += "$name: ${g.tr} yan analizi ${label(ax, ci)} yönünde"
                else neg += "$name: ${g.tr} yan analizi ${label(ax, f)} diyor, nihai ${label(ax, ci)}"
            }
            val dis = disagreement(ax, ci)
            if (dis > 0.5) neg += "$name: yan modellerinin ${pct(dis)}'i nihai seçime katılmıyor"
            val ne = Mx.normEntropy(fin[ax.ordinal])
            if (ne > 0.985) neg += "$name entropisi yüksek (${pct(ne)}): iki seçenek neredeyse eşit"
            val py = (0 until ctx.axis(ax).size).firstOrNull { ctx.axis(ax).ids[it] == "py_side_kalip" }
            if (py != null) {
                val pk = ctx.axis(ax).dists[py]; val f = Mx.argmax(pk)
                det += "Kalıp 2.0 (Python) $name: ${label(ax, f)} (${pct(pk[f])})" + if (f == ci) " ✓" else ""
            }
            // Seri: yalnızca öğrenilmiş devam olasılığı
            val sm = b.side.model(ax, "ks_streak") as? StreakModel
            val last = b.mem.sym(ax, 1)
            if (sm != null && last >= 0) {
                val run = b.mem.run(ax); val pc = sm.continueProb(run)
                det += "$name serisi: $run × ${label(ax, last)}; öğrenilmiş devam olasılığı ${pct(pc)}"
            }
            val rp = b.regime.perf[ctx.regime]
            if (rp[0] >= 20) {
                val r = rp[2] / rp[0]
                det += "${V13.REGIME_NAMES[ctx.regime]} yan isabeti (ikisi birden): ${pct(r)} (n=${rp[0].toInt()}), şans %25"
            }
        }
        // Birleşik geçiş
        val lastC = b.mem.sym(Axis.COMB, 1)
        if (lastC >= 0) {
            val tp = b.mem.transitionProb(Axis.COMB, lastC)
            val f = Mx.argmax(tp)
            val line = "Birleşik geçiş: ${CombinedSide.fromIndex(lastC).display} sonrası en olası ${CombinedSide.fromIndex(f).display} (${pct(tp[f])})"
            det += line
            if (f == combined.index) pos += "Birleşik geçiş matrisi ${combined.display} seçimini destekliyor"
            else neg += "Birleşik geçiş matrisi ${CombinedSide.fromIndex(f).display} diyor, nihai ${combined.display}"
        }
        det += "Birleşik dağılım: " + (0 until 4).joinToString(" · ") { "${CombinedSide.fromIndex(it).display} ${pct(d.joint[it])}" }
        det += "Ürün/Doğrudan birleşik uzman payı: ${pct(ctx.blendProduct)} / ${pct(1 - ctx.blendProduct)}"
        val cf = b.ens[Axis.COMB.ordinal].finalTracker
        if (cf.cal.total >= 50) {
            if (cf.cal.gap() < 0.08) pos += "Yan kalibrasyonu kabul edilebilir (boşluk ${pct(cf.cal.gap())})"
            else neg += "Yan kalibrasyon boşluğu yüksek (${pct(cf.cal.gap())})"
        }
    }
}
