package fan.lightningroulette

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.core.LabTabs as CoreLabTabs
import fan.lightningroulette.overlay.OverlayService
import fan.lightningroulette.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cihaz üzerinde uçtan uca yolculuk (emülatör): sıfır kurulum → ilk kurulum akışı → örnek veri → ENTER → geri al →
 * tüm sekmeler/alt sekmeler → LAB taban koşusu + 16 sekme → tanılama → overlay servisi. Çökme veya takılma = test hatası.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class JourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun has(t: String, sub: Boolean = true) = rule.onAllNodesWithText(t, substring = sub).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(t: String, ms: Long = 60_000, sub: Boolean = true) {
        try { rule.waitUntil(ms) { has(t, sub) } } catch (e: Throwable) { throw AssertionError("beklenen metin görünmedi: “$t” (${ms / 1000} sn). ${diag()} Ekran: ${dump()}") }
    }
    private fun dump(): String = try { rule.onRoot(useUnmergedTree = false).printToString().take(700) } catch (e: Throwable) { "?" }
    private fun diag(): String {
        val u = Engine.ui.value
        val logs = try { Engine.dao.logs(8).joinToString(" | ") { it.event + (it.code?.let { c -> "($c)" } ?: "") + ":" + it.detail.take(140) } } catch (e: Throwable) { "log okunamadı: ${e.message}" }
        return "durum[phase=${u.phase} spin=${u.spinCount} error=${u.error} toast=${u.toast} py=${u.pyStatus} busy=${u.busy}] loglar[$logs]"
    }
    private fun first(t: String, sub: Boolean = false): SemanticsNodeInteraction = rule.onAllNodesWithText(t, substring = sub)[0]
    private fun click(t: String, sub: Boolean = false) { val n = first(t, sub); try { n.performScrollTo() } catch (_: Throwable) { }; n.performClick(); rule.waitForIdle() }
    private fun tag(t: String) { val n = rule.onAllNodesWithTag(t)[0]; try { n.performScrollTo() } catch (_: Throwable) { }; n.performClick(); rule.waitForIdle() }
    private fun nav(i: Int) = tag("nav_$i")
    private fun seg(label: String) = tag("seg_$label")

    @Test fun fullJourney() {
        // ── 1) açılış + ilk kurulum
        waitText("Hoş geldin", 120_000)
        click("Okudum:", true); click("Devam")
        waitText("İzinler"); click("Atla")
        waitText("Başlangıç verisi"); waitText("Örnek veri seti")
        click("Başla")

        // ── 2) ana ekran: örnek veri + Python + kilitli tahmin
        waitText("KİLİT", 360_000)
        assertTrue("NEXT kartı görünmeli", has("NEXT"))
        assertTrue("TABLE kartı görünmeli", has("TABLE"))
        assertTrue("dürüstlük uyarısı görünmeli", has("geleceği bilmez"))
        val py = Engine.ui.value.pyStatus
        assertTrue("Python durumu OK/ERROR/OFF olmalı: $py", py in listOf("OK", "ERROR", "OFF"))

        // ── 3) canlı giriş: 1,7 → ENTER → değerlendirme → geri al
        tag("key_1"); tag("key_7"); tag("key_ENTER")
        waitText("SONUÇ DEĞERLENDİRMESİ", 60_000)
        waitText("KİLİT", 60_000)
        val before = Engine.ui.value.spinCount
        click("↩ Son spini geri al")
        try { rule.waitUntil(90_000) { Engine.ui.value.spinCount == before - 1 } } catch (e: Throwable) { throw AssertionError("geri al çalışmadı: spin $before → ${Engine.ui.value.spinCount}. ${diag()}") }
        assertTrue("geri alma sonrası kilitli tahmin görünmeli", Engine.ui.value.pred != null)
        // geçersiz giriş: 3,7 → 37 → kutu kırmızı ve ENTER pasif
        tag("key_3"); tag("key_7")
        waitText("0–36 DIŞI")
        tag("key_DEL"); tag("key_DEL")

        // ── 4) Meclisler
        nav(1); waitText("KOTLIN MECLİSİ")
        for (s in listOf("Python", "Table", "Hakem", "Champion")) { seg(s); rule.waitForIdle() }
        waitText("CHAMPION / CHALLENGER")
        // ── 5) Wheel / Masa / Sektörler
        nav(2); waitText("Wheel & Masa")
        for (s in listOf("Masa", "Sektörler", "Wheel")) { seg(s); rule.waitForIdle() }
        // ── 6) LAB: taban koşusu + 16 sekme
        nav(3); waitText("LAB V2")
        waitText("Hesapla", 30_000, false)
        click("Hesapla")
        waitText("Train", 420_000)
        for ((_, label) in CoreLabTabs.TABS) { seg(label); rule.waitUntil(60_000) { !has("hesaplanıyor…") }; rule.waitForIdle() }
        seg("Robustness")
        for ((_, label) in CoreLabTabs.ROB_TABS) { seg(label); rule.waitUntil(60_000) { !has("hesaplanıyor…") }; rule.waitForIdle() }
        // ── 7) Veri
        nav(4); waitText("Spinler")
        for (s in listOf("İçe aktar", "Sürümler", "Dışa aktar", "Spinler")) { seg(s); rule.waitForIdle() }
        // ── 8) Ayarlar + tanılama
        nav(5); waitText("Ayarlar")
        for (s in listOf("Overlay", "Python/LAB", "Veri", "Loglar", "Hakkında")) { seg(s); rule.waitForIdle() }
        seg("Tanılama"); click("Tümünü çalıştır")
        waitText("Geçen / toplam", 30_000)
        rule.waitUntil(30_000) { has("◔ çalışıyor…", true) || has("Atlanan", true) && has("✓ ", true) }
        rule.waitUntil(300_000) { !has("◔ çalışıyor…", true) }
        val failed = fan.lightningroulette.engine.SelfTest.last.filter { it.status == "fail" }
        assertTrue("tanılamada kalan test: " + failed.joinToString(" || ") { "${it.group} / ${it.name}: ${it.detail} (${it.code})" } + " — toplam ${fan.lightningroulette.engine.SelfTest.last.size}", failed.isEmpty())
        // ── 9) overlay servisi (izin adb ile verilir)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        if (OverlayService.canDraw(ctx)) {
            OverlayService.start(ctx)
            rule.waitUntil(30_000) { OverlayService.running.value }
            Thread.sleep(2500)
            OverlayService.stop(ctx)
            rule.waitUntil(30_000) { !OverlayService.running.value }
        }
        nav(0); waitText("KİLİT")
    }
}
