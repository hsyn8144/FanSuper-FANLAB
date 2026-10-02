package fan.lightningroulette.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fan.lightningroulette.core.*
import fan.lightningroulette.data.ExperimentE
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.engine.EngineUi
import fan.lightningroulette.lab.LabManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ROB_IDS = LabTabs.ROB_TABS.map { it.first }

@Composable
fun LabScreen(ui: EngineUi) {
    var page by rememberSaveable { mutableStateOf("") }
    var prefill by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = page.isNotEmpty()) { page = "" }
    when {
        page == "builder" -> BuilderPage(prefill, { page = "" }, { page = "queue" })
        page == "queue" -> QueuePage({ page = "" }) { id -> page = "report:$id" }
        page.startsWith("report:") -> ReportPage(page.removePrefix("report:").toLongOrNull() ?: 0L, { page = "" }) { h -> prefill = h; page = "hyp" }
        page == "hyp" -> HypothesisPage({ page = "" }) { h -> prefill = h; page = "builder" }
        else -> LabMain(ui, { page = it })
    }
}

@Composable
private fun JobStrip() {
    val lab by LabManager.ui.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        confirmButton = { TextButton(onClick = { confirm = false; LabManager.cancelCurrent() }) { Text("Deneyi iptal et") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Devam et") } },
        title = { Text("Deney iptal edilsin mi?") }, text = { Text("Kısmi sonuç CANCELLED olarak saklanır; silinmez.") })
    if (lab.running) {
        LrCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("▶ ${lab.jobCode} · %${lab.progress}", C.blue, 12, true, true)
                    T(lab.jobTitle + if (lab.detail.isNotEmpty()) " · " + lab.detail else "", C.dim, 10)
                }
                LrButton("İptal", { confirm = true }, "danger")
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.card2)) { Box(Modifier.fillMaxWidth(lab.progress / 100f).height(6.dp).background(C.blue)) }
        }
    }
    if (lab.baseBusy) LrCard { T("⏳ LAB taban koşusu hesaplanıyor · %${lab.baseProgress}", C.blue, 12, true, true); Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.card2)) { Box(Modifier.fillMaxWidth(lab.baseProgress / 100f).height(6.dp).background(C.blue)) } }
}

@Composable
private fun LabMain(ui: EngineUi, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var rob by rememberSaveable { mutableStateOf(0) }
    val ctxState by rememberLabCtx()
    val ctx = ctxState
    val lab by LabManager.ui.collectAsState()
    val appCtx = LocalContext.current
    val tabId = LabTabs.TABS[tab].first
    val sub = if (tabId == "robustness") ROB_IDS[rob] else ""
    val secs by produceState<List<Sec>?>(null, ctx, tab, rob) {
        value = null
        val c = ctx
        if (c != null && tabId != "experiments") value = withContext(Dispatchers.Default) { try { LabManager.cachedSecs(tabId, sub) { LabTabs.build(c, tabId, sub) } } catch (e: Throwable) { listOf(S.text("Hata", "${Codes.REPLAY}: ${e.message}", "bad")) } }
        else if (c != null) value = withContext(Dispatchers.Default) { try { LabManager.cachedSecs(tabId, sub) { LabTabs.build(c, tabId, sub) } } catch (e: Throwable) { emptyList() } }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T("🧪 LAB V2", C.text, 18, true); T("araştırma motoru · canlı modeli değiştiremez", C.dim, 10) }
            Pill("LAB ≠ LIVE", "purple")
        }
        if (ctx != null) {
            val seg = LabRunner.segmentLen(ctx.n)
            Row { Pill(ctx.datasetLabel + " · " + S.th(ctx.n) + " spin", "line"); Pill("Train ${S.th(ctx.n - 2 * seg)} · Val ${S.th(seg)} · OOS ${S.th(seg)}", "line") }
        }
        SegTabs(LabTabs.TABS.map { it.second }, tab, { tab = it })
        if (tabId == "robustness") SegTabs(LabTabs.ROB_TABS.map { it.second }, rob, { rob = it })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            JobStrip()
            if (ui.spinCount < 200) Banner("LAB için en az 200 spin gerekir (şu an ${ui.spinCount}). Veri sekmesinden örnek veri yükleyebilir ya da dosya içe aktarabilirsin.", "info")
            else if (ctx == null) {
                LrCard(title = "LAB TABAN KOŞUSU") {
                    T("LAB, canlı yapılandırmayla kronolojik bir walk-forward koşusu yapar (Train → Validation → OOS; shuffle yok). Sonuç dataset sürümüne bağlı olarak önbelleğe alınır.", C.text, 12)
                    VGap(8)
                    LrButton(if (lab.baseBusy) "Hesaplanıyor…" else "Hesapla", { LabManager.requestBase(appCtx) }, "primary", !lab.baseBusy, Modifier.fillMaxWidth())
                }
            } else {
                if (tabId == "experiments") ExperimentsTab(ctx, secs ?: emptyList(), go)
                else {
                    val s = secs
                    if (s == null) T("⏳ hesaplanıyor…", C.dim, 12) else for (sec in s) SecView(sec)
                }
                TabActions(tabId, go)
            }
        }
    }
}

@Composable
private fun TabActions(tabId: String, go: (String) -> Unit) {
    val appCtx = LocalContext.current
    val lab by LabManager.ui.collectAsState()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (tabId) {
            "overview" -> {
                LrButton("+ Yeni deney", { go("builder") }, "primary", true, Modifier.weight(1f))
                LrButton("Kuyruk", { go("queue") }, "ghost", true, Modifier.weight(1f))
                LrButton("Hipotez", { go("hyp") }, "ghost", true, Modifier.weight(1f))
            }
            "replay" -> {
                LrButton("Determinizm testi", { LabManager.enqueue("DETERMINISM", LabManager.baseConfig(), "Determinizm testi (aynı veri + seed → aynı sonuç)"); LabManager.pump(appCtx); go("queue") }, "primary", !lab.running, Modifier.weight(1f))
                LrButton("Taban koşusunu yenile", { Engine.scope.launch { val d = Engine.dataset; if (d != null) { Engine.dao.deleteStatePrefix("lab.base"); LabManager.invalidate(); LabManager.requestBase(appCtx) } } }, "ghost", !lab.baseBusy, Modifier.weight(1f))
            }
            "counterfactual", "ablation", "robustness" -> {
                LrButton(if (tabId == "ablation") "Ablation turunu kuyruğa ekle" else "Robustness paketini çalıştır", {
                    LabManager.enqueue("SUITE", LabManager.baseConfig(), "Robustness paketi (ablation + karşı-olgusal + hassasiyet + stres)"); LabManager.pump(appCtx); go("queue")
                }, "primary", !lab.running, Modifier.fillMaxWidth())
            }
            else -> {}
        }
    }
}

private fun fmtTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(ms))

@Composable
private fun ClsPill(cls: String) { if (cls.isNotEmpty()) ClassBadge(cls) }

@Composable
private fun ExperimentsTab(ctx: LabCtx, secs: List<Sec>, go: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(0) }
    val all by Engine.dao.experimentsFlow().collectAsState(initial = emptyList())
    for (s in secs) SecView(s)
    SegTabs(listOf("Hepsi", "Tamamlanan", "Başarısız hafıza", "Kuyrukta"), filter, { filter = it })
    val rows = all.filter {
        when (filter) {
            1 -> it.status == "DONE"
            2 -> it.status == "REJECTED" || it.status == "CANCELLED" || (it.status == "DONE" && it.cls in listOf("C", "D", "F", "O", "L", "S"))
            3 -> it.status == "QUEUED" || it.status == "RUNNING"
            else -> true
        }
    }
    LrCard(title = "DENEYLER · ${rows.size}", right = "dokun → rapor") {
        if (rows.isEmpty()) T("Henüz deney yok. “+ Yeni deney” ile oluştur ya da Robustness paketini çalıştır.", C.dim, 12)
        for (e in rows) ExpRowView(e) { go("report:${e.id}") }
        T("Başarısız deneyler silinmez (başarısız hipotez hafızası). Leakage şüphesi (L) olan deney sonucu geçersiz sayılır; kaydı kalır.", C.dim2, 10)
    }
    LrButton("+ Yeni deney", { go("builder") }, "primary", true, Modifier.fillMaxWidth())
}

@Composable
private fun ExpRowView(e: ExperimentE, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(e.code, C.dim, 10, true, true, modifier = Modifier.weight(1f))
            Pill(e.status, when (e.status) { "DONE" -> "ok"; "RUNNING" -> "blue"; "REJECTED" -> "bad"; "CANCELLED" -> "warn"; else -> "line" })
            ClsPill(e.cls)
        }
        T(e.hypothesis, C.text, 12, true)
        val cfg = try { ExpConfig.fromMap(Json.parse(e.paramsJson).jmap()).label() } catch (_: Exception) { "" }
        T(cfg + (if (e.n > 0) " · N ${S.th(e.n)} · Δ ${S.sg(e.deltaPp)}" else "") + (if (e.reason.isNotEmpty()) " · ${e.reason}" else ""), C.dim2, 10)
        Divider1()
    }
}

// ───────────────────────── deney oluşturucu
@Composable
private fun BuilderPage(prefill: String, onBack: () -> Unit, onQueued: () -> Unit) {
    val appCtx = LocalContext.current
    var hyp by rememberSaveable { mutableStateOf(prefill) }
    var cands by rememberSaveable { mutableStateOf(setOf(5)) }
    var ks by rememberSaveable { mutableStateOf(setOf(0)) }
    var wins by rememberSaveable { mutableStateOf(setOf(300)) }
    var dir by rememberSaveable { mutableStateOf("bi") }
    var decay by rememberSaveable { mutableStateOf(20) }
    var feats by rememberSaveable { mutableStateOf(BrainConfig.ALL_FEATURES.toList()) }
    var kw by rememberSaveable { mutableStateOf(100) }
    var seed by rememberSaveable { mutableStateOf("42") }
    val oosUsed by produceState(0) { value = withContext(Dispatchers.Default) { Engine.dao.experiments().count { it.status == "DONE" } } }
    val combos = cands.size * ks.size * wins.size
    val py = kw < 100
    @Composable fun multi(label: String, options: List<Pair<String, Int>>, sel: Set<Int>, set: (Set<Int>) -> Unit) {
        T(label, C.dim, 11, true, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { for ((lab, v) in options) { val on = v in sel; Pill(lab + if (on) " ✓" else "", if (on) "blue" else "line", Modifier.clickable { set(if (on && sel.size > 1) sel - v else sel + v) }) } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { LrButton("←", onBack, "ghost"); HGap(10); T("Yeni deney", C.text, 17, true) }
        LrCard(title = "HİPOTEZ") {
            OutlinedTextField(value = hyp, onValueChange = { hyp = it }, label = { Text("Hipotez (boşsa parametrelerden üretilir)") }, modifier = Modifier.fillMaxWidth())
        }
        LrCard(title = "ADAY SAYISI · K · YÖN") {
            multi("Aday sayısı (çoklu seçim)", listOf("3" to 3, "4" to 4, "5" to 5), cands) { cands = it }
            multi("k (0 = otomatik k1/k2/k3)", listOf("oto" to 0, "k1" to 1, "k2" to 2, "k3" to 3), ks) { ks = it }
            Row { for ((l, v) in listOf("↔" to "bi", "L" to "L", "R" to "R")) Pill(l, if (dir == v) "blue" else "line", Modifier.clickable { dir = v }) }
        }
        LrCard(title = "FEATURE’LAR") {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { for (f in listOf("sector", "region", "neighbor", "frequency")) { val on = f in feats; Pill(f + if (on) " ✓" else "", if (on) "blue" else "line", Modifier.clickable { feats = if (on) feats - f else feats + f }) } }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { for (f in listOf("pattern", "transition", "table", "ML")) { val on = f in feats; Pill(f + if (on) " ✓" else "", if (on) "blue" else "line", Modifier.clickable { feats = if (on) feats - f else feats + f }) } }
            T("wheel her zaman açıktır. Seçilen gruplar modele girer.", C.dim2, 10)
        }
        LrCard(title = "RECENCY / DECAY") {
            multi("Pencere (çoklu seçim)", listOf("50" to 50, "100" to 100, "200" to 200, "300" to 300, "500" to 500), wins) { wins = it }
            Row { for ((l, v) in listOf("λ 0,01" to 10, "λ 0,02" to 20, "λ 0,05" to 50)) Pill(l, if (decay == v) "blue" else "line", Modifier.clickable { decay = v }) }
        }
        LrCard(title = "ENSEMBLE") {
            T("Kotlin / Python ağırlığı", C.dim, 11, true)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { for ((l, v) in listOf("Yalnız Kotlin" to 100, "80/20" to 80, "60/40" to 60, "50/50" to 50, "40/60" to 40, "20/80" to 20)) Pill(l, if (kw == v) "blue" else "line", Modifier.clickable { kw = v }) }
            if (py) Banner("Python dahil: önce 8 üye parça parça hesaplanır (daha uzun sürer, arka planda).", "info")
        }
        LrCard(title = "SEED") { OutlinedTextField(value = seed, onValueChange = { seed = it.filter { c -> c.isDigit() }.take(9) }, label = { Text("Seed") }, singleLine = true, modifier = Modifier.fillMaxWidth()); T("Aynı dataset + model + parametre + seed + kod sürümü aynı sonucu üretir.", C.dim2, 10) }
        LrCard(title = "ÖZET VE OOS KORUMASI") {
            KV("Kombinasyon sayısı", "$combos")
            KV("Tahmini süre", "≈ ${combos * (if (py) 25 else 4)} sn (cihaza göre değişir)")
            KV("OOS’ta raporlanan deney sayısı", "$oosUsed", if (oosUsed > 20) "warn" else "")
            T("OOS yalnızca raporlama içindir; bu OOS ile tekrar model seçimi engellenir. Çok deney = data-snooping riski.", C.dim2, 10)
        }
        if (combos > 24) Banner("En fazla 24 kombinasyon kuyruğa eklenebilir.", "warn")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val make: (Int) -> Unit = { prio ->
                val sd = seed.toLongOrNull() ?: 42L
                for (c in cands.sorted()) for (k in ks.sorted()) for (w in wins.sorted()) {
                    val h = hyp.ifBlank { "w$w · ${if (k == 0) "k-oto" else "k$k"} · c$c · $dir" }
                    LabManager.enqueue("RUN", ExpConfig(h, w, k, c, Dir.of(dir), feats.toSet(), decay / 1000.0, kw / 100.0, sd), h, prio)
                }
                LabManager.pump(appCtx); onQueued()
            }
            LrButton("Kuyruğa ekle", { make(0) }, "ghost", combos in 1..24 && feats.isNotEmpty(), Modifier.weight(1f))
            LrButton("Hemen çalıştır", { make(10) }, "primary", combos in 1..24 && feats.isNotEmpty(), Modifier.weight(1f))
        }
    }
}

// ───────────────────────── kuyruk
@Composable
private fun QueuePage(onBack: () -> Unit, open: (Long) -> Unit) {
    val appCtx = LocalContext.current
    val all by Engine.dao.experimentsFlow().collectAsState(initial = emptyList())
    val lab by LabManager.ui.collectAsState()
    val queued = all.filter { it.status == "QUEUED" }.sortedWith(compareByDescending<ExperimentE> { it.priority }.thenBy { it.id })
    val done = all.filter { it.status != "QUEUED" && it.status != "RUNNING" }.take(10)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { LrButton("←", onBack, "ghost"); HGap(10); T("Deney kuyruğu", C.text, 17, true) }
        JobStrip()
        LrCard(title = "SIRADAKİLER · ${queued.size}") {
            if (queued.isEmpty()) T("Kuyruk boş.", C.dim, 12)
            for ((i, e) in queued.withIndex()) Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                T("${i + 1}", C.dim, 12, true, true, modifier = Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f)) { T(e.code, C.dim, 10, true, true); T(e.hypothesis, C.text, 12, true) }
                LrButton("Çıkar", { LabManager.delete(e) }, "ghost")
            }
            VGap(6)
            LrButton("Kuyruğu çalıştır", { LabManager.pump(appCtx) }, "primary", queued.isNotEmpty() && !lab.running, Modifier.fillMaxWidth())
        }
        LrCard(title = "SON TAMAMLANANLAR") {
            for (e in done) ExpRowView(e) { open(e.id) }
            if (done.isEmpty()) T("—", C.dim, 12)
        }
        Banner("Checkpoint düzenli aralıklarla yazılır; uygulama kapanırsa son checkpoint’ten devam edilir (resume). Bildirim izni yoksa ilerleme yalnızca uygulama içinde görünür. İptal edilen deney CANCELLED olarak saklanır.", "info")
    }
}

// ───────────────────────── rapor
@Composable
private fun ReportPage(id: Long, onBack: () -> Unit, deriveHypothesis: (String) -> Unit) {
    val appCtx = LocalContext.current
    val e by produceState<ExperimentE?>(null, id) { value = withContext(Dispatchers.Default) { Engine.dao.experiment(id) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val ex = e
        if (uri != null && ex != null) Engine.scope.launch {
            val bytes = Json.stringify(mapOf("format" to "LRAI-EXP-1", "code" to ex.code, "kind" to ex.kind, "status" to ex.status, "cls" to ex.cls, "hypothesis" to ex.hypothesis, "params" to Json.parse(ex.paramsJson), "result" to (try { Json.parse(ex.resultJson) } catch (_: Exception) { null }), "reason" to ex.reason, "createdAt" to ex.createdAt)).toByteArray(Charsets.UTF_8)
            appCtx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            Engine.notify("Rapor dışa aktarıldı")
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { LrButton("←", onBack, "ghost"); HGap(10); T("Deney raporu", C.text, 17, true) }
        val ex = e
        if (ex == null) { T("⏳ yükleniyor…", C.dim, 12); return@Column }
        val res = ExpResult.fromJson(ex.resultJson)
        T(ex.code, C.gold, 12, true, true, modifier = Modifier.padding(top = 6.dp))
        T(ex.hypothesis, C.text, 14, true)
        if (res == null) {
            Banner("Durum: ${ex.status}${if (ex.reason.isNotEmpty()) " · ${ex.reason}" else ""}. Bu deney için sonuç kaydı yok (kuyrukta/çalışıyor/iptal).", "info")
        } else for (s in LabReport.build(res, ex.code, ex.status, ex.hypothesis, ex.createdAt)) SecView(s)
        VGap(6)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            LrButton("JSON dışa aktar", { pick.launch(ex.code + ".json") }, "ghost", true, Modifier.weight(1f))
            LrButton("Yeniden üret", {
                val cfg = try { ExpConfig.fromMap(Json.parse(ex.paramsJson).jmap()) } catch (_: Exception) { null }
                if (cfg != null) { LabManager.enqueue(if (ex.kind == "SUITE") "SUITE" else "RUN", cfg, "yeniden üretim · " + ex.code); LabManager.pump(appCtx); Engine.notify("Yeniden üretim kuyruğa eklendi: aynı hash beklenir") }
            }, "ghost", ex.kind != "DETERMINISM", Modifier.weight(1f))
            LrButton("Hipotez türet", { deriveHypothesis("türev: " + ex.hypothesis) }, "ghost", true, Modifier.weight(1f))
        }
        T("Oluşturma: ${fmtTime(ex.createdAt)} · bitiş ${fmtTime(ex.finishedAt)} · seed ${ex.seed} · kod ${ex.codeVersion} · hash ${ex.paramHash}", C.dim2, 10, modifier = Modifier.padding(top = 6.dp))
    }
}

// ───────────────────────── hipotez üretici ve başarısız hipotez hafızası
@Composable
private fun HypothesisPage(onBack: () -> Unit, create: (String) -> Unit) {
    val appCtx = LocalContext.current
    val all by Engine.dao.experimentsFlow().collectAsState(initial = emptyList())
    val failed = all.filter { it.status == "REJECTED" || it.status == "CANCELLED" || (it.status == "DONE" && it.cls in listOf("C", "D", "F", "O", "L", "S")) }
    val ctxState by rememberLabCtx()
    val ctx = ctxState
    val ideas by produceState(listOf("⏳ öneriler hesaplanıyor…"), ctx) {
        value = withContext(Dispatchers.Default) {
            val l = ArrayList<String>()
            if (ctx != null) {
                try {
                    for (t in ExposeAnalysis.sectorTests(ctx)) if (t.p < 0.05 && t.cls != "S") l.add("Sektör ‘${t.name}’ (Δ ${S.sg(t.delta, 2, t.unit)}, p ${S.f(t.p, 3)}) — bağımsız pencerede tutuyor mu?")
                    ctx.suites?.ablation?.minByOrNull { it.second.delta }?.let { l.add("‘${it.first}’ çıkarılınca en büyük düşüş (${S.sg(it.second.delta)}): bu grubu tek başına güçlendir") }
                } catch (_: Exception) { }
            }
            if (l.isEmpty()) l.add("Hipotez adayı yok: LAB bulgularında Bonferroni sonrası ayrışma görülmedi. Yine de kendi hipotezini oluşturabilirsin.")
            l
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) Engine.scope.launch {
            val rows = Engine.dao.experiments().filter { it.status != "QUEUED" }.map { mapOf("code" to it.code, "hypothesis" to it.hypothesis, "params" to it.paramsJson, "status" to it.status, "cls" to it.cls, "delta" to it.deltaPp, "n" to it.n, "reason" to it.reason, "createdAt" to it.createdAt, "datasetHash" to it.datasetHash) }
            appCtx.contentResolver.openOutputStream(uri)?.use { it.write(Json.stringify(mapOf("format" to "LRAI-FAILED-MEMORY-1", "items" to rows)).toByteArray(Charsets.UTF_8)) }
            Engine.notify("Hafıza dışa aktarıldı")
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { LrButton("←", onBack, "ghost"); HGap(10); T("Hipotez üretici", C.text, 17, true) }
        Banner("Bağımsız OOS: yeni hipotez, kaynağın seçildiği OOS ile değerlendirilmez; sonuçlar yalnızca raporlama içindir ve aynı OOS’ta tekrar model seçimi yapılmaz.", "info")
        LrCard(title = "ÖNERİLEN HİPOTEZLER") {
            for (i in ideas) Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                T(i, C.text, 12)
                if (!i.startsWith("Hipotez adayı yok") && !i.startsWith("⏳")) LrButton("Deney oluştur", { create(i.take(80)) }, "ghost", true, Modifier.padding(top = 4.dp))
            }
            LrButton("Boş hipotezle başla", { create("") }, "ghost", true, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        LrCard(title = "BAŞARISIZ HİPOTEZ HAFIZASI · ${failed.size}", right = "silinemez") {
            if (failed.isEmpty()) T("Henüz başarısız kayıt yok.", C.dim, 12)
            for (e in failed) Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { T(e.code, C.dim, 10, true, true, modifier = Modifier.weight(1f)); ClsPill(e.cls) }
                T(e.hypothesis, C.text, 12, true)
                T((try { ExpConfig.fromMap(Json.parse(e.paramsJson).jmap()).label() } catch (_: Exception) { "" }) + " · N ${S.th(e.n)} · Δ ${S.sg(e.deltaPp)} · ${fmtTime(e.createdAt)}", C.dim2, 10)
                T("Neden: " + (e.reason.ifEmpty { "OOS’ta tutmadı" }), C.warn, 10)
                Divider1()
            }
            T("Aynı hipotez yeniden önerilmeden önce uyarı verilir. Kayıtlar kilitlidir; yalnızca dışa aktarılabilir.", C.dim2, 10)
        }
        LrButton("Hafızayı dışa aktar (JSON)", { pick.launch("lab_hafiza.json") }, "ghost", true, Modifier.fillMaxWidth())
    }
}

/** Arayüzden analiz erişimi (sektör testleri çağrıyı bağlamdan yapar; ağır olduğu için remember ile bir kez). */
object ExposeAnalysis {
    fun sectorTests(c: LabCtx): List<fan.lightningroulette.core.AnSectorRow> = AnBridge.sectorTests(c.values, c.sectors)
}
