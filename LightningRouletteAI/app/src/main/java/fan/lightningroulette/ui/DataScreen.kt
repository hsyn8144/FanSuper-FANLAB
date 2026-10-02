package fan.lightningroulette.ui

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fan.lightningroulette.core.ImportReport
import fan.lightningroulette.core.S
import fan.lightningroulette.data.SpinE
import fan.lightningroulette.engine.DataOps
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.engine.EngineUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(ms: Long): String = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.US).format(Date(ms))

@Composable
fun DataScreen(ui: EngineUi) {
    var sub by rememberSaveable { mutableStateOf(0) }
    var wizard by rememberSaveable { mutableStateOf(false) }
    if (wizard) { BackHandler { wizard = false }; ImportWizard({ wizard = false }, { wizard = false }); return }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        T("🗂 Veri", C.text, 18, true)
        SegTabs(listOf("Spinler", "İçe aktar", "Sürümler", "Dışa aktar"), sub, { sub = it })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (sub) { 0 -> SpinsTab(ui); 1 -> ImportTab(ui) { wizard = true }; 2 -> VersionsTab(ui); else -> ExportTab(ui) }
        }
    }
}

// ───────────────────────── Spinler
@Composable
private fun SpinsTab(ui: EngineUi) {
    var filter by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var impactFor by remember { mutableStateOf<Set<Long>?>(null) }
    var editId by remember { mutableStateOf<Long?>(null) }
    val size = Engine.settings.pageSize
    val filters = listOf("Hepsi" to "ALL", "LIVE" to "LIVE", "IMP" to "IMPORT", "SYN" to "SAMPLE")
    val rows by produceState<List<SpinE>>(emptyList(), filter, query, page, ui.tick, ui.spinCount) { value = withContext(Dispatchers.Default) { DataOps.page(filters[filter].second, query, page, size) } }
    val total = ui.spinCount
    SegTabs(filters.map { it.first }, filter, { filter = it; page = 0; selected = emptySet() })
    OutlinedTextField(value = query, onValueChange = { query = it.take(8); page = 0 }, label = { Text("Ara: sayı (0–36) veya #kimlik") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (ui.synthetic) Banner("Bu dataset SENTETİK (SYNTHETIC_IMPORT): zaman damgaları yapay, 1’er dakika artırılmıştır; gerçek zaman gibi yorumlanmaz.", "warn")
    if (selected.isNotEmpty()) LrCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("${selected.size} seçili", C.text, 13, true, modifier = Modifier.weight(1f))
            LrButton("Sil", { impactFor = selected }, "danger"); HGap(6)
            LrButton("Düzenle", { editId = selected.first() }, "ghost", selected.size == 1); HGap(6)
            LrButton("İptal", { selected = emptySet() }, "ghost")
        }
    }
    LrCard(title = "SPİNLER · toplam ${S.th(total)} · sayfa ${page + 1}", right = "uzun bas → seç") {
        if (rows.isEmpty()) T("Kayıt yok.", C.dim, 12)
        for (r in rows) {
            val on = r.id in selected
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) C.blueDeep.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent)
                .pointerInput(r.id, selected) { detectTapGestures(onLongPress = { selected = selected + r.id }, onTap = { if (selected.isNotEmpty()) selected = if (on) selected - r.id else selected + r.id }) }
                .padding(vertical = 5.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T("#${r.id}", C.dim, 11, mono = true, modifier = Modifier.weight(1f))
                NumChip(r.value, 28.dp, 12)
                T(fmt(r.tsMs), C.dim, 10, mono = true, align = TextAlign.End, modifier = Modifier.weight(2f))
                Pill(when (r.source) { "LIVE" -> "LIVE"; "IMPORT" -> "IMP"; "SAMPLE" -> "SYN"; else -> r.source.take(4) }, if (r.tsType != "REAL") "warn" else "line", Modifier.padding(start = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LrButton("◀", { if (page > 0) page-- }, "ghost", page > 0, Modifier.weight(1f))
            LrButton("▶", { if ((page + 1) * size < total) page++ }, "ghost", (page + 1) * size < total, Modifier.weight(1f))
        }
        T("Arayüz/veritabanı EN YENİ → EN ESKİ gösterir; analiz ve replay EN ESKİ → EN YENİ okur. Sayfa boyutu Ayarlar’dan değişir.", C.dim2, 10, modifier = Modifier.padding(top = 6.dp))
    }
    val ids = impactFor
    if (ids != null) {
        var ack by remember { mutableStateOf(false) }
        val impact by produceState<DataOps.Impact?>(null, ids) { value = withContext(Dispatchers.Default) { DataOps.impactOf(ids) } }
        AlertDialog(onDismissRequest = { impactFor = null },
            confirmButton = { TextButton(enabled = ack, onClick = { val s = ids; impactFor = null; selected = emptySet(); Engine.scope.launch { DataOps.deleteSpins(s) } }) { Text("Sil ve replay başlat") } },
            dismissButton = { TextButton(onClick = { impactFor = null }) { Text("Vazgeç") } },
            title = { Text("Silme etkisi") },
            text = {
                Column {
                    val im = impact
                    Text(if (im == null) "hesaplanıyor…" else "${im.spins} spin silinecek (ilk indeks ${im.firstIndex} / ${im.total}).\nBu spinden sonraki her şey yeniden hesaplanmalı: ≈ ${im.predictions} tahmin + değerlendirme, model durumu ve LAB önbelleği geçersiz olur. Tam replay arka planda ilerleme göstererek yapılır; işlem tek transaction’dır, hata olursa geri alınır.")
                    Row(Modifier.clickable { ack = !ack }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = ack, onCheckedChange = { ack = it }); Text("Anladım") }
                }
            })
    }
    val eid = editId
    if (eid != null) {
        var v by remember { mutableStateOf("") }
        val ok = v.toIntOrNull()?.let { it in 0..36 } == true
        AlertDialog(onDismissRequest = { editId = null },
            confirmButton = { TextButton(enabled = ok, onClick = { val n = v.toInt(); editId = null; selected = emptySet(); Engine.scope.launch { DataOps.editSpin(eid, n) } }) { Text("Kaydet ve replay başlat") } },
            dismissButton = { TextButton(onClick = { editId = null }) { Text("Vazgeç") } },
            title = { Text("Spin #$eid düzenle") },
            text = { Column { Text("Düzenleme de silme gibi bağımlı tahmin/replay/model durumunu geçersiz kılar.\n"); OutlinedTextField(value = v, onValueChange = { v = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Yeni değer (0–36)") }, singleLine = true) } })
    }
}

// ───────────────────────── İçe aktar
@Composable
private fun ImportTab(ui: EngineUi, openWizard: () -> Unit) {
    LrCard(title = "DOSYADAN İÇE AKTAR") {
        T("CSV / TXT / JSON. Önce sütun eşleme ve doğrulama raporu gösterilir; hiçbir şey rapor onaylanmadan kaydedilmez. Geniş depolama izni istenmez (Android dosya seçicisi).", C.text, 12)
        VGap(8)
        LrButton("Dosya seç ve doğrula", openWizard, "primary", true, Modifier.fillMaxWidth())
    }
    LrCard(title = "SIRA VE ZAMAN") {
        T("Dosyanın hangi sırada olduğunu sen bildirirsin; bu sıra korunur. Gerçek zaman damgası yoksa başlangıç zamanı verilir, kayıtlar 1’er dakika artırılır ve timestamp_type = SYNTHETIC_IMPORT işaretlenir (yapay zaman gerçek zaman gibi yorumlanmaz).", C.text, 12)
    }
    LrCard(title = "BİRLEŞTİRME") { T("Yeni dataset olarak aç ya da mevcut dataset’e ekle (her ikisinde de yeni sürüm). Overlap/duplicate denetimi ve içe aktarma kimliği her zaman açıktır.", C.text, 12) }
    val batches by produceState<List<fan.lightningroulette.data.ImportBatchE>>(emptyList(), ui.tick) { value = withContext(Dispatchers.Default) { Engine.dao.batches(10) } }
    LrCard(title = "SON İÇE AKTARMALAR") {
        if (batches.isEmpty()) T("Henüz yok.", C.dim, 12)
        for (b in batches) KV("#${b.id} · ${b.fileName}", "${b.newCount} yeni · ${b.invalid} geçersiz · ${b.duplicate} yinelenen", "", fmt(b.createdAt) + " · " + b.format + if (b.synthetic) " · SYNTHETIC_IMPORT" else "")
    }
}

@Composable
fun ImportWizard(onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(0L) }
    var order by remember { mutableStateOf("AUTO") }
    var mode by remember { mutableStateOf(if (Engine.dataset == null) "NEW_DATASET" else "APPEND") }
    var name by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<ImportReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Engine.scope.launch {
            try {
                busy = true; err = ""; report = null
                var nm = "dosya"
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) nm = c.getString(i) ?: nm } }
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                if (bytes.size > 30_000_000) throw IllegalStateException("dosya çok büyük (30 MB üstü)")
                fileName = nm; size = bytes.size.toLong(); text = String(bytes, Charsets.UTF_8)
            } catch (e: Throwable) { err = "${fan.lightningroulette.core.Codes.IMP}: ${e.message}" }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { LrButton("←", onCancel, "ghost"); HGap(10); T("İçe aktar", C.text, 17, true) }
        if (err.isNotEmpty()) Banner(err, "bad")
        LrCard(title = "1 · DOSYA") {
            if (text == null) T("Henüz dosya seçilmedi.", C.dim, 12) else { KV("Dosya", fileName, mono = false); KV("Boyut", "${size / 1024} KB · ${text!!.lines().size} satır") }
            VGap(6)
            LrButton(if (text == null) "Dosya seç" else "Başka dosya seç", { pick.launch(arrayOf("*/*")) }, "primary", !busy, Modifier.fillMaxWidth())
        }
        if (text != null) {
            LrCard(title = "2 · SIRA") {
                Row { for ((l, v) in listOf("Otomatik" to "AUTO", "Eski → yeni" to "OLD_FIRST", "Yeni → eski" to "NEW_FIRST")) Pill(l, if (order == v) "blue" else "line", Modifier.clickable { order = v; report = null }) }
                T("Dosyanın sırasını sen bildirirsin. Arayüzde en yeni üstte, analizde en eski önce kullanılır.", C.dim2, 10)
            }
            LrCard(title = "3 · BİRLEŞTİRME") {
                Row {
                    Pill("Yeni dataset", if (mode == "NEW_DATASET") "blue" else "line", Modifier.clickable { mode = "NEW_DATASET"; report = null })
                    if (Engine.dataset != null) Pill("Mevcut dataset’e ekle", if (mode == "APPEND") "blue" else "line", Modifier.clickable { mode = "APPEND"; report = null })
                }
                if (mode == "NEW_DATASET") OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Dataset adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                T("Her iki durumda da yeni sürüm oluşur; overlap ve duplicate denetimi açıktır.", C.dim2, 10)
            }
            LrButton(if (busy) "Doğrulanıyor…" else "Doğrula", { Engine.scope.launch { busy = true; try { report = DataOps.analyse(text!!, fileName, order, mode) } catch (e: Throwable) { err = "${fan.lightningroulette.core.Codes.IMP}: ${e.message}" }; busy = false } }, "primary", !busy, Modifier.fillMaxWidth())
        }
        val r = report
        if (r != null) {
            LrCard(title = "4 · DOĞRULAMA RAPORU · ${r.format}") {
                KV("TOTAL", S.th(r.total)); KV("VALID", S.th(r.valid), "ok"); KV("INVALID", S.th(r.invalid), if (r.invalid > 0) "warn" else ""); KV("DUPLICATE", S.th(r.duplicate), if (r.duplicate > 0) "warn" else ""); KV("NEW", S.th(r.newCount), "gold")
                if (r.overlap > 0) KV("Overlap (mevcut son kayıtlarla)", "${r.overlap}", "warn", "örtüşen kayıtlar atlanır")
                if (r.syntheticTs) Banner("Gerçek zaman damgası yok: kayıtlara 1’er dakika aralıkla SYNTHETIC_IMPORT zamanı verilecek.", "warn")
                if (r.reordered > 0) Banner("Bildirilen sıra ile zaman damgaları çelişiyor (${r.reordered} kayıt yeniden sıralandı). Karar sende; gerekirse sırayı değiştirip tekrar doğrula.", "warn")
                for (n in r.notes) T("• $n", C.dim, 11)
            }
            if (r.invalidRows.isNotEmpty()) LrCard(title = "SORUNLU SATIRLAR · ${r.invalidRows.size}") {
                for (row in r.invalidRows.take(40)) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { T("${row.line}", C.dim, 10, mono = true, modifier = Modifier.weight(0.5f)); T(row.raw.take(24), C.text, 11, mono = true, modifier = Modifier.weight(1.5f)); T(row.reason ?: "", C.warn, 10, modifier = Modifier.weight(2f)) }
                if (r.invalidRows.size > 40) T("… ve ${r.invalidRows.size - 40} satır daha", C.dim2, 10)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LrButton("İptal (hiçbir şey yazılmaz)", onCancel, "ghost", true, Modifier.weight(1f))
                LrButton("Onayla · ${r.newCount} yeni kayıt", { Engine.scope.launch { busy = true; val res = DataOps.applyImport(r, fileName, mode, name); busy = false; if (res == "ok") withContext(Dispatchers.Main) { onDone() } else err = res } }, "ok", r.newCount > 0 && !busy, Modifier.weight(1f))
            }
        }
    }
}

// ───────────────────────── Sürümler
@Composable
private fun VersionsTab(ui: EngineUi) {
    val ds by Engine.dao.datasetsFlow().collectAsState(initial = emptyList())
    var confirm by remember { mutableStateOf<Long?>(null) }
    var cmp by remember { mutableStateOf<String?>(null) }
    LrCard(title = "DATASET SÜRÜMLERİ") {
        if (ds.isEmpty()) T("Dataset yok.", C.dim, 12)
        for (d in ds.reversed()) key(d.id) {
            val count by produceState(0, d.id, ui.tick) { value = withContext(Dispatchers.Default) { Engine.dao.spinCount(d.id) } }
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("DATASET-" + d.id.toString().padStart(3, '0') + " · " + d.name, C.text, 13, true, modifier = Modifier.weight(1f))
                    if (d.active) Pill("AKTİF", "ok"); if (d.synthetic) Pill("SYN", "warn")
                }
                T("v${d.version} · ${S.th(count)} spin · ${fmt(d.createdAt)} · ${d.source}", C.dim, 11)
                T("hash ${d.hash.takeLast(12)}" + if (d.note.isNotEmpty()) " · ${d.note}" else "", C.dim2, 10, mono = true)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LrButton("Aktif yap", { confirm = d.id }, "ghost", !d.active)
                    LrButton("Karşılaştır", { Engine.scope.launch { cmp = compare(d.id) } }, "ghost", ds.size > 1)
                }
                Divider1()
            }
        }
        T("Silinmez; yalnızca pasife alınabilir. Örnek (sentetik) sürüm açıkça işaretlenir ve gerçek değerlendirmeye varsayılan olarak dahil edilmez. Her deney dataset sürümüne bağlıdır.", C.dim2, 10)
    }
    val active = ds.firstOrNull { it.active }
    if (active != null) {
        val vs by produceState<List<fan.lightningroulette.data.DatasetVersionE>>(emptyList(), active.id, ui.tick) { value = withContext(Dispatchers.Default) { Engine.dao.versions(active.id) } }
        LrCard(title = "AKTİF DATASET · SÜRÜM GEÇMİŞİ") {
            for (v in vs) KV("v${v.version} · ${S.th(v.spinCount)} spin", fmt(v.createdAt), "", v.note + " · " + v.hash.takeLast(10))
        }
    }
    val cid = confirm
    if (cid != null) AlertDialog(onDismissRequest = { confirm = null },
        confirmButton = { TextButton(onClick = { confirm = null; Engine.scope.launch { DataOps.activate(cid) } }) { Text("Aktif yap") } },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("Vazgeç") } },
        title = { Text("Dataset değişsin mi?") }, text = { Text("Canlı model bu dataset’in geçmişiyle yeniden kurulur; her dataset’in kilitli tahminleri ve öğrenme durumu ayrı saklanır.") })
    val c = cmp
    if (c != null) AlertDialog(onDismissRequest = { cmp = null }, confirmButton = { TextButton(onClick = { cmp = null }) { Text("Tamam") } }, title = { Text("Karşılaştırma") }, text = { Text(c) })
}

private fun compare(otherId: Long): String {
    val a = Engine.dataset ?: return "aktif dataset yok"
    val b = Engine.dao.dataset(otherId) ?: return "dataset yok"
    if (a.id == b.id) return "aynı dataset"
    val sa = Engine.dao.spins(a.id).map { it.value }; val sb = Engine.dao.spins(b.id).map { it.value }
    var common = 0; while (common < sa.size && common < sb.size && sa[common] == sb[common]) common++
    return "${a.name} (v${a.version}): ${sa.size} spin\n${b.name} (v${b.version}): ${sb.size} spin\nortak başlangıç dizisi: $common spin\nhash: ${if (a.hash == b.hash) "AYNI" else "FARKLI"}"
}

// ───────────────────────── Dışa aktar / yedek
@Composable
private fun ExportTab(ui: EngineUi) {
    val ctx = LocalContext.current
    var scope by remember { mutableStateOf(setOf("spins", "predictions", "experiments", "models")) }
    var fmtSel by rememberSaveable { mutableStateOf(Engine.settings.exportFormat) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var result by remember { mutableStateOf("") }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val b = pending
        if (uri != null && b != null) Engine.scope.launch { ctx.contentResolver.openOutputStream(uri)?.use { it.write(b) }; Engine.notify("Dışa aktarıldı (${b.size / 1024} KB)") }
        pending = null
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Engine.scope.launch { try { restoreBytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Throwable) { result = "${fan.lightningroulette.core.Codes.EXP}: ${e.message}" } }
    }
    fun doSave(make: () -> Pair<String, ByteArray>) { Engine.scope.launch { val (n, b) = make(); pending = b; withContext(Dispatchers.Main) { save.launch(n) } } }
    @Composable fun sw(id: String, label: String) { val on = id in scope; Pill(label + if (on) " ✓" else "", if (on) "blue" else "line", Modifier.clickable { scope = if (on) scope - id else scope + id }) }
    LrCard(title = "KAPSAM") { Row { sw("spins", "Dataset (spinler)"); sw("predictions", "Tahminler") }; Row { sw("experiments", "Deneyler"); sw("models", "Model metadata") } }
    LrCard(title = "SPİNLERİ DIŞA AKTAR") {
        Row { for (f in listOf("csv", "json", "txt")) Pill(f.uppercase(), if (fmtSel == f) "blue" else "line", Modifier.clickable { fmtSel = f; Engine.settings.exportFormat = f }) }
        VGap(4)
        LrButton("Spinleri dışa aktar (${fmtSel.uppercase()})", { doSave { DataOps.exportSpins(fmtSel) } }, "primary", ui.spinCount > 0, Modifier.fillMaxWidth())
    }
    LrCard(title = "TEK PAKET (.lrexport)") {
        T("Seçilenleri tek dosyada toplar (GZIP JSON).", C.dim, 11)
        VGap(4)
        LrButton("Paketi dışa aktar", { doSave { DataOps.exportPackage(scope) } }, "ghost", scope.isNotEmpty(), Modifier.fillMaxWidth())
        VGap(6)
        LrButton("Round-trip testi", { Engine.scope.launch { result = try { DataOps.verifyRoundTrip(DataOps.exportPackage(setOf("spins")).second) } catch (e: Throwable) { "${fan.lightningroulette.core.Codes.EXP}: ${e.message}" } } }, "ghost", true, Modifier.fillMaxWidth())
        if (result.isNotEmpty()) Banner(result, if (result.startsWith("ROUND-TRIP BAŞARILI")) "ok" else "bad")
    }
    LrCard(title = "YEDEK AL / GERİ YÜKLE") {
        T("Veritabanı + model metadata tek dosyaya. Geri yükleme önce önizleme gösterir; veri yeni dataset olarak (pasif) eklenir, mevcut veri silinmez.", C.dim, 11)
        VGap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LrButton("Yedek al", { doSave { DataOps.exportPackage(setOf("spins", "predictions", "experiments", "models")) } }, "primary", true, Modifier.weight(1f))
            LrButton("Geri yükle", { open.launch(arrayOf("*/*")) }, "ghost", true, Modifier.weight(1f))
        }
        val files = remember(ui.tick) { DataOps.backupDir().listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList() }
        if (files.isNotEmpty()) { T("Otomatik yedekler (cihaz içi):", C.dim2, 10, modifier = Modifier.padding(top = 6.dp)); for (f in files.take(5)) T("• ${f.name} · ${f.length() / 1024} KB", C.dim, 10, mono = true) }
    }
    val rb = restoreBytes
    if (rb != null) {
        val preview by produceState("önizleme hazırlanıyor…", rb) { value = withContext(Dispatchers.Default) { try { val raw = java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(rb)).use { String(it.readBytes(), Charsets.UTF_8) }; val m = fan.lightningroulette.core.Json.parse(raw); val mm = (m as? Map<*, *>) ?: emptyMap<String, Any?>(); "dataset: ${(mm["datasets"] as? List<*>)?.size ?: 0} · spin: ${(mm["spins"] as? List<*>)?.size ?: 0} · tahmin: ${(mm["predictions"] as? List<*>)?.size ?: 0} · deney: ${(mm["experiments"] as? List<*>)?.size ?: 0}" } catch (e: Throwable) { "paket okunamadı: ${e.message}" } } }
        AlertDialog(onDismissRequest = { restoreBytes = null },
            confirmButton = { TextButton(onClick = { val b = rb; restoreBytes = null; Engine.scope.launch { result = try { DataOps.restorePackage(b) } catch (e: Throwable) { "${fan.lightningroulette.core.Codes.EXP}: ${e.message}" } } }) { Text("Geri yükle") } },
            dismissButton = { TextButton(onClick = { restoreBytes = null }) { Text("Vazgeç") } },
            title = { Text("Geri yükleme önizlemesi") }, text = { Text(preview) })
    }
}
