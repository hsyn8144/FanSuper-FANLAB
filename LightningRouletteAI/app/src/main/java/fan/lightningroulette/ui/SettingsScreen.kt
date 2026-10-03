package fan.lightningroulette.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fan.lightningroulette.core.Codes
import fan.lightningroulette.core.S
import fan.lightningroulette.data.LogE
import fan.lightningroulette.data.LrDb
import fan.lightningroulette.engine.DataOps
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.engine.EngineUi
import fan.lightningroulette.engine.SelfTest
import fan.lightningroulette.engine.TestResult
import fan.lightningroulette.lab.LabManager
import fan.lightningroulette.overlay.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(ui: EngineUi) {
    var sub by rememberSaveable { mutableStateOf(0) }
    val s = Engine.settings
    val ver by s.version.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        T("⚙ Ayarlar", C.text, 18, true)
        SegTabs(listOf("Tahmin", "Overlay", "Python/LAB", "Veri", "Loglar", "Tanılama", "Hakkında"), sub, { sub = it })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (sub) { 0 -> PredSettings(ver); 1 -> OverlaySettings(ver); 2 -> PyLabSettings(ver); 3 -> DataSettings(ver); 4 -> LogsTab(); 5 -> DiagTab(); else -> AboutTab() }
        }
    }
}

@Composable
private fun Chips(setting: String, options: List<Pair<String, String>>, sel: String, set: (String) -> Unit) {
    // Önceki Row ekrandan geniş olunca sağdaki seçenekler kırpılıp dokunulamaz kalıyordu.
    // Yatay kaydırma ve tüm chip alanında en az 44dp dokunma hedefi sağlar.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for ((label, value) in options) {
            Pill(
                label,
                if (value == sel) "blue" else "line",
                Modifier.heightIn(min = 44.dp)
                    .testTag("choice_${setting}_$value")
                    .clickable(role = Role.RadioButton) { set(value) }
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, sub: String = "", on: Boolean, set: (Boolean) -> Unit) {
    // Başlığa/açıklamaya dokunmak da switch'i değiştirir; yalnızca küçük anahtara bağlı değil.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("setting_$label")
            .toggleable(value = on, role = Role.Switch, onValueChange = set)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) { T(label, C.text, 13); if (sub.isNotEmpty()) T(sub, C.dim2, 10) }
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun SliderRow(label: String, value: Int, min: Int, max: Int, unit: String = "", onDone: (Int) -> Unit) {
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row { T(label, C.text, 13, modifier = Modifier.weight(1f)); T("${v.toInt()}$unit", C.gold, 13, true, true) }
        Slider(
            modifier = Modifier.testTag("slider_$label"), value = v,
            onValueChange = { v = it }, valueRange = min.toFloat()..max.toFloat(),
            onValueChangeFinished = { onDone(v.toInt()) }
        )
    }
}

@Composable
private fun PredSettings(ver: Int) {
    val s = Engine.settings
    LrCard(title = "ADAY VE K") {
        T("Aday sayısı (3–5)", C.dim, 11, true); Chips("nCand", listOf("3" to "3", "4" to "4", "5" to "5"), s.nCand.toString()) { s.nCand = it.toInt(); Engine.applySettings() }
        T("Varsayılan k", C.dim, 11, true); Chips("kMode", listOf("otomatik" to "0", "k1" to "1", "k2" to "2", "k3" to "3"), s.kMode.toString()) { s.kMode = it.toInt(); Engine.applySettings() }
        T("Yön", C.dim, 11, true); Chips("dir", listOf("↔ iki yön" to "bi", "L saat yönünün tersi" to "L", "R saat yönü" to "R"), s.dir) { s.dir = it; Engine.applySettings() }
        T("Değişiklik yalnızca SONRAKİ tahmini etkiler; mevcut kilitli tahmin değişmez.", C.dim2, 10)
    }
    LrCard(title = "ÇEŞİTLİLİK") { SwitchRow("Çeşitlilik kuralı", "Mümkünse 3–5 adayda en az 2 farklı sector/region kaynağı", s.diversity) { s.diversity = it; Engine.applySettings() } }
    LrCard(title = "YÜZDE SEMANTİĞİ") {
        Chips("pctMode", listOf("Kalibre olasılık" to "cal", "Model olasılığı (Kotlin)" to "model"), s.pctMode) { s.pctMode = it }
        T("Kalibre olasılık (varsayılan), model olasılığı ve tarihsel oran ayrı tutulur. Sadece frequency ise “confidence” diye sunulmaz.", C.dim2, 10)
    }
    LrCard(title = "MECLİS AĞIRLIKLARI") {
        SwitchRow("Python meclisi", "Kapalıysa yalnızca Kotlin (Kotlin-only)", s.pythonEnabled) { s.pythonEnabled = it; Engine.applySettings() }
        SwitchRow("Kotlin/Python ağırlığı otomatik", "Validation’da seçilir; kapatılırsa elle", s.weightAuto) { s.weightAuto = it; Engine.applySettings() }
        if (!s.weightAuto) SliderRow("Kotlin ağırlığı", s.kotlinPct, 20, 80, "%") { s.kotlinPct = it; Engine.applySettings() }
        SliderRow("Pencere (son N spin)", s.window, 50, 1000) { s.window = it; Engine.applySettings() }
        T("Öğrenme eşiği: en az 50 spin (sabit).", C.dim2, 10)
    }
    LrCard(title = "KİLİTLİ KURALLAR") {
        T("🔒 Prediction lock · versiyonlu artımlı güncelleme · Champion’a manuel onay · test (OOS) setiyle model seçimi yok. Bunlar kapatılamaz.", C.text, 12)
    }
}

@Composable
private fun OverlaySettings(ver: Int) {
    val s = Engine.settings; val ctx = LocalContext.current
    val running by OverlayService.running.collectAsState()
    val can = OverlayService.canDraw(ctx)
    LrCard(title = "OVERLAY SERVİSİ") {
        SwitchRow("Overlay açık", "Açıkken ön plan servisi bildirimi görünür", s.ovEnabled && running) { on ->
            if (on) { if (OverlayService.canDraw(ctx)) { s.ovEnabled = true; OverlayService.start(ctx) } else ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) }
            else { s.ovEnabled = false; OverlayService.stop(ctx) }
        }
        KV("İzin: diğer uygulamaların üzerinde göster", if (can) "VERİLDİ" else "VERİLMEDİ", if (can) "ok" else "warn")
        if (!can) LrButton("Ayarı aç", { ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) }, "ghost", true, Modifier.fillMaxWidth())
    }
    LrCard(title = "GÖRÜNÜM") {
        Chips("ovMode", listOf("Dikey" to "vertical", "Yatay" to "horizontal", "Kompakt" to "compact", "Metin" to "text", "Simge" to "icon"), s.ovMode) { s.ovMode = it }
        SliderRow("Boyut (küçültmek için 60%’a kadar)", s.ovScale, 60, 140, "%") { s.ovScale = it }
        SliderRow("Saydamlık", s.ovAlpha, 60, 100, "%") { s.ovAlpha = it }
        T("NEXT sayısı", C.dim, 11, true); Chips("ovNext", listOf("3" to "3", "4" to "4", "5" to "5"), s.ovNext.toString()) { s.ovNext = it.toInt() }
        T("SON 8 ve TABLE satır sayısı sabittir (8 ve 5).", C.dim2, 10)
    }
    LrCard(title = "KLAVYE VE UYARILAR") {
        SwitchRow("Klavyeyi göster", "Kompakt/metin modda ⌨ ile açılır", s.ovKeyboard) { s.ovKeyboard = it }
        SwitchRow("Uyarı simgesi (⚠)", "Düşük sample, OOS zayıf, Calibration zayıf …", s.ovWarn) { s.ovWarn = it }
        SwitchRow("Titreşim geri bildirimi", "", s.ovVibrate) { s.ovVibrate = it }
    }
    LrCard(title = "KONUM") {
        SwitchRow("Kenara yapış", "Bırakınca en yakın ekran kenarına", s.ovSnap) { s.ovSnap = it }
        LrButton("Konumu sıfırla", { s.ovX = -1; s.ovY = -1; if (running) { OverlayService.stop(ctx); OverlayService.start(ctx) } }, "ghost", true, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PyLabSettings(ver: Int) {
    val s = Engine.settings; val ctx = LocalContext.current
    LrCard(title = "HATA DAVRANIŞI") { T("Python hata verirse uygulama çökmez; status = ERROR yazılır ve Kotlin-only fallback açıkça gösterilir. Fallback Python sonucu gibi sunulmaz.", C.text, 12) }
    LrCard(title = "LAB ARKA PLAN") {
        SwitchRow("LAB’a Python meclisini dahil et", "Daha uzun sürer (8 üye parça parça hesaplanır); kapalıysa Kotlin-only", s.labPython) { s.labPython = it; LabManager.invalidate() }
        SwitchRow("Pil tasarrufunda duraklat", "Pil tasarrufu kapanınca kaldığı yerden sürer", s.labPauseOnSaver) { s.labPauseOnSaver = it }
        SwitchRow("Ön plan bildirimi", "LAB çalışıyor %63 · İptal", s.labForeground) { s.labForeground = it }
        SliderRow("Checkpoint aralığı (adım)", s.labCheckpoint, 25, 1000) { s.labCheckpoint = it }
        T("Paralel iş sayısı: 1 (canlı arayüzü bloke etmemek için LAB ayrı, düşük öncelikli iş parçacığında çalışır).", C.dim2, 10)
    }
    LrCard(title = "ÖNBELLEK") {
        T("Cache dataset içerik özetine ve parametre hash’ine bağlıdır; veri veya yapılandırma değişince eskiyen önbellek otomatik geçersiz olur.", C.text, 12)
        VGap(6)
        LrButton("LAB önbelleğini temizle", { Engine.scope.launch { Engine.dao.deleteStatePrefix("lab."); LabManager.invalidate(); Engine.notify("LAB önbelleği temizlendi") } }, "ghost", true, Modifier.fillMaxWidth())
    }
    LrCard(title = "SAYFALAMA") { SliderRow("Liste sayfa boyutu", s.pageSize, 10, 1000) { s.pageSize = it } }
    if (ctx != null) T("", C.dim)
}

@Composable
private fun DataSettings(ver: Int) {
    val s = Engine.settings; val ctx = LocalContext.current
    var confirm by remember { mutableStateOf("") }
    var ack by remember { mutableStateOf(false) }
    var word by remember { mutableStateOf("") }
    val sizes by produceState("hesaplanıyor…") { value = withContext(Dispatchers.Default) {
        val db = ctx.getDatabasePath(LrDb.FILE).length() / 1024; val py = File(ctx.filesDir, "py_state.b64").let { if (it.exists()) it.length() / 1024 else 0 }
        val bk = (DataOps.backupDir().listFiles() ?: emptyArray()).sumOf { it.length() } / 1024
        "veritabanı $db KB · Python durumu $py KB · yedekler $bk KB"
    } }
    LrCard(title = "DIŞA AKTARMA VARSAYILANI") {
        Chips("exportFormat", listOf("CSV" to "csv", "JSON" to "json", "TXT" to "txt"), s.exportFormat) { s.exportFormat = it }
        SwitchRow("Günlük otomatik yedek", "Cihaz içi; son ${s.backupKeep} yedek saklanır", s.autoBackup) { s.autoBackup = it }
    }
    LrCard(title = "DEPOLAMA") {
        T(sizes, C.text, 12, mono = true)
        T("Resimler veritabanına konmaz; ham spinler küçüktür. Büyüme deneyler, model metadata, önbellek ve replay sonuçlarından gelir; gereksiz ara veri saklanmaz.", C.dim2, 10)
    }
    LrCard(title = "İZİN ÖZETİ") {
        KV("Overlay", if (OverlayService.canDraw(ctx)) "verildi" else "verilmedi", if (OverlayService.canDraw(ctx)) "ok" else "warn")
        T("Bildirim ve pil izinleri isteğe bağlıdır; verilmezse işlev bozulmaz (ilerleme yalnızca uygulama içinde görünür).", C.dim2, 10)
        VGap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LrButton("Bildirim izni", { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }, "ghost", Build.VERSION.SDK_INT >= 33, Modifier.weight(1f))
            LrButton("Pil ayarı", { ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, "ghost", true, Modifier.weight(1f))
        }
    }
    LrCard(title = "TEHLİKELİ BÖLGE") {
        T("Başarısız hipotez hafızası tek tek silinemez. Sıfırlama geri alınamaz.", C.warn, 11)
        VGap(6)
        LrButton("Örnek veriyi sil", { confirm = "sample" }, "danger", true, Modifier.fillMaxWidth())
        VGap(6)
        LrButton("Tüm veriyi sıfırla", { confirm = "reset" }, "danger", true, Modifier.fillMaxWidth())
    }
    if (confirm == "sample") AlertDialog(onDismissRequest = { confirm = "" },
        confirmButton = { TextButton(onClick = { confirm = ""; Engine.scope.launch { Engine.notify(DataOps.deleteSample()) } }) { Text("Sil") } },
        dismissButton = { TextButton(onClick = { confirm = "" }) { Text("Vazgeç") } },
        title = { Text("Örnek veri silinsin mi?") }, text = { Text("Sentetik örnek dataset ve ona bağlı tahmin/değerlendirme kayıtları silinir. Gerçek veriler etkilenmez.") })
    if (confirm == "reset") AlertDialog(onDismissRequest = { confirm = ""; ack = false; word = "" },
        confirmButton = { TextButton(enabled = ack && word == "SIFIRLA", onClick = { confirm = ""; ack = false; word = ""; Engine.scope.launch { DataOps.resetAll() } }) { Text("Sıfırla") } },
        dismissButton = { TextButton(onClick = { confirm = ""; ack = false; word = "" }) { Text("Vazgeç") } },
        title = { Text("Tüm veri sıfırlansın mı?") },
        text = { Column { Text("Datasetler, spinler, tahminler, değerlendirmeler, deneyler, başarısız hipotez hafızası ve model durumu SİLİNİR. Geri alınamaz. Önce Veri › Dışa aktar ile yedek almanı öneririz.\n")
            Row(Modifier.clickable { ack = !ack }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = ack, onCheckedChange = { ack = it }); Text("Anladım") }
            OutlinedTextField(value = word, onValueChange = { word = it.uppercase().take(8) }, label = { Text("Onay için SIFIRLA yaz") }, singleLine = true) } })
}

@Composable
private fun LogsTab() {
    val ctx = LocalContext.current
    val logs by Engine.dao.logsFlow(200).collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.US) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) Engine.scope.launch { val txt = Engine.dao.logs(2000).reversed().joinToString("\n") { "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it.ts))} [${it.level}] ${it.code ?: "-"} ${it.event} ${it.detail}" }; ctx.contentResolver.openOutputStream(uri)?.use { o -> o.write(txt.toByteArray(Charsets.UTF_8)) }; Engine.notify("Log dışa aktarıldı") }
    }
    LrCard(title = "İŞLEM LOGLARI · son ${logs.size}") {
        if (logs.isEmpty()) T("Kayıt yok.", C.dim, 12)
        for (l in logs.take(80)) LogRow(l, fmt)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LrButton("Dışa aktar", { save.launch("lightning_log.txt") }, "ghost", true, Modifier.weight(1f))
            LrButton("Temizle (yalnız bilgi)", { Engine.scope.launch { Engine.dao.clearInfoLogs() } }, "ghost", true, Modifier.weight(1f))
        }
        T("“Temizle” yalnızca bilgi seviyesini siler; hata kayıtları kalır.", C.dim2, 10)
    }
    LrCard(title = "HATA KODU SÖZLÜĞÜ · LR-E-*") {
        val dict = listOf(
            Codes.LEAK to "Sızıntı: tahmin zamanı > gerçek zaman ya da gelecek veriye erişim denemesi", Codes.LOCK2 to "Kilit: aynı spin için ikinci tahmin üretilemez / kilitli tahmin değiştirilemez",
            Codes.SEQ to "Sıra: kayıt sırası bozuk (zaman geriye gidiyor)", Codes.STATE1 to "Durum: model durumu bozuk/uyumsuz (yeniden kurulum)", Codes.PY to "Python: çıktı yok/geçersiz → Kotlin-only fallback",
            Codes.DB to "Veritabanı: açılış/yazma hatası; işlem geri alındı", Codes.REPLAY to "Replay: walk-forward işlemi başarısız", Codes.REC to "Kayıt: geçersiz değer (0–36 dışı)",
            Codes.IMP to "İçe aktarma: biçim/okuma hatası", Codes.EXP to "Deney/dışa aktarma: parametre veya paket hatası", Codes.REPRO to "Tekrarlanabilirlik: aynı girdi farklı sonuç verdi"
        )
        for ((c, d) in dict) Column(Modifier.padding(vertical = 3.dp)) { T(c, C.gold, 11, true, true); T(d, C.dim, 11) }
    }
}

@Composable
private fun LogRow(l: LogE, fmt: SimpleDateFormat) {
    val col = when (l.level) { "ERROR" -> C.bad; "WARN" -> C.warn; else -> C.dim }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row { T(fmt.format(Date(l.ts)), C.dim2, 10, mono = true); HGap(6); T(l.level, col, 10, true); HGap(6); T(l.event, C.text, 11, true) }
        if (l.code != null) T(l.code, C.bad, 10, true, true)
        if (l.detail.isNotEmpty()) T(l.detail.take(160), C.dim, 10)
    }
}

@Composable
private fun DiagTab() {
    val results = remember { mutableStateListOf<TestResult>() }
    var running by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf(0L) }
    val ctx = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) Engine.scope.launch { val txt = results.joinToString("\n") { "[${it.status}] ${it.group} · ${it.name}: ${it.detail}${if (it.code.isNotEmpty()) " (${it.code})" else ""}" }; ctx.contentResolver.openOutputStream(uri)?.use { o -> o.write(txt.toByteArray(Charsets.UTF_8)) }; Engine.notify("Rapor dışa aktarıldı") }
    }
    val pass = results.count { it.status == "pass" }; val fail = results.count { it.status == "fail" }; val skip = results.count { it.status == "skip" }
    LrCard(title = "ÖZET") {
        KV("Geçen / toplam", "$pass / ${results.size}", if (fail > 0) "bad" else if (results.isNotEmpty()) "ok" else "")
        KV("Atlanan", "$skip"); KV("Son çalıştırma", if (last == 0L) "—" else SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date(last)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LrButton(if (running) "◔ çalışıyor…" else "Tümünü çalıştır", {
                results.clear(); running = true
                Engine.scope.launch(Dispatchers.Default) { try { SelfTest.run { r -> results.add(r) } } finally { running = false; last = System.currentTimeMillis() } }
            }, "primary", !running, Modifier.weight(1f))
            LrButton("Raporu dışa aktar", { save.launch("lightning_tanilama.txt") }, "ghost", results.isNotEmpty(), Modifier.weight(1f))
        }
    }
    for (g in SelfTest.GROUPS) {
        val rs = results.filter { it.group == g }
        if (rs.isEmpty()) continue
        LrCard(title = g.uppercase() + " · ${rs.count { it.status == "pass" }}/${rs.size}") {
            for (r in rs) Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row { T(when (r.status) { "pass" -> "✓ "; "fail" -> "✗ "; else -> "● " }, when (r.status) { "pass" -> C.ok; "fail" -> C.bad; else -> C.dim }, 13, true); T(r.name, C.text, 12, true) }
                T(r.detail + if (r.code.isNotEmpty() && r.status == "fail") " · ${r.code}" else "", if (r.status == "fail") C.bad else C.dim2, 10)
            }
        }
    }
    if (results.isEmpty()) Banner("Test grupları: veri bütünlüğü · canlı giriş · sızıntı ve replay · kalıcılık · istatistik · aktarım · model kuralları. ✓ geçti · ✗ kaldı (kod ve açıklamayla) · ● atlandı · ◔ çalışıyor.", "info")
}

@Composable
private fun AboutTab() {
    LrCard(title = "DÜRÜSTLÜK BİLDİRİMİ") {
        T("Sistem geleceği bildiğini iddia etmez.", C.gold, 14, true)
        T("Her tahmin geçmişten üretilir, kilitlenir, gerçek sonuç sonradan açılır, değerlendirilir ve ancak ondan sonra öğrenmeye girer: tahmin → LOCK → sonuç → değerlendirme → öğrenme.", C.text, 12)
    }
    LrCard(title = "SABİT UYARILAR") {
        val w = listOf(
            "Düşük sample." to "OOS n < 300 ya da bir rejimde n < 100 olduğunda", "OOS zayıf." to "OOS’ta tabandan fark ≤ 0 olduğunda",
            "Calibration zayıf." to "ECE > 0,05 olduğunda", "Parameter stability yok." to "Hassasiyet taraması yapılmadığında/başarısız olduğunda",
            "Baseline’dan anlamlı ayrışma yok." to "%95 CI taban çizgisini (0 pp) kapsadığında", "Leakage şüphesi." to "Bir sızıntı kapısı kaldığında (LR-E-LEAK-001)",
            "Yüksek hit oranı tek başına avantaj kanıtı değildir." to "Her zaman"
        )
        for ((a, b) in w) Column(Modifier.padding(vertical = 3.dp)) { T("⚠ $a", C.warn, 12, true); T(b, C.dim2, 10) }
    }
    LrCard(title = "BİLİMSEL ÇERÇEVE") {
        T("Çok veri daha iyi tahmin ve daha dar güven aralığı verebilir; ancak rastgele ve bağımsız bir veriden kalıcı bir avantaj çıkması zorunlu değildir. Uygulama bunu gizlemez, ama hipotezleri ciddi biçimde araştırır: walk-forward (shuffle yok), OOS koruması, çoklu test düzeltmesi, başarısız hipotez hafızası, robustness ve leakage kapıları.", C.text, 12)
    }
    LrCard(title = "GİZLİLİK") { T("Çevrimdışı çalışır; hesap, reklam ve ağ yok (INTERNET izni istenmez). Tüm veri cihazda tutulur; istediğin an dışa aktarabilir veya silebilirsin.", C.text, 12) }
    LrCard(title = "SÜRÜM") {
        KV("Uygulama", "Lightning Roulette AI ${fan.lightningroulette.BuildConfig.VERSION_NAME}", mono = false); KV("Paket", "fan.lightningroulette", mono = false); KV("Veritabanı şeması", "v${LrDb.VERSION}")
        KV("Python meclisi", "numpy · Chaquopy 3.11", mono = false); KV("Hata kodları", "LR-E-*", mono = false)
    }
}
