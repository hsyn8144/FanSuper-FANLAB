package fan.lightningroulette.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fan.lightningroulette.core.Candidate
import fan.lightningroulette.core.Eval
import fan.lightningroulette.core.PredView
import fan.lightningroulette.core.Regions
import fan.lightningroulette.core.S
import fan.lightningroulette.core.TableCats
import fan.lightningroulette.core.Wheel
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.engine.EngineUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun timeOf(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

fun pctText(c: Candidate, pred: PredView, mode: String): Pair<String, String> {
    val p = if (mode == "model") pred.pKotlin[c.n] else c.p
    return S.pc(p * 100) to "×" + S.f(p * Wheel.N, 2)
}

@Composable
fun StatusChips(ui: EngineUi) {
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Column {
            Row {
                Pill("DATASET-" + ui.datasetId.toString().padStart(3, '0') + " v" + ui.datasetVersion + " · " + S.th(ui.spinCount) + " spin", "blue")
                Pill("Champion " + ui.champion, "line")
            }
            Row {
                when (ui.pyStatus) {
                    "OK" -> Pill("🐍 Python OK", "ok")
                    "ERROR" -> Pill("🐍 Python ERROR · Kotlin-only", "bad")
                    else -> Pill("🐍 Python kapalı · Kotlin-only", "line")
                }
                Pill("OFFLINE", "line")
                if (ui.synthetic) Pill("SYN · örnek veri", "warn")
            }
        }
    }
}

@Composable
fun LifecycleStrip(locked: Boolean) {
    val steps = listOf("Geçmiş", "Tahmin", "🔒 LOCK", "Gerçek sonuç", "Değerlendirme", "Öğrenme")
    val active = if (locked) 2 else 0
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for ((i, s) in steps.withIndex()) {
            val on = i == active
            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) C.blueDeep else C.card2).border(BorderStroke(1.dp, if (on) C.blue else C.line), RoundedCornerShape(8.dp)).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                T(s, if (on) Color.White else C.dim2, 9, on, align = TextAlign.Center)
            }
        }
    }
}

@Composable
fun HomeScreen(ui: EngineUi, onDetail: () -> Unit, onLab: () -> Unit) {
    val pct = Engine.settings.pctMode
    val mask = Engine.settings.tableMask
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { T("⚡ Lightning Roulette AI", C.text, 18, true); }
        StatusChips(ui)
        if (ui.pyStatus == "ERROR") Banner("Python meclisi ${ui.pyMessage.ifEmpty { "çalışmıyor" }}. Tahminler yalnızca Kotlin meclisinden üretiliyor; bu Python sonucu gibi gösterilmez.", "warn")
        LifecycleStrip(ui.pred != null)
        val pred = ui.pred
        if (pred == null) {
            LrCard(title = "ÖĞRENİYOR") {
                T("${minOf(ui.spinCount, ui.minSample)} / ${ui.minSample} spin", C.text, 20, true, true)
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.card2)) {
                    Box(Modifier.fillMaxWidth((minOf(ui.spinCount, ui.minSample) / ui.minSample.toFloat()).coerceIn(0f, 1f)).height(8.dp).background(C.blue))
                }
                T("Tahmin için en az ${ui.minSample} spin gerekir (öğrenme eşiği). Aşağıdaki tuşlarla sonuç gir ya da Veri sekmesinden örnek veri / dosya yükle.", C.dim, 12)
            }
        } else {
            LrCard(title = "KİLİT") {
                Row(verticalAlignment = Alignment.CenterVertically) { T("🔒 ", C.gold, 16); T(pred.code, C.gold, 15, true, true) }
                T("referans spin #${pred.refCount} · kilit ${timeOf(pred.createdAt)} · model ${pred.modelVersion}", C.dim, 11)
                T("DATASET-" + ui.datasetId.toString().padStart(3, '0') + " v${ui.datasetVersion} · Kilit atomiktir: aynı spin için ikinci tahmin üretilmez; kilitli tahmin sonradan değiştirilemez.", C.dim2, 10)
            }
            LrCard(title = "NEXT · ${pred.candidates.size} aday", right = "dokun → detay", modifier = Modifier.clickable { onDetail() }) {
                for ((i, c) in pred.candidates.withIndex()) {
                    val (ptxt, lift) = pctText(c, pred, pct)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(RANK_COLORS[i % RANK_COLORS.size]).padding(horizontal = 7.dp, vertical = 2.dp)) { T("${i + 1}", Color(0xFF111111), 11, true) }
                        NumChip(c.n, 32.dp)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            T(c.label + "  ·  S${c.sector + 1} · ${Regions.NAMES[c.region]}", C.text, 12, true)
                            T(c.span.joinToString("·"), C.dim, 11, mono = true)
                        }
                        Column(horizontalAlignment = Alignment.End) { T(ptxt, C.text, 13, true, true); T(lift, C.gold, 10, mono = true) }
                    }
                }
                Divider1()
                T("Kapsama: ${pred.coverage}/37 = ${S.pc(pred.coverage * 100.0 / 37)} — daha çok aday/komşu = daha yüksek kapsama; isabet kapsama tabanıyla karşılaştırılır.", C.dim2, 10, modifier = Modifier.padding(top = 6.dp))
            }
            LrCard(title = "TABLE · 5 kategori (sayıdan bağımsız model)") {
                for (call in pred.table) {
                    if ((mask shr call.cat) and 1 == 0) continue
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(TableCats.IDS[call.cat], C.dim, 10, true, modifier = Modifier.weight(1.1f))
                        T(call.pick, C.text, 13, true, modifier = Modifier.weight(1.3f))
                        Box(Modifier.weight(1.6f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.card2)) {
                            Box(Modifier.fillMaxWidth(call.p.toFloat().coerceIn(0f, 1f)).height(8.dp).background(C.warn))
                            Row(Modifier.fillMaxWidth()) {
                                Box(Modifier.weight(call.base.toFloat().coerceIn(0.01f, 0.99f)).height(8.dp))
                                Box(Modifier.width(2.dp).height(8.dp).background(C.gold))
                                Box(Modifier.weight((1f - call.base.toFloat()).coerceIn(0.01f, 0.99f)).height(8.dp))
                            }
                        }
                        T(S.pc(call.p * 100), C.warn, 12, true, true, TextAlign.End, Modifier.weight(0.9f))
                    }
                }
                T("Sarı çizgi = tesadüf tabanı. Table, sayı tahmininin türevi değildir.", C.dim2, 10)
            }
        }
        Banner(DISCLAIMER, "warn")
        EvaluationCard(ui)
        InputPad(ui)
        SummaryCard(ui, onLab)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LrButton("Tahmin detayı", onDetail, "ghost", ui.pred != null, Modifier.weight(1f))
            LrButton("LAB Overview", onLab, "ghost", true, Modifier.weight(1f))
        }
    }
}

@Composable
fun EvaluationCard(ui: EngineUi) {
    val ev: Eval = ui.eval ?: return
    val p = ui.evaluated
    LrCard(title = "SONUÇ DEĞERLENDİRMESİ · ${p?.code ?: ""}", right = "kapat ✕", modifier = Modifier.clickable { Engine.clearEval() }) {
        Row(verticalAlignment = Alignment.CenterVertically) { NumChip(ev.actual, 40.dp, 16, true); HGap(10); Column {
            T("Gerçek sonuç: ${ev.actual}", C.text, 15, true)
            T("Sector S${(p?.candidates?.firstOrNull()?.sector ?: 0) + 1} → ${if (ev.sector) "✓" else "✗"} · Region ${if (ev.region) "✓" else "✗"} (${Regions.NAMES[Regions.of[ev.actual]]})", C.dim, 11)
        } }
        VGap(6)
        Row { Pill("Exact " + (if (ev.exact) "✓" else "✗"), if (ev.exact) "ok" else "line"); Pill("Candidate " + (if (ev.candidate) "✓ rank ${ev.candRank}" else "✗"), if (ev.candidate) "ok" else "line"); Pill("Neighbor " + (if (ev.neighbor) "✓" else "✗"), if (ev.neighbor) "ok" else "line") }
        Row { Pill("Sector " + (if (ev.sector) "✓" else "✗"), if (ev.sector) "ok" else "line"); Pill("Region " + (if (ev.region) "✓" else "✗"), if (ev.region) "ok" else "line"); Pill("Table ${ev.tableCount}/5", if (ev.tableCount >= 3) "ok" else "line") }
        if (p != null) {
            Row { for (q in 0 until 5) Pill(p.table[q].pick + (if (ev.tableHits[q]) " ✓" else " ✗"), if (ev.tableHits[q]) "ok" else "line") }
        }
        T("Öğrenme zinciri: değerlendirme → exact/candidate → sector/region/table → model hatası → feature sonucu → veritabanı → artımlı güncelleme → yeni tahmin. Ağırlıklar körlemesine değil, sınırlı adımlarla güncellenir. Tahmin sonuçtan ÖNCE kilitlendi; öğrenme yalnızca sonuç açıldıktan sonra yapıldı.", C.dim2, 10, modifier = Modifier.padding(top = 4.dp))
        if (ui.pred != null) T("Yeni tahmin ${ui.pred.code} kilitlendi.", C.gold, 11, true)
    }
}

@Composable
fun InputPad(ui: EngineUi) {
    var text by rememberSaveable { mutableStateOf("") }
    var armedAt by remember { mutableStateOf(0L) }
    var lastEnter by remember { mutableStateOf(0L) }
    var hint by remember { mutableStateOf("") }
    val value = text.toIntOrNull()
    val valid = value != null && value in 0..36
    val invalid = text.isNotEmpty() && !valid
    fun digit(d: Int) {
        var t = text + d
        if (t.length == 2 && t[0] == '0') t = t.substring(1)      // baştaki sıfır normalize: 07 → 7
        if (t.length <= 2) text = t
        hint = ""
    }
    LrCard(title = "CANLI SONUÇ GİRİŞİ") {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (invalid) C.bad.copy(alpha = 0.18f) else C.card2)
                .border(BorderStroke(2.dp, if (invalid) C.bad else if (valid) C.ok else C.line), RoundedCornerShape(10.dp)).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (text.isEmpty()) T("sonuç (0–36)", C.dim2, 18, mono = true)
            else T(if (invalid) "$text  ·  0–36 DIŞI!" else text, if (invalid) C.bad else C.text, 24, true, true)
        }
        VGap(8)
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("DEL", "0", "ENTER"))
        for (row in keys) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in row) {
                    val isEnter = k == "ENTER"
                    val on = !isEnter || valid
                    val bg = if (isEnter) (if (valid) C.ok else C.card2) else if (k == "DEL") C.card2 else C.blueDeep.copy(alpha = 0.55f)
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(bg).border(BorderStroke(1.dp, C.line), RoundedCornerShape(10.dp))
                            .clickable(enabled = on) {
                                when (k) {
                                    "DEL" -> {
                                        if (text.isNotEmpty()) { text = text.dropLast(1); hint = "" }
                                        else {
                                            val now = System.currentTimeMillis()
                                            if (now - armedAt < 3000) { armedAt = 0; hint = ""; Engine.undo() }
                                            else if (ui.spinCount > 0) { armedAt = now; hint = "Son kayıtlı spini silmek için 3 sn içinde DEL’e tekrar bas." }
                                        }
                                    }
                                    "ENTER" -> {
                                        val now = System.currentTimeMillis()
                                        if (valid && now - lastEnter > 450) { lastEnter = now; Engine.enter(value!!, ui.spinCount); text = ""; hint = "" }
                                    }
                                    else -> digit(k.toInt())
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { T(k, if (isEnter && !valid) C.dim2 else Color.White, if (k.length > 1) 13 else 18, true) }
                }
            }
        }
        if (hint.isNotEmpty()) Banner(hint, "warn")
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LrButton("↩ Son spini geri al", { Engine.undo() }, "ghost", ui.spinCount > 0, Modifier.weight(1f))
        }
        T("Akış: kayıt → değerlendirme → öğrenme → yeni tahmin + LOCK. Çift ENTER ikinci kayıt oluşturmaz.", C.dim2, 10, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SummaryCard(ui: EngineUi, onLab: () -> Unit) {
    LrCard(title = "SON OOS PENCERESİ · n = ${ui.sumN}", right = "sınıf") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("Exact ve Candidate ayrı satırlardır", C.dim2, 10, modifier = Modifier.weight(1f)); ClassBadge(ui.cls)
        }
        if (ui.summary.isEmpty()) T("Henüz değerlendirilmiş tahmin yok.", C.dim, 12)
        else {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { for ((i, h) in listOf("Ölçüt", "Gözlenen", "Taban", "Fark", "%95 CI").withIndex()) T(h, C.dim, 10, true, align = if (i == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(if (i == 0) 1.3f else 1f)) }
            Divider1()
            for (r in ui.summary) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                T(r.name, C.text, 12, true, modifier = Modifier.weight(1.3f))
                T(S.pc(r.obs), C.text, 11, mono = true, align = TextAlign.End, modifier = Modifier.weight(1f))
                T(S.pc(r.base), C.dim, 11, mono = true, align = TextAlign.End, modifier = Modifier.weight(1f))
                T(S.sg(r.delta, 1, ""), if (r.delta > 0.05) C.ok else if (r.delta < -0.05) C.bad else C.dim, 11, true, true, TextAlign.End, Modifier.weight(1f))
                T("[" + S.f(r.lo - r.base, 1) + "; " + S.f(r.hi - r.base, 1) + "]", C.dim2, 10, mono = true, align = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
        VGap(4)
        for (f in ui.flags) Row(Modifier.padding(vertical = 1.dp)) { T("⚠ ", C.warn, 11, true); T(f, C.text, 11) }
    }
}

/** Tahmin detayı (Ekran 11): wheel, geometri, skor katkıları, Table uyumu, meclis uyuşmazlığı. */
@Composable
fun PredictionDetail(ui: EngineUi, onBack: () -> Unit, onLab: () -> Unit) {
    val pred = ui.pred
    val sectors = Engine.settings.sectors()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { LrButton("←", onBack, "ghost"); HGap(10); T("Tahmin detayı", C.text, 17, true) }
        if (pred == null) { VGap(12); Banner("Henüz kilitli tahmin yok (öğrenme eşiği: ${ui.minSample} spin).", "info"); return@Column }
        T(pred.code + " · referans spin #" + pred.refCount, C.gold, 12, true, true, modifier = Modifier.padding(top = 8.dp))
        WheelView(pred.candidates, ui.last8, sectors, selected = -1)
        T("Altın halka = merkez numara · renkli yay = komşu aralığı · beyaz noktalar SON 8 (1 = en yeni).", C.dim2, 10)
        val top = pred.candidates.firstOrNull()
        if (top != null) {
            LrCard(title = "GEOMETRİ · en üst aday ${top.label}") {
                KV("Wheel indeksi", "${Wheel.POS[top.n]}")
                KV("Sektör / bölge", "S${top.sector + 1} / ${Regions.NAMES[top.region]}")
                if (ui.last8.isNotEmpty()) {
                    val last = ui.last8[0]
                    KV("Son sonuca (${last}) mesafe", "saat yönü ${Wheel.cw(last, top.n)} · ters ${Wheel.ccw(last, top.n)} · dairesel ${Wheel.circ(last, top.n)}")
                }
                KV("Kalibre P(exact)", S.pc(top.p * 100, 2) + "  (×" + S.f(top.p * Wheel.N, 2) + ")")
            }
        }
        for ((i, c) in pred.candidates.withIndex()) {
            LrCard(title = "SKOR KATKILARI · ${i + 1}. ${c.label}") {
                val mx = (c.contrib.values.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0).coerceAtLeast(0.01)
                for ((name, v) in c.contrib) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(name, C.dim, 11, modifier = Modifier.weight(1.4f))
                        Box(Modifier.weight(2f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.card2)) {
                            val f = (kotlin.math.abs(v) / mx).toFloat().coerceIn(0f, 1f)
                            Row(Modifier.fillMaxWidth()) {
                                if (v >= 0) { Box(Modifier.weight(1f)); Box(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth(f).height(8.dp).background(C.ok)) } }
                                else { Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Box(Modifier.fillMaxWidth(f).height(8.dp).background(C.bad)) }; Box(Modifier.weight(1f)) }
                            }
                        }
                        T(S.sg(v, 2, ""), if (v >= 0) C.ok else C.bad, 11, true, true, TextAlign.End, Modifier.weight(0.8f))
                    }
                }
            }
        }
        val bad = pred.councilsDisagree
        if (bad) Banner("Kotlin ve Python meclisleri bu spin için farklı top-1 oyu veriyor. Aynı hatayı yapan modeller bağımsız kanıt sayılmaz.", "warn")
        LrCard(title = "MECLİS OYLARI · bu spin için top-1") {
            for (v in pred.votes) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                T((if (v.council == "K") "🔵 " else "🐍 ") + v.title, C.dim, 11, modifier = Modifier.weight(1.8f))
                NumChip(v.top, 24.dp, 10); HGap(6)
                T(S.pc(v.p * 100), C.text, 11, true, true, TextAlign.End, Modifier.weight(0.8f))
                T("w " + S.f(v.weight * 100, 0) + "%", C.dim2, 10, mono = true, align = TextAlign.End, modifier = Modifier.weight(0.7f))
            }
        }
        Banner("Dürüstlük notu: skorlar nedensel açıklama değil, modelin sinyalleridir; sonuç garanti değildir.", "info")
        LrButton("LAB’da incele", onLab, "ghost", true, Modifier.fillMaxWidth())
    }
}
