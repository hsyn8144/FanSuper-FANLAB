package fan.lightningroulette.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fan.lightningroulette.core.S
import fan.lightningroulette.core.TableCall
import fan.lightningroulette.core.TableCats
import fan.lightningroulette.core.Wheel

/** Tasarım sistemi (Ekran 02): koyu lacivert, altın vurgu, rulet renkleri. */
object C {
    val bg = Color(0xFF0B1220); val card = Color(0xFF121B2D); val card2 = Color(0xFF18243B); val line = Color(0xFF243350)
    val text = Color(0xFFE8EEF8); val dim = Color(0xFF8A97AA); val dim2 = Color(0xFF6F7F96)
    val blue = Color(0xFF42A5F5); val blueDeep = Color(0xFF1976D2); val gold = Color(0xFFFFC531)
    val ok = Color(0xFF4CAF50); val warn = Color(0xFFFFB300); val bad = Color(0xFFEF5350); val purple = Color(0xFFB388FF)
    val red = Color(0xFFD32F2F); val black = Color(0xFF212121); val green = Color(0xFF2E7D32)
    fun pocket(n: Int): Color = if (n == 0) green else if (Wheel.RED[n]) red else black
    fun tone(t: String): Color = when (t) { "ok" -> ok; "warn" -> warn; "bad" -> bad; "purple" -> purple; "blue" -> blue; "gold" -> gold; else -> dim }
}

@Composable
fun LrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.blue, onPrimary = Color.White, secondary = C.gold, background = C.bg, onBackground = C.text,
            surface = C.card, onSurface = C.text, error = C.bad
        ),
        content = content
    )
}

val Mono = FontFamily.Monospace

@Composable
fun T(text: String, color: Color = C.text, size: Int = 14, bold: Boolean = false, mono: Boolean = false, align: TextAlign? = null, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFamily = if (mono) Mono else FontFamily.Default, textAlign = align, modifier = modifier)
}

@Composable
fun LrCard(modifier: Modifier = Modifier, title: String = "", right: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(C.card)
            .border(BorderStroke(1.dp, C.line), RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        if (title.isNotEmpty() || right.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T(title, C.dim, 11, true, modifier = Modifier.weight(1f))
                if (right.isNotEmpty()) T(right, C.dim2, 11)
            }
        }
        content()
    }
}

@Composable
fun Pill(text: String, tone: String = "line", modifier: Modifier = Modifier) {
    val col = C.tone(tone)
    val fill = if (tone == "line") C.card2 else col.copy(alpha = 0.16f)
    Box(modifier.padding(end = 6.dp, bottom = 4.dp).clip(RoundedCornerShape(50)).background(fill).border(BorderStroke(1.dp, if (tone == "line") C.line else col.copy(alpha = 0.55f)), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        T(text, if (tone == "line") C.text else col, 11, true)
    }
}

@Composable
fun Banner(text: String, tone: String = "info", modifier: Modifier = Modifier) {
    val col = when (tone) { "warn" -> C.warn; "bad" -> C.bad; "ok" -> C.ok; "info" -> C.blue; else -> C.dim }
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.12f)).border(BorderStroke(1.dp, col.copy(alpha = 0.45f)), RoundedCornerShape(10.dp)).padding(10.dp)) {
        T(text, C.text, 12)
    }
}

@Composable
fun KV(label: String, value: String, tone: String = "", sub: String = "", mono: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(label, C.dim, 12, modifier = Modifier.weight(1f).padding(end = 8.dp))
            T(value, if (tone.isEmpty()) C.text else C.tone(tone), 13, true, mono)
        }
        if (sub.isNotEmpty()) T(sub, C.dim2, 10)
    }
}

@Composable
fun NumChip(n: Int, size: Dp = 30.dp, fs: Int = 13, ring: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape).background(C.pocket(n)).border(BorderStroke(if (ring) 2.dp else 1.dp, if (ring) C.gold else Color(0x33FFFFFF)), CircleShape),
        contentAlignment = Alignment.Center
    ) { T(n.toString(), Color.White, fs, true, true) }
}

/**
 * Tüm bahis sınıflarını ve sıfırı aynı anda gösterir. Altın çerçeve, Table modelinin
 * kilitli tahminindeki en yüksek sınıfı belirtir; diğer yüzdeler alternatif sınıflardır.
 */
@Composable
fun TableDistributionRow(call: TableCall, dense: Boolean = false) {
    val classCount = TableCats.classes(call.cat)
    val shape = RoundedCornerShape(7.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = if (dense) 2.dp else 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(TableCats.IDS[call.cat], C.dim, if (dense) 8 else 10, true, modifier = Modifier.weight(1f))
            T("Seçim: ${call.pick}", C.gold, if (dense) 8 else 10, true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 4.dp)) {
            for (i in 0..classCount) {
                val zero = i == classCount
                val selected = !zero && i == call.cls
                val fill = when {
                    call.cat == TableCats.COLOR && i == 0 -> C.red
                    call.cat == TableCats.COLOR && i == 1 -> C.black
                    zero -> C.green
                    selected -> C.blueDeep.copy(alpha = 0.35f)
                    else -> C.card2
                }
                val label = if (zero) "0 · yeşil" else TableCats.PICKS[call.cat][i]
                val fg = if (call.cat == TableCats.COLOR || zero) Color.White else if (selected) C.gold else C.dim
                Column(
                    Modifier.weight(1f).clip(shape).background(fill)
                        .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) C.gold else C.line), shape)
                        .padding(horizontal = if (dense) 1.dp else 3.dp, vertical = if (dense) 3.dp else 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    T(label, fg, if (dense) 7 else 9, selected, align = TextAlign.Center)
                    T(S.pc(call.probability(i) * 100, 0), if (selected) C.gold else Color.White,
                        if (dense) 9 else 11, true, true, TextAlign.Center)
                }
            }
        }
        T(
            "Taban: dış taraf ${S.pc(TableCats.baseline(call.cat) * 100, 1)} · 0 ${S.pc(TableCats.baseline(call.cat, classCount) * 100, 1)}",
            C.dim2, if (dense) 7 else 9
        )
    }
}

@Composable
fun LrButton(text: String, onClick: () -> Unit, kind: String = "primary", enabled: Boolean = true, modifier: Modifier = Modifier) {
    val bg = if (!enabled) C.card2 else when (kind) { "primary" -> C.blueDeep; "ok" -> C.ok; "danger" -> C.bad.copy(alpha = 0.85f); "gold" -> C.gold; else -> C.card2 }
    val fg = if (!enabled) C.dim2 else if (kind == "gold") Color(0xFF1A1400) else Color.White
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg).border(BorderStroke(1.dp, if (kind == "ghost") C.line else Color.Transparent), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { T(text, fg, 13, true, align = TextAlign.Center) }
}

/** Yatay kaydırılan sekme çipleri (sekme göstergesi: aktif mavi). */
@Composable
fun SegTabs(items: List<String>, sel: Int, onSel: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((i, s) in items.withIndex()) {
            val on = i == sel
            Box(
                Modifier.testTag("seg_$s").clip(RoundedCornerShape(50)).background(if (on) C.blueDeep else C.card2)
                    .border(BorderStroke(1.dp, if (on) C.blue else C.line), RoundedCornerShape(50)).clickable { onSel(i) }.padding(horizontal = 12.dp, vertical = 7.dp)
            ) { T(s, if (on) Color.White else C.dim, 12, on) }
        }
    }
}

@Composable
fun Divider1() { Box(Modifier.fillMaxWidth().height(1.dp).background(C.line)) }

@Composable
fun HGap(w: Int = 8) { Spacer(Modifier.width(w.dp)) }

@Composable
fun VGap(h: Int = 8) { Spacer(Modifier.height(h.dp)) }

/** Sınıf rozeti (A–F, O, L, S). */
@Composable
fun ClassBadge(cls: String) {
    val col = when (cls) { "A" -> C.ok; "B" -> C.blue; "C" -> C.dim; "D", "F" -> C.warn; "L" -> C.bad; "O" -> C.purple; "S" -> C.warn; else -> C.dim }
    Box(Modifier.size(26.dp).clip(CircleShape).background(col.copy(alpha = 0.2f)).border(BorderStroke(1.5.dp, col), CircleShape), contentAlignment = Alignment.Center) { T(cls, col, 13, true) }
}

const val DISCLAIMER = "Bu sistem geleceği bilmez. Her tahmin yalnızca geçmişten üretilir, sonuç açılmadan kilitlenir; hiçbir sonuç garanti edilmez. Yüksek isabet oranı tek başına avantaj kanıtı değildir."
