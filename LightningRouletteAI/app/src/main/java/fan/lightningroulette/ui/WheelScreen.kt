package fan.lightningroulette.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fan.lightningroulette.core.Dir
import fan.lightningroulette.core.Regions
import fan.lightningroulette.core.S
import fan.lightningroulette.core.Sectors
import fan.lightningroulette.core.TableCats
import fan.lightningroulette.core.Wheel
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.engine.EngineUi

@Composable
fun WheelScreen(ui: EngineUi) {
    var sub by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        T("◎ Wheel & Masa", C.text, 18, true)
        SegTabs(listOf("Wheel", "Masa", "Sektörler"), sub, { sub = it })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (sub) { 0 -> WheelTab(ui); 1 -> TableLayoutTab(ui); else -> SectorsTab() }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(if (on) C.blueDeep else C.card2).border(BorderStroke(1.dp, if (on) C.blue else C.line), RoundedCornerShape(50)).clickable { set(!on) }.padding(horizontal = 10.dp, vertical = 6.dp)) {
        T(label, if (on) Color.White else C.dim, 11, on)
    }
}

@Composable
private fun WheelTab(ui: EngineUi) {
    var cand by rememberSaveable { mutableStateOf(true) }; var sec by rememberSaveable { mutableStateOf(true) }
    var reg by rememberSaveable { mutableStateOf(true) }; var last by rememberSaveable { mutableStateOf(true) }
    var sel by rememberSaveable { mutableStateOf(-1) }
    var k by rememberSaveable { mutableStateOf(2) }; var dir by rememberSaveable { mutableStateOf("bi") }
    val sectors = Engine.settings.sectors()
    Row(Modifier.padding(top = 4.dp)) { Toggle("Aday", cand) { cand = it }; Toggle("Sektör", sec) { sec = it }; Toggle("Bölge", reg) { reg = it }; Toggle("SON 8", last) { last = it } }
    WheelView(ui.pred?.candidates ?: emptyList(), ui.last8, sectors, cand, sec, reg, last, sel, { sel = it })
    T("Renkli halka = aday merkezi · dış yay = komşu aralığı · rozet = rank 1–5 · beyaz noktalar SON 8 (1 = en yeni). Çarkta bir numaraya dokun.", C.dim2, 10)
    LrCard(title = "K VE YÖN") {
        Row { for (kk in 1..3) Toggle("k$kk", k == kk) { k = kk }; HGap(8); Toggle("↔", dir == "bi") { dir = "bi" }; Toggle("L", dir == "L") { dir = "L" }; Toggle("R", dir == "R") { dir = "R" } }
        T("↔ iki yön · L saat yönünün tersi · R saat yönü (örn. 17-L2, 17-R1).", C.dim2, 10)
    }
    if (sel >= 0) {
        val span = Wheel.span(sel, k, Dir.of(dir))
        LrCard(title = "SEÇİLİ CEP") {
            Row(verticalAlignment = Alignment.CenterVertically) { NumChip(sel, 36.dp, 14, true); HGap(10); Column { T("$sel · ${if (sel == 0) "yeşil" else if (Wheel.RED[sel]) "kırmızı" else "siyah"}", C.text, 14, true); T("wheel indeksi ${Wheel.POS[sel]} · S${sectors.of[sel] + 1} · ${Regions.NAMES[Regions.of[sel]]}", C.dim, 11) } }
            VGap(6)
            T("${if (dir == "bi") "$sel-k$k" else "$sel-${dir}$k"}: " + span.joinToString("·"), C.gold, 12, true, true)
            if (ui.last8.isNotEmpty()) T("son sonuca (${ui.last8[0]}) mesafe: saat yönü ${Wheel.cw(ui.last8[0], sel)} · ters ${Wheel.ccw(ui.last8[0], sel)} · dairesel ${Wheel.circ(ui.last8[0], sel)}", C.dim, 11)
        }
    }
    LrCard(title = "FİZİKSEL SIRA · saat yönü") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { for (n in Wheel.ORDER) { NumChip(n, 24.dp, 9) } }
        T("37 numaranın çark üzerindeki dizilişi (masa sırası değil).", C.dim2, 10)
    }
}

@Composable
private fun RowScope.TCell(text: String, bg: Color, on: Boolean) {
    Box(Modifier.weight(1f).padding(1.5.dp).height(34.dp).clip(RoundedCornerShape(6.dp)).background(bg).border(BorderStroke(if (on) 2.5.dp else 0.dp, if (on) C.gold else Color.Transparent), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { T(text, Color.White, 11, true, align = TextAlign.Center) }
}

@Composable
private fun TableLayoutTab(ui: EngineUi) {
    val p = ui.pred
    val picks = p?.table ?: emptyList()
    fun picked(cat: Int, cls: Int) = picks.any { it.cat == cat && it.cls == cls }
    val s = Engine.settings; val v by s.version.collectAsState()
    LrCard(title = "MASA · bahis düzeni (altın çerçeve = modelin seçimi)") {
        Row(Modifier.fillMaxWidth()) { TCell("0", C.green, false) }
        for (r in 0 until 12) Row(Modifier.fillMaxWidth()) { for (c in 0 until 3) { val n = 3 * r + c + 1; TCell(n.toString(), C.pocket(n), false) } }
        Row(Modifier.fillMaxWidth()) { for (c in 0 until 3) TCell("COL ${c + 1}", C.card2, picked(TableCats.COLUMN, c)) }
        Row(Modifier.fillMaxWidth()) { for (d in 0 until 3) TCell("${d + 1}. 12", C.card2, picked(TableCats.DOZEN, d)) }
        Row(Modifier.fillMaxWidth()) { TCell("1–18", C.card2, picked(TableCats.HIGHLOW, 0)); TCell("ÇİFT", C.card2, picked(TableCats.PARITY, 0)); TCell("KIRMIZI", C.red, picked(TableCats.COLOR, 0)) }
        Row(Modifier.fillMaxWidth()) { TCell("SİYAH", C.black, picked(TableCats.COLOR, 1)); TCell("TEK", C.card2, picked(TableCats.PARITY, 1)); TCell("19–36", C.card2, picked(TableCats.HIGHLOW, 1)) }
    }
    LrCard(title = "TABLE OLASILIKLARI") {
        if (p == null) T("Kilitli tahmin yok.", C.dim, 12)
        for (c in picks) KV(TableCats.IDS[c.cat] + " · " + c.pick, S.pc(c.p * 100) + " / taban " + S.pc(c.base * 100) + " (" + S.sg((c.p - c.base) * 100, 1) + ")", if (c.p > c.base + 0.02) "warn" else "")
        T("Table modelleri kendi özellikleriyle öğrenir; sayı tahmininin türevi değildir.", C.dim2, 10)
    }
    val top = p?.candidates?.firstOrNull()
    if (top != null) {
        var agree = 0
        for (c in picks) if (TableCats.classOf(c.cat, top.n) == c.cls) agree++
        LrCard(title = "WHEEL ↔ TABLE") {
            KV("Rank-1 aday ${top.n} ile Table seçimleri uyumu", "$agree / ${picks.size}", if (agree >= 4) "warn" else "")
            T("Table yalnızca Wheel’i tekrar ediyorsa bağımsız teyit sayılmaz; hata korelasyonu LAB › Side/Table’da izlenir.", C.dim2, 10)
        }
    }
    LrCard(title = "KATEGORİ ANAHTARLARI") {
        for (q in 0 until 5) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(TableCats.IDS[q], C.text, 12, modifier = Modifier.weight(1f))
            Switch(checked = (s.tableMask shr q) and 1 == 1, onCheckedChange = { on -> s.tableMask = if (on) s.tableMask or (1 shl q) else s.tableMask and (1 shl q).inv() })
        }
        if (v < 0) T("", C.dim)
    }
}

@Composable
private fun SectorsTab() {
    val saved = Engine.settings.sectors()
    var bounds by remember { mutableStateOf(saved.bounds.toList()) }
    val cur = Sectors.fromBounds(bounds) ?: saved
    LrCard(title = "SEKTÖRLER · ${cur.count}") {
        for (s in 0 until cur.count) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("S${s + 1} · ${cur.size(s)} cep · taban ${S.pc(cur.baseline(s) * 100)}", C.text, 12, true)
                    T(cur.members(s).joinToString("·"), C.dim, 10, mono = true)
                }
                if (s < cur.count - 1) {
                    val b = bounds[s + 1]
                    LrButton("−", { if (b - 1 > bounds[s]) bounds = bounds.toMutableList().also { it[s + 1] = b - 1 } }, "ghost"); HGap(4)
                    LrButton("+", { if (b + 1 < bounds[s + 2]) bounds = bounds.toMutableList().also { it[s + 1] = b + 1 } }, "ghost")
                }
            }
        }
        T("− / + komşu sektör sınırını bir cep kaydırır. Boyut farklı olduğundan taban = cep sayısı / 37.", C.dim2, 10)
    }
    LrCard(title = "BÖLGELER (klasik)") {
        KV("VOISINS", "${Regions.VOISINS.size} cep · taban ${S.pc(Regions.VOISINS.size * 100.0 / 37)}", mono = false)
        KV("TIERS", "${Regions.TIERS.size} cep · taban ${S.pc(Regions.TIERS.size * 100.0 / 37)}", mono = false)
        KV("ORPHELINS", "${Regions.ORPH.size} cep · taban ${S.pc(Regions.ORPH.size * 100.0 / 37)}", mono = false)
    }
    Banner("Tanım değişirse sektör/bölge özellikleri yeni sürümle yeniden hesaplanır; kilitli tahmin etkilenmez, LAB’da yeni feature sürümü oluşur.", "warn")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LrButton("Varsayılana dön", { bounds = Sectors.DEFAULT_BOUNDS.toList() }, "ghost", true, Modifier.weight(1f))
        LrButton("Kaydet", { Engine.settings.sectorBounds = bounds.joinToString(","); Engine.applySettings(); fan.lightningroulette.lab.LabManager.invalidate() }, "primary", bounds != saved.bounds.toList(), Modifier.weight(1f))
    }
}
