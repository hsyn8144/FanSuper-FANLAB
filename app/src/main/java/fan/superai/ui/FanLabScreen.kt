package fan.superai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fan.superai.EngineHost
import fan.superai.v13.Axis
import fan.superai.v13.CfResult
import fan.superai.v13.FinalPrediction
import fan.superai.v13.ModelRow
import fan.superai.v13.PredictionExplanation
import fan.superai.v13.V13Insights
import fan.superai.v13.db.PredictionRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val TABS = listOf("Overview", "Models", "Side Analysis", "Replay", "Calibration", "Diversity", "Regime", "Counterfactual")
private val AXES = listOf("NUMBER" to "Rakam", "BS" to "Büyük/Küçük", "OE" to "Tek/Çift", "COMB" to "Birleşik yan")

private fun f2(x: Double) = "%.2f".format(x)
private fun f3(x: Double) = "%.3f".format(x)
private fun f4(x: Double) = "%.4f".format(x)

/** FAN LAB: 8 sekme (kaydırılabilir). Overview altında v1.2 araştırma içeriği korunur. */
@Composable
fun FanLabScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ins by EngineHost.v13Insights.collectAsState()
    val fin by EngineHost.finalPrediction.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TABS.forEachIndexed { i, t ->
                Text(t, color = if (i == tab) Color.White else C.muted, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (i == tab) C.blue else C.card)
                        .clickable { tab = i }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
            when (tab) {
                0 -> OverviewTab(ins, fin)
                1 -> ModelsTab(ins)
                2 -> SideTab(ins, fin)
                3 -> ReplayTab()
                4 -> CalibrationTab(ins)
                5 -> DiversityTab(ins)
                6 -> RegimeTab(ins)
                else -> CounterfactualTab(fin)
            }
        }
    }
}

@Composable
private fun Mono(t: String, color: Color = C.text, size: Int = 11, modifier: Modifier = Modifier) =
    Text(t, color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace, modifier = modifier)

@Composable
private fun TableRow(cells: List<String>, widths: List<Int>, header: Boolean = false, color: Color = C.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        cells.forEachIndexed { i, c ->
            Text(c, color = if (header) C.head else color, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                modifier = if (widths[i] == 0) Modifier.weight(1f) else Modifier.width(widths[i].dp))
        }
    }
}

@Composable
private fun Lines(prefix: String, items: List<String>, color: Color) {
    items.forEach { Text("$prefix $it", color = color, fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp)) }
}

@Composable
private fun ExplanationCards(e: PredictionExplanation) {
    FCard("Rakam açıklaması (gerçek hesaplardan)") {
        if (e.numberPositive.isEmpty() && e.numberNegative.isEmpty()) Muted("Veri yok")
        Lines("＋", e.numberPositive, C.lightGreen); Lines("－", e.numberNegative, C.danger)
    }
    FCard("Yan açıklaması") {
        Lines("＋", e.sidePositive, C.lightGreen); Lines("－", e.sideNegative, C.danger)
        Lines("·", e.sideDetail, C.muted)
    }
}

// ============================================================================================ Overview
@Composable
private fun OverviewTab(ins: V13Insights?, fin: FinalPrediction?) {
    val status by EngineHost.v13Status.collectAsState()
    FCard("🧠 v1.3 Meta-Ensemble · nihai tahmin") {
        if (fin == null) Muted(status.error ?: status.message.ifEmpty { "Hazırlanıyor…" })
        else {
            KV("Rakam", "${fin.number}  (güven ${pct(fin.confidence)})")
            KV("Yan", "${fin.side.display}  (güven ${pct(fin.sideConfidence)})")
            KV("Entropi", f3(fin.entropy))
            KV("Rejim", fin.regime)
            KV("Ensemble", "v${fin.ensembleVersion}")
            KV("Kilit", fin.predictionLockId, mono = true)
            KV("Sıra / son bilinen kayıt", "#${fin.predictionSequence} / #${fin.lastKnownRecordId}", mono = true)
            Muted("Rakam dağılımı: " + fin.numberDistribution.probabilities.withIndex().joinToString("  ") { "${it.index + 1}:${pct(it.value)}" })
        }
        status.error?.let { Text("⚠️ $it", color = C.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
    ins?.let { x ->
        val s = x.stats
        FCard("Başarı (v1.3 nihai, n=${s.total}) · baseline'larla") {
            @Composable fun row(k: String, v: Double, base: Double, causal: Double) =
                KV(k, "${pct(v)} · şans ${pct(base)} · çoğunluk ${pct(causal)}", vColor = if (s.total < 30) C.text else if (v > base + 0.02) C.lightGreen else if (v < base - 0.02) C.danger else C.text)
            row("Rakam Top-1", s.numberAcc, 0.25, s.baseNumber)
            KV("Rakam Top-2", "${pct(s.top2Acc)} · şans %50")
            row("Büyük/Küçük", s.bsAcc, 0.5, s.baseBs)
            row("Tek/Çift", s.oeAcc, 0.5, s.baseOe)
            row("Yan (ikisi birden)", s.sideAcc, 0.25, s.baseSide)
            KV("Log loss / Brier", "${f3(s.numberLL)} / yan LL ${f3(s.sideLL)}")
            KV("Son ${s.recentN}", "rakam ${pct(s.recentNumber)} · yan ${pct(s.recentSide)}")
            Muted("Veri neredeyse rastgeledir; şans çizgisinin üstü kalıcı değilse bunu başarı saymayın (Replay › permutation testine bakın).", Modifier.padding(top = 4.dp))
        }
        FCard("Sızıntı denetimi") {
            KV("Kontrol / ihlal", "${x.leakChecks} / ${x.leakViolations}", vColor = if (x.leakViolations == 0L) C.lightGreen else C.danger)
            x.leakMessages.takeLast(3).forEach { Muted(it) }
        }
    }
    fin?.let { ExplanationCards(it.explanation) }
    PredictionHistory()
    Muted("— v1.2 araştırma (walk-forward) —", Modifier.padding(top = 10.dp, bottom = 4.dp))
    ResearchContent()
}

@Composable
private fun PredictionHistory() {
    val ins by EngineHost.v13Insights.collectAsState()
    val rows = remember { mutableStateListOf<PredictionRecordEntity>() }
    var total by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var expl by remember { mutableStateOf<PredictionExplanation?>(null) }
    val scope = rememberCoroutineScope()
    val page = 30
    LaunchedEffect(ins?.count) {
        val first = withContext(Dispatchers.IO) { EngineHost.predictionPage(0, maxOf(page, rows.size)) to EngineHost.predictionTotal() }
        rows.clear(); rows.addAll(first.first); total = first.second
    }
    FCard("Tahmin geçmişi · $total kayıt (sayfalı)") {
        if (rows.isEmpty()) Muted("Henüz tahmin kaydı yok.")
        rows.forEach { r ->
            val ok = r.sideHit
            val res = if (r.actualNumber == null) "bekliyor" else
                "gerçek ${r.actualNumber} ${if (r.numberHit == true) "✓" else "✗"} · yan ${if (ok == true) "✓" else "✗"}"
            Column(Modifier.fillMaxWidth().clickable {
                selected = if (selected == r.sequence) -1 else r.sequence
                if (selected >= 0 && r.mode == "LIVE") scope.launch { expl = withContext(Dispatchers.IO) { EngineHost.explanationFor(r.sequence) } } else expl = null
            }.padding(vertical = 3.dp)) {
                Mono("#${r.sequence + 1} ${r.mode.take(1)} · ${r.predictedNumber} · ${sideTr(r)} · %${(r.confidence * 100).toInt()} → $res",
                    color = when { r.actualNumber == null -> C.orange; r.sideHit == true -> C.lightGreen; else -> C.text })
                if (selected == r.sequence) {
                    Muted("kilit ${r.lockId} · ${r.regime} · entropi ${f3(r.entropy)}")
                    expl?.let { e ->
                        Lines("＋", e.numberPositive.take(3) + e.sidePositive.take(3), C.lightGreen)
                        Lines("－", e.numberNegative.take(3) + e.sideNegative.take(3), C.danger)
                    }
                }
            }
        }
        if (rows.size < total) Text("Daha fazla yükle", color = C.lightBlue, fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp).clickable {
                scope.launch {
                    val more = withContext(Dispatchers.IO) { EngineHost.predictionPage(rows.size, page) }
                    rows.addAll(more)
                }
            })
    }
}

private fun sideTr(r: PredictionRecordEntity): String =
    (if (r.bigSmall == "BIG") "BÜYÜK" else "KÜÇÜK") + "+" + (if (r.oddEven == "ODD") "TEK" else "ÇİFT")

// ============================================================================================ Models
@Composable
private fun ModelsTab(ins: V13Insights?) {
    if (ins == null) { Muted("Hazırlanıyor…"); return }
    Muted("Her model aynı biçimde (olasılık dağılımı) meta-ensemble'a girer; ağırlıklar performans, kalibrasyon, çeşitlilik ve rejime göre yumuşak güncellenir.", Modifier.padding(bottom = 6.dp))
    AXES.forEach { (key, title) ->
        val rows: List<ModelRow> = ins.models[key] ?: emptyList()
        FCard("$title · ${rows.size} model · τ=${f2(ins.tau[key] ?: 1.0)}") {
            TableRow(listOf("Model", "ağ.", "n", "top1", "LL", "son"), listOf(0, 40, 36, 40, 44, 40), header = true)
            rows.forEach { r ->
                val icon = if (r.group == fan.superai.v13.Group.PYTHON) "🐍" else "🔵"
                TableRow(listOf("$icon ${r.name.substringAfter("· ")}".take(26), pct(r.weight), "${r.n}", pct(r.top1), f2(r.logLoss), pct(r.recent)),
                    listOf(0, 40, 36, 40, 44, 40))
            }
            if (rows.isEmpty()) Muted("Bu eksende model yok")
        }
    }
}

// ============================================================================================ Side
@Composable
private fun SideTab(ins: V13Insights?, fin: FinalPrediction?) {
    if (fin == null || ins == null) { Muted("Hazırlanıyor…"); return }
    val sd = fin.sideDistribution
    FCard("Nihai yan (rakamdan türetilmez)") {
        KV("Büyük / Küçük", "BÜYÜK ${pct(sd.bigSmall[1])} · KÜÇÜK ${pct(sd.bigSmall[0])}")
        KV("Tek / Çift", "TEK ${pct(sd.oddEven[1])} · ÇİFT ${pct(sd.oddEven[0])}")
        KV("Seçim", fin.side.display, vColor = C.purple)
        Muted("Birleşik: " + (0 until 4).joinToString(" · ") { "${fan.superai.v13.CombinedSide.fromIndex(it).display} ${pct(sd.combined[it])}" })
    }
    ins.sequences.forEach { sv ->
        val title = AXES.first { it.first == sv.axis }.second
        FCard("$title · dizi analizi") {
            val tot = sv.counts.sum().coerceAtLeast(1.0)
            Muted("Frekans: " + sv.labels.indices.joinToString(" · ") { "${sv.labels[it]} ${pct(sv.counts[it] / tot)}" })
            if (sv.last >= 0) {
                val k = sv.labels.size
                val rowSum = (0 until k).sumOf { sv.transitions[sv.last * k + it] }.coerceAtLeast(1.0)
                Muted("Son: ${sv.labels[sv.last]} · seri ${sv.run} · aynı devam etme oranı ${pct(sv.transitions[sv.last * k + sv.last] / rowSum)} (gözlenen; kumarbaz yanılgısı yok)",
                    Modifier.padding(top = 2.dp))
            }
            val k = sv.labels.size
            Muted("Geçiş matrisi (satır: önceki → sütun: sonraki)", Modifier.padding(top = 6.dp))
            TableRow(listOf("") + sv.labels.map { it.take(10) }, listOf(70) + List(k) { 0 }, header = true)
            for (i in 0 until k) {
                val rs = (0 until k).sumOf { sv.transitions[i * k + it] }.coerceAtLeast(1.0)
                TableRow(listOf(sv.labels[i].take(9)) + (0 until k).map { pct(sv.transitions[i * k + it] / rs) }, listOf(70) + List(k) { 0 })
            }
        }
    }
    ExplanationCards(fin.explanation)
}

// ============================================================================================ Replay
@Composable
private fun ReplayTab() {
    val busy by EngineHost.v13ResearchBusy.collectAsState()
    val out by EngineHost.v13Research.collectAsState()
    FCard("Replay · RESEARCH modu") {
        Muted("Kronolojik: yalnızca geçmiş → tahmin → LOCK → gerçek sonuç → değerlendirme → öğrenme. Sandbox çalışır; canlı state ve overlay etkilenmez.")
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumButton(if (busy == null) "▶ Research Replay" else "⏳ çalışıyor", C.blue, 13) { EngineHost.runResearchReplay() }
            NumButton("⟲ Full Replay (canlı)", C.del, 13) { EngineHost.fullReplay() }
        }
        busy?.let { Text(it, color = C.orange, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Muted("Full Replay yalnızca elle başlatılır: canlı model durumunu baştan kurar (canlı tahmin geçmişi korunur).", Modifier.padding(top = 4.dp))
    }
    val o = out ?: run { Muted("Henüz research replay çalıştırılmadı."); return }
    val r = o.result
    FCard("Replay özeti") {
        KV("Kayıt / tahmin", "${r.total} / ${r.steps.size}")
        KV("Süre", "${o.durationMs / 1000.0} sn")
        KV("Sıra doğru", if (r.orderOk) "evet" else "HAYIR", vColor = if (r.orderOk) C.lightGreen else C.danger)
        KV("Sızıntı kontrolü / ihlal", "${r.leakChecks} / ${r.leakViolations}", vColor = if (r.leakViolations == 0L) C.lightGreen else C.danger)
        KV("Python dahil", if (r.includesPython) "evet" else "hayır")
        KV("Tekrarlanabilirlik parmak izi", java.lang.Long.toHexString(o.fingerprint), mono = true)
        r.errors.take(3).forEach { Text(it, color = C.danger, fontSize = 11.sp) }
    }
    val s = o.summary
    FCard("Metrikler · bootstrap %95 (n=${s.n}, ilk ${s.skipped} ısınma hariç)") {
        TableRow(listOf("Metrik", "değer", "aralık", "baseline"), listOf(0, 52, 100, 56), header = true)
        s.metrics.forEach { m ->
            val better = if (m.higherIsBetter) m.lo > m.baseline else m.hi < m.baseline
            TableRow(listOf(m.name, f3(m.value), "${f3(m.lo)}–${f3(m.hi)}", f3(m.baseline)), listOf(0, 52, 100, 56),
                color = if (better) C.lightGreen else C.text)
        }
        Muted("Yeşil: güven aralığı baseline'ın tamamen iyi tarafında.", Modifier.padding(top = 4.dp))
    }
    FCard("Permutation testi") {
        s.permutations.forEach { p -> KV(p.name, "isabet ${pct(p.observed)} · p=${f3(p.pValue)} · percentile ${pct(p.percentile)}",
            vColor = if (p.pValue < 0.05) C.lightGreen else C.text) }
        Muted("p < 0.05 olmadıkça sonuç şanstan ayırt edilemez.", Modifier.padding(top = 4.dp))
    }
    FCard("Baseline'lar") {
        s.causalBaseline.forEach { (k, v) -> KV("$k · nedensel çoğunluk", pct(v)) }
        KV("Rakam rastgele", "%25 (1/N, N=4)"); KV("Yan rastgele", "%50 · birleşik %25")
    }
}

// ============================================================================================ Calibration
@Composable
private fun CalibrationTab(ins: V13Insights?) {
    if (ins == null) { Muted("Hazırlanıyor…"); return }
    ins.calibration.forEach { c ->
        val title = AXES.first { it.first == c.axis }.second
        FCard("$title · nihai kalibrasyon (n=${c.n})") {
            KV("Doğruluk", pct(c.accuracy)); KV("Güven-isabet boşluğu", f3(c.gap))
            KV("Brier", f4(c.brier)); KV("Log loss", f4(c.logLoss)); KV("Ortalama entropi", f4(c.entropy))
            if (c.bins.isNotEmpty()) {
                TableRow(listOf("güven aralığı", "n", "ort.güven", "isabet"), listOf(0, 40, 70, 56), header = true)
                c.bins.forEach { b -> TableRow(listOf("${pct(b.lo)}–${pct(b.hi)}", "${b.n}", pct(b.meanConf), pct(b.accuracy)), listOf(0, 40, 70, 56),
                    color = if (abs(b.meanConf - b.accuracy) < 0.08) C.text else C.orange) }
            }
        }
    }
}

// ============================================================================================ Diversity
@Composable
private fun DiversityTab(ins: V13Insights?) {
    if (ins == null) { Muted("Hazırlanıyor…"); return }
    ins.diversity.forEach { d ->
        val title = AXES.first { it.first == d.axis }.second
        FCard("$title · ${d.ids.size} model") {
            KV("Nihaiyle ayrışma (EWMA)", pct(d.disagreement)); KV("Ortalama JS çeşitliliği", f4(d.diversity))
            val pairs = ArrayList<Triple<Int, Int, Double>>()
            for (i in d.ids.indices) for (j in i + 1 until d.ids.size) pairs.add(Triple(i, j, d.corr[i][j]))
            if (pairs.isNotEmpty()) {
                Muted("En çok korele çiftler (ağırlık cezası uygulanır):", Modifier.padding(top = 4.dp))
                pairs.sortedByDescending { it.third }.take(5).forEach { (i, j, c) ->
                    Mono("${d.names[i].substringAfter("· ").take(14)} ↔ ${d.names[j].substringAfter("· ").take(14)}  r=${f2(c)} uyum=${pct(d.agree[i][j])}", size = 10)
                }
                Muted("Korelasyon matrisi:", Modifier.padding(top = 6.dp))
                Column {
                    for (i in d.ids.indices) Row {
                        for (j in d.ids.indices) {
                            val v = d.corr[i][j].coerceIn(-1.0, 1.0)
                            val col = if (v >= 0) Color(0xFF1565C0).copy(alpha = (0.15 + 0.85 * v).toFloat()) else Color(0xFFC62828).copy(alpha = (0.15 + 0.85 * -v).toFloat())
                            Box(Modifier.size(if (d.ids.size > 14) 9.dp else 14.dp).padding(0.5.dp).background(col))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================================ Regime
@Composable
private fun RegimeTab(ins: V13Insights?) {
    if (ins == null) { Muted("Hazırlanıyor…"); return }
    val rg = ins.regime
    FCard("Rejim motoru · şu an ${rg.current} (${rg.duration} adım)") {
        TableRow(listOf("Rejim", "adım", "n", "rakam", "top2", "yan"), listOf(0, 48, 44, 48, 48, 48), header = true)
        rg.rows.forEach { r ->
            TableRow(listOf((if (r.current) "▶ " else "  ") + r.name, "${r.steps}", "${r.n}", pct(r.numberAcc), pct(r.top2Acc), pct(r.sideAcc)),
                listOf(0, 48, 44, 48, 48, 48), color = if (r.current) C.lightBlue else C.text)
        }
        Muted("Rejim, son pencerenin geçiş sıklığı ve dengesizliğine göre çevrimiçi kümelemeyle belirlenir; rejim bazlı doğruluk yalnızca gerçek sonuçlardan hesaplanır.", Modifier.padding(top = 4.dp))
    }
    FCard("Rejim geçişleri") {
        val n = ins.regime.rows.size
        TableRow(listOf("") + rg.rows.map { it.name.takeLast(1) }, listOf(40) + List(n) { 0 }, header = true)
        for (i in 0 until n) {
            val rs = (0 until n).sumOf { rg.transitions[i * n + it] }.coerceAtLeast(1.0)
            TableRow(listOf(rg.rows[i].name.takeLast(1)) + (0 until n).map { pct(rg.transitions[i * n + it] / rs) }, listOf(40) + List(n) { 0 })
        }
    }
    FCard("Son rejim değişimleri") {
        if (rg.history.isEmpty()) Muted("Henüz değişim yok")
        rg.history.takeLast(8).reversed().forEach { h -> Mono("${fan.superai.v13.V13.REGIME_NAMES[h[0]]}  #${h[1]}'den ${h[2]} adım", size = 11) }
    }
}

// ============================================================================================ Counterfactual
@Composable
private fun CounterfactualTab(fin: FinalPrediction?) {
    val out by EngineHost.v13Research.collectAsState()
    var res by remember { mutableStateOf<List<CfResult>>(emptyList()) }
    LaunchedEffect(fin?.predictionLockId) {
        res = withContext(Dispatchers.IO) { EngineHost.liveCounterfactual() }
    }
    FCard("Karşı-olgusal · mevcut kilitli tahmin") {
        Muted("Simülasyon: kilitli tahminin girdileri farklı ağırlıklarla yeniden birleştirilir. Gerçek state DEĞİŞMEZ.")
        TableRow(listOf("Senaryo", "rakam", "yan"), listOf(0, 48, 100), header = true)
        res.forEach { r ->
            TableRow(listOf(r.scenario.take(30), "${r.number}${if (r.numberChanged) "*" else ""}", "${r.side.display.replace(" + ", "+")}${if (r.sideChanged) "*" else ""}"),
                listOf(0, 48, 100), color = if (r.numberChanged || r.sideChanged) C.orange else C.text)
        }
        if (res.isEmpty()) Muted("Kilitli tahmin yok")
        Muted("* normal ensemble'dan farklı", Modifier.padding(top = 4.dp))
    }
    out?.let { o ->
        FCard("Replay boyunca senaryo doğruluğu") {
            TableRow(listOf("Senaryo", "n", "rakam", "yan(2)"), listOf(0, 40, 56, 56), header = true)
            o.counterfactual.forEach { c -> TableRow(listOf(c.scenario, "${c.n}", pct(c.numberAcc), pct(c.sideAcc)), listOf(0, 40, 56, 56)) }
            Muted("Python/Kotlin katkısı: 'yok' senaryolarının normalden farkı. Katkı payları her tahminin açıklamasında listelenir.", Modifier.padding(top = 4.dp))
        }
        o.lastCounterfactual.takeIf { it.isNotEmpty() }?.let {
            FCard("Replay son adım senaryoları") {
                it.forEach { r -> KV(r.scenario.take(32), "${r.number} · ${r.side.display.replace(" + ", "+")}") }
            }
        }
    }
}
