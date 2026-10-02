package fan.lightningroulette.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fan.lightningroulette.core.Importer
import fan.lightningroulette.core.S
import fan.lightningroulette.core.SampleData
import fan.lightningroulette.engine.BootStep
import fan.lightningroulette.engine.DataOps
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.engine.EngineUi
import fan.lightningroulette.lab.LabManager
import fan.lightningroulette.overlay.OverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Root() {
    val ui by Engine.ui.collectAsState()
    Box(Modifier.fillMaxSize().background(C.bg)) {
        when (ui.phase) {
            "boot" -> SplashScreen(ui)
            "setup" -> SetupFlow(ui)
            "error" -> ErrorScreen(ui)
            else -> MainScaffold(ui)
        }
    }
}

@Composable
fun StepRow(s: BootStep) {
    val (icon, col) = when (s.status) { "ok" -> "✓" to C.ok; "run" -> "◔" to C.blue; "err" -> "✗" to C.bad; else -> "●" to C.dim2 }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        T(icon, col, 16, true, modifier = Modifier.padding(end = 10.dp))
        Column { T(s.name, if (s.status == "err") C.bad else C.text, 14, true); if (s.detail.isNotEmpty()) T(s.detail, if (s.status == "err") C.bad else C.dim, 11) }
    }
}

@Composable
fun SplashScreen(ui: EngineUi) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        T("⚡", C.gold, 54, true)
        T("Lightning Roulette AI", C.text, 22, true)
        T("Avrupa ruleti araştırma ve tahmin uygulaması", C.dim, 12)
        VGap(24)
        LrCard(title = "HAZIRLIK") {
            for (s in ui.steps) StepRow(s)
            if (ui.busy != null) T(ui.busy, C.blue, 12, true, mono = true, modifier = Modifier.padding(top = 6.dp))
        }
        T("Sonraki açılışlarda yalnızca yeni kayıtlar işlenir; “tam replay” yalnızca ilk kurulum, durum bozulması veya elle yapılır. Hata olursa ilgili satır kırmızı olur ve LR-E-* kodu yazılır; Python yoksa Kotlin-only çalışılır.", C.dim2, 10, align = TextAlign.Center)
    }
}

@Composable
fun ErrorScreen(ui: EngineUi) {
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        T("⚠ Başlatılamadı", C.bad, 20, true)
        VGap(8)
        for (s in ui.steps) StepRow(s)
        Banner(ui.error ?: "bilinmeyen hata", "bad")
        T("Verin silinmedi. Yeniden deneyebilirsin; sorun sürerse Ayarlar › İşlem logları’ndaki hata kodunu not al.", C.dim, 12)
        VGap(12)
        LrButton("Yeniden dene", { Engine.retryBoot() }, "primary", true, Modifier.fillMaxWidth())
    }
}

// ───────────────────────── ilk kurulum: hoş geldin → izinler → başlangıç verisi
@Composable
fun SetupFlow(ui: EngineUi) {
    var page by rememberSaveable { mutableStateOf(if (Engine.settings.disclaimerAccepted) 2 else 0) }
    var ack by rememberSaveable { mutableStateOf(false) }
    var choice by rememberSaveable { mutableStateOf("sample") }
    var wizard by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (wizard) { BackHandler { wizard = false }; ImportWizard(onDone = { wizard = false }, onCancel = { wizard = false }); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        when (page) {
            0 -> {
                T("⚡ Hoş geldin", C.text, 22, true)
                T("Lightning Roulette AI bir araştırma ve tahmin aracıdır.", C.dim, 13)
                VGap(10)
                LrCard(title = "NASIL ÇALIŞIR?") {
                    T("tahmin → 🔒 LOCK → gerçek sonuç → değerlendirme → öğrenme", C.gold, 13, true)
                    T("Tahmin, sonuç girilmeden önce kilitlenir ve sonradan değiştirilemez. Öğrenme yalnızca sonuç açıldıktan sonra yapılır.", C.dim, 12)
                }
                LrCard(title = "VERİ VE GİZLİLİK") { T("Her şey cihazda. İnternet izni, hesap ve reklam yok. Verini istediğin an dışa aktarabilir veya silebilirsin.", C.text, 12) }
                Banner(DISCLAIMER, "warn")
                Row(Modifier.fillMaxWidth().clickable { ack = !ack }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ack, onCheckedChange = { ack = it }); T("Okudum: sistem geleceği bilmez, hiçbir sonuç garanti değildir.", C.text, 12, modifier = Modifier.weight(1f))
                }
                VGap(8)
                LrButton("Devam", { Engine.settings.disclaimerAccepted = true; page = 1 }, "primary", ack, Modifier.fillMaxWidth())
            }
            1 -> {
                T("İzinler", C.text, 22, true)
                T("Hepsi isteğe bağlıdır; sonradan Ayarlar’dan verilebilir.", C.dim, 12)
                LrCard(title = "DOSYA ERİŞİMİ") { T("İçe/dışa aktarma Android’in dosya seçicisini kullanır; geniş depolama izni istenmez.", C.text, 12) }
                LrCard(title = "AĞ YOK") { T("Uygulama internete bağlanmaz; veri dışarı gönderilmez.", C.text, 12) }
                LrCard(title = "EKRAN ÜSTÜ (OVERLAY)") {
                    T("Başka uygulamanın üstünde sonuç girişi için “Diğer uygulamaların üzerinde göster” izni gerekir.", C.text, 12)
                    VGap(6)
                    LrButton(if (OverlayService.canDraw(ctx)) "✓ İzin verildi" else "Ayarı aç", { ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) }, "ghost", !OverlayService.canDraw(ctx))
                }
                LrCard(title = "BİLDİRİM") {
                    T("LAB ve overlay ön plan servisleri bildirim gösterir. İzin verilmezse ilerleme yalnızca uygulama içinde görünür.", C.text, 12)
                    VGap(6)
                    LrButton("Bildirim izni iste", { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }, "ghost", Build.VERSION.SDK_INT >= 33)
                }
                LrCard(title = "PİL KISITI (isteğe bağlı)") {
                    T("Uzun LAB deneylerinin arka planda kesilmemesi için “kısıtlama yok” önerilir. Reddedilirse deney kalan yerden devam eder (resume).", C.text, 12)
                    VGap(6)
                    LrButton("Pil ayarlarını aç", { ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, "ghost")
                }
                VGap(6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LrButton("Atla", { page = 2 }, "ghost", true, Modifier.weight(1f)); LrButton("Devam", { page = 2 }, "primary", true, Modifier.weight(1f))
                }
            }
            else -> {
                T("Başlangıç verisi", C.text, 22, true)
                T("Tahmin için en az 50 spin gerekir. Başlangıç noktasını seç:", C.dim, 12)
                VGap(8)
                @Composable fun opt(id: String, title: String, sub: String, badge: String = "") {
                    val on = choice == id
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.blueDeep.copy(alpha = 0.25f) else C.card).border(BorderStroke(1.5.dp, if (on) C.blue else C.line), RoundedCornerShape(12.dp)).clickable { choice = id }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(if (on) "◉" else "○", if (on) C.blue else C.dim, 18, true, modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) { Row { T(title, C.text, 14, true); if (badge.isNotEmpty()) { HGap(6); Pill(badge, "warn") } }; T(sub, C.dim, 11) }
                    }
                }
                opt("sample", "Örnek veri seti (2 000 spin)", "Sabit tohumlu, adil rulet simülasyonu. Gerçek sonuç değildir; sistemin çalışmasını denemek içindir. Sınıf C (tabandan ayrışma yok) beklenir.", "SYN")
                opt("import", "Dosyadan içe aktar", "CSV / TXT / JSON. Önce doğrulama raporu gösterilir; rapor onaylanmadan hiçbir şey kaydedilmez.")
                opt("empty", "Boş başla", "Canlı sonuçları kendin girersin; ilk 50 spin öğrenme eşiğidir.")
                if (choice == "sample") {
                    val prev = remember { SampleData.generate().take(10) }
                    LrCard(title = "ÖNİZLEME · ilk 10 / 2 000") { Row { for (s in prev) NumChip(s.value, 26.dp, 11) }; T("timestamp_type = SYNTHETIC_IMPORT (yapay zaman; gerçek zaman gibi yorumlanmaz)", C.warn, 10) }
                }
                VGap(8)
                LrButton("Başla", {
                    when (choice) {
                        "import" -> wizard = true
                        "sample" -> Engine.scope.launch { Engine.createDataset("Örnek veri (2 000 sentetik)", "SAMPLE", true, SampleData.generate(), "adil rulet simülasyonu · sabit tohum"); Engine.finishSetup(); Engine.openSession() }
                        else -> Engine.scope.launch { Engine.createDataset("Canlı veri", "LIVE", false, emptyList(), "boş başlangıç"); Engine.finishSetup(); Engine.openSession() }
                    }
                }, "primary", true, Modifier.fillMaxWidth())
            }
        }
    }
}

// ───────────────────────── ana iskelet: alt gezinme
private val NAV = listOf("🏠" to "Ana", "🏛" to "Meclisler", "◎" to "Wheel", "🧪" to "LAB", "🗂" to "Veri", "⚙" to "Ayarlar")

@Composable
fun MainScaffold(ui: EngineUi) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var detail by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    BackHandler(enabled = detail) { detail = false }
    LaunchedEffect(Unit) {
        Engine.scope.launch { try { if (Engine.settings.autoBackup) DataOps.autoBackupIfDue(Engine.settings.backupKeep) } catch (_: Exception) { } }
        LabManager.resumeIfNeeded(ctx)
        if (Engine.settings.ovEnabled && OverlayService.canDraw(ctx) && !OverlayService.running.value) { try { OverlayService.start(ctx) } catch (_: Exception) { } }
    }
    LaunchedEffect(ui.toastId) { if (ui.toast != null) { delay(3200); Engine.clearToast() } }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (detail) PredictionDetail(ui, { detail = false }, { detail = false; tab = 3 })
            else when (tab) {
                0 -> HomeScreen(ui, { detail = true }, { tab = 3 })
                1 -> CouncilScreen(ui)
                2 -> WheelScreen(ui)
                3 -> LabScreen(ui)
                4 -> DataScreen(ui)
                else -> SettingsScreen(ui)
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(10.dp)) {
                if (ui.busy != null) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.blueDeep).padding(10.dp)) { T("⏳ " + ui.busy, Color.White, 12, true, true) }
                if (ui.toast != null) Box(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(C.card2).border(BorderStroke(1.dp, C.line), RoundedCornerShape(10.dp)).clickable { Engine.clearToast() }.padding(10.dp)) { T(ui.toast, C.text, 12) }
            }
        }
        Row(Modifier.fillMaxWidth().background(C.card).padding(vertical = 4.dp)) {
            for ((i, n) in NAV.withIndex()) {
                val on = i == tab && !detail
                Column(Modifier.weight(1f).testTag("nav_$i").clickable { tab = i; detail = false }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(n.first, if (on) C.gold else C.dim, 18)
                    T(n.second, if (on) C.text else C.dim2, 10, on)
                }
            }
        }
    }
    val err = ui.error
    if (err != null) AlertDialog(onDismissRequest = { Engine.clearError() }, confirmButton = { TextButton(onClick = { Engine.clearError() }) { Text("Tamam") } },
        title = { Text("Hata") }, text = { Text(err + "\n\nCanlı durum korundu. Kayıt/işlem iptal edildi.") })
}

