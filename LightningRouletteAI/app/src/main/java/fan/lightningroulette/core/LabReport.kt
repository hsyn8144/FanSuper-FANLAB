package fan.lightningroulette.core

/** Tek bir LAB deneyinin raporu (Ekran 42): hit türleri ve fark, skorlar ve CI, analiz özetleri, durum/sınıf. */
object LabReport {
    fun failReason(r: ExpResult): String = when (r.cls) {
        "L" -> "Leakage şüphesi: sonuç geçersiz sayıldı"
        "S" -> "Yetersiz sample (OOS n=${r.n})"
        "O" -> "Overfit şüphesi: validation Δ ${S.sg(r.valDeltaPp)} → OOS Δ ${S.sg(r.deltaPp)}"
        "A", "B" -> ""
        else -> "OOS’ta tutmadı: Δ ${S.sg(r.deltaPp)} [${S.f(r.ciLo)}; ${S.f(r.ciHi)}] · perm. p ${S.f(r.permP, 2)}"
    }

    fun build(r: ExpResult, code: String, status: String, hypothesis: String, createdAt: Long): List<Sec> {
        val out = ArrayList<Sec>()
        out.add(S.tiles(listOf(
            listOf("Kimlik", code.takeLast(12), hypothesis.take(28), ""),
            listOf("Durum", status, "sınıf ${r.cls}", if (status == "DONE") "ok" else "warn"),
            listOf("OOS N", S.th(r.n), "Validation ${S.th(r.nVal)}", ""),
            listOf("Δ ${r.headline.name}", S.sg(r.deltaPp), "[${S.f(r.ciLo)}; ${S.f(r.ciHi)}]", if (r.ciLo > 0) "ok" else "")
        )))
        val ms = r.metrics
        out.add(S.forest("HİT TÜRLERİ · tabandan fark (pp, %95 CI)", ms.map { it.name }, ms.map { it.delta }, ms.map { it.lo - it.base }, ms.map { it.hi - it.base },
            Math.max(2.0, Math.ceil(ms.maxOf { Math.max(Math.abs(it.lo - it.base), Math.abs(it.hi - it.base)) }) + 1), "Exact ve Candidate ayrı satırlardır. Sarı çizgi = tesadüf tabanı."))
        out.add(S.table("ÖLÇÜTLER", listOf("Ölçüt", "Gözlenen", "Taban", "Fark", "%95 CI"),
            ms.map { listOf(it.name, S.pc(it.obs), S.pc(it.base), S.sg(it.delta, 1, ""), "[${S.f(it.lo - it.base)}; ${S.f(it.hi - it.base)}]") }, al = "lrrrr"))
        out.add(S.kv("SKORLAR VE GÜVEN ARALIĞI", listOf(
            listOf("Brier (Cand-${r.cfg.cands} olayı)", S.f(r.brier, 4), "", "rastgele taban ${S.f(r.baseBrier, 4)}"),
            listOf("Log Loss", S.f(r.logLoss, 4), "", "rastgele taban ${S.f(r.baseLogLoss, 4)} (ln 37)"),
            listOf("ECE (kalibrasyon hatası)", S.f(r.ece, 3), if (r.ece > 0.05) "warn" else "", ""),
            listOf("Permütasyon p (1 000)", S.f(r.permP, 3), "", "görülen farkın rastgele düzenlemelerde de çıkma olasılığı"),
            listOf("%95 CI (Δ pp)", "[${S.f(r.ciLo)}; ${S.f(r.ciHi)}]", "", "bootstrap · 1 000 örnek"))))
        val overfit = r.valDeltaPp - r.deltaPp
        out.add(S.kv("ANALİZ ÖZETLERİ", listOf(
            listOf("Leakage kapıları", if (r.leakFree) "temiz" else Codes.LEAK, if (r.leakFree) "ok" else "bad", "sıra · kesim · zaman · test seçimi yok"),
            listOf("Overfit (validation → OOS)", "${S.sg(r.valDeltaPp)} → ${S.sg(r.deltaPp)}", if (overfit > 3) "warn" else "", if (overfit > 3) "validation OOS’tan belirgin iyi" else "belirgin fark yok"),
            listOf("Yapılandırma", r.cfg.label(), "", "hash ${r.cfg.paramHash()} · seed ${r.cfg.seed}"),
            listOf("Sonuç özeti (hash)", java.lang.Long.toHexString(r.hash), "", "yeniden üretimde aynı çıkmalı"))))
        val fr = failReason(r)
        out.add(S.text(if (fr.isEmpty()) "Değerlendirme" else "Başarısızlık nedeni", if (fr.isEmpty()) "Sınıf ${r.cls}: aday sinyal; tek başına kanıt değildir, bağımsız pencerede doğrulanmalıdır." else fr, if (fr.isEmpty()) "info" else "warn"))
        return out
    }
}
