package fan.lightningroulette.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fan.lightningroulette.core.LabCtx
import fan.lightningroulette.core.LabTabs
import fan.lightningroulette.core.Referee
import fan.lightningroulette.core.S
import fan.lightningroulette.core.TableCats
import fan.lightningroulette.core.Wheel
import fan.lightningroulette.data.ModelVersionE
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.engine.EngineUi
import fan.lightningroulette.lab.LabManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** LAB bağlamını arka planda yükler (taban koşusu önbellekteyse). */
@Composable
fun rememberLabCtx(): State<LabCtx?> {
    val lab by LabManager.ui.collectAsState()
    return produceState<LabCtx?>(initialValue = null, lab.version, lab.baseReady) {
        value = withContext(Dispatchers.Default) { try { LabManager.ctx() } catch (e: Exception) { null } }
    }
}

private val K_DESC = mapOf(
    "wheel" to "dairesel mesafe · yerel yoğunluk", "sector" to "sektör frekans · geçiş · entropi", "region" to "bölge kalıcılığı · dwell/return",
    "neighbor" to "k1/k2/k3 · sol/sağ yön", "frequency" to "global · recent · rolling · decay", "pattern" to "exact / karışık dizi",
    "transition" to "1. · 2. · 3. derece", "kml" to "softmax regresyon · özellik grupları"
)
private val P_DESC = mapOf(
    "lstm" to "raw history + wheel", "transformer" to "dikkat · induction head", "cnn" to "wheel dizisi", "gboost" to "tablo + wheel özellikleri",
    "hmm" to "sektör rejimleri", "knn_dtw" to "ofset dizisi benzerliği", "context" to "sektör bağlamı", "motif" to "tekrarlayan sektör motifleri"
)

@Composable
fun CouncilScreen(ui: EngineUi) {
    var sub by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        T("🏛 Meclisler", C.text, 18, true)
        SegTabs(listOf("Kotlin", "Python", "Table", "Hakem", "Champion"), sub, { sub = it })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (sub) { 0 -> KotlinCouncil(ui); 1 -> PythonCouncil(ui); 2 -> TableCouncil(ui); 3 -> RefereeTab(); else -> ChampionTab() }
        }
    }
}

@Composable
private fun MemberRow(title: String, desc: String, weight: Double, delta: Double?, top: Int, bench: Boolean, extra: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (bench) C.warn else C.ok))
        Column(Modifier.weight(1.6f).padding(start = 8.dp)) { T(title, C.text, 12, true); T(desc + extra, C.dim2, 10) }
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(C.card2)) { Box(Modifier.fillMaxWidth(weight.toFloat().coerceIn(0f, 1f)).height(7.dp).background(C.blue)) }
            T("w " + S.f(weight * 100, 0) + "%", C.dim, 9, mono = true)
        }
        T(if (delta == null) "—" else S.sg(delta, 1, ""), if (delta == null) C.dim2 else if (delta > 0.05) C.ok else if (delta < -0.05) C.bad else C.dim, 11, true, true, TextAlign.End, Modifier.weight(0.6f))
        if (top >= 0) NumChip(top, 24.dp, 10)
    }
}

@Composable
private fun KotlinCouncil(ui: EngineUi) {
    val ctx by rememberLabCtx()
    val votes = ui.pred?.votes?.filter { it.council == "K" } ?: emptyList()
    if (votes.isEmpty()) Banner("Henüz kilitli tahmin yok (öğrenme eşiği ${ui.minSample} spin). Üye satırları ilk tahminden sonra görünür.", "info")
    LrCard(title = "🔵 KOTLIN MECLİSİ · 8 üye", right = "Δ top-5 (pp, son OOS)") {
        val c = ctx
        for ((j, v) in votes.withIndex()) {
            val d = if (c != null && j < (c.oos.firstOrNull()?.memberTop5Hit?.size ?: 0) && c.oos.isNotEmpty()) (c.oos.count { it.memberTop5Hit[j] }.toDouble() / c.oos.size - 5.0 / Wheel.N) * 100 else null
            MemberRow(v.title, K_DESC[v.id] ?: "", v.weight, d, v.top, v.weight < 0.03)
        }
        T("● yeşil aktif · ● sarı BENCH (ağırlığı çok düşük)", C.dim2, 10)
    }
    LrCard(title = "AĞIRLIKLAR") { T("Körlemesine değiştirilmez: log-skor kazancı ve kalibrasyon cezasıyla yumuşatılmış, alt sınırı olan Fixed-Share (Hedge) güncellemesi. Güncelleme yalnızca sonuç açıldıktan sonra yapılır ve sürümlüdür.", C.text, 12) }
    Banner("Üyelerin aynı sayıda toplanması bağımsız kanıt değildir; aynı hatayı yapan modeller ayrı sayılmaz (bkz. LAB › Diversity).", "info")
    Banner("Bir üyeyi kapatmak canlı modeli değiştirmez; yeni bir Challenger oluşturur ve LAB’da test edilir.", "info")
}

@Composable
private fun PythonCouncil(ui: EngineUi) {
    val ctx by rememberLabCtx()
    var confirm by remember { mutableStateOf(false) }
    val s = Engine.settings; val ver by s.version.collectAsState()
    val votes = ui.pred?.votes?.filter { it.council == "P" } ?: emptyList()
    val lam = Engine.py?.lastLam ?: emptyList()
    LrCard(title = "🐍 PYTHON MECLİSİ") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (ui.pyStatus) { "OK" -> Pill("status = OK", "ok"); "ERROR" -> Pill("status = ERROR", "bad"); else -> Pill("status = OFF", "line") }
            Pill("numpy · Chaquopy 3.11", "line")
        }
        if (ui.pyStatus == "ERROR") Banner("${ui.pyMessage}\nFallback: Kotlin-only. Fallback Python sonucu gibi gösterilmez.", "warn")
        Row(verticalAlignment = Alignment.CenterVertically) { T("Python meclisi açık", C.text, 13, modifier = Modifier.weight(1f)); Switch(checked = s.pythonEnabled, onCheckedChange = { s.pythonEnabled = it; Engine.applySettings() }) }
        if (ver < 0) T("", C.dim)
    }
    LrCard(title = "ÜYELER · 8", right = "Δ top-5 (pp)") {
        if (votes.isEmpty()) T(if (ui.pyStatus == "OFF") "Python kapalı ya da henüz Python’lu tahmin yok." else "Python’lu tahmin bekleniyor.", C.dim, 12)
        for ((j, v) in votes.withIndex()) {
            val c = ctx
            val d = if (c != null && c.oos.isNotEmpty() && c.oos[0].pyTop5Hit != null && j < (c.oos[0].pyTop5Hit?.size ?: 0)) (c.oos.count { it.pyTop5Hit?.get(j) == true }.toDouble() / c.oos.size - 5.0 / Wheel.N) * 100 else null
            MemberRow(v.title, P_DESC[v.id] ?: "", v.weight, d, v.top, v.weight < 0.03, if (j < lam.size) " · güven ${S.f((1 - lam[j]) * 100, 0)}%" else "")
        }
        T("güven = 1 − λ: her üye çevrimiçi öğrenilen bir kaybolma (shrinkage) katsayısıyla düzgün dağılıma çekilir; işe yaramayan üye aşırı güvenli olamaz.", C.dim2, 10)
    }
    LrCard(title = "SÖZLEŞME") {
        T("Giriş: geçmiş (≤ 4 000 spin penceresi + mutlak sıra), sektör tanımı. Çıkış: 8 üye × 37 olasılık, metadata. Python yalnızca geçmişi görür: dilimlenmiş görünüm, gelecek fiziksel olarak yoktur.", C.text, 12)
    }
    LrCard(title = "DOĞRULAMA") { T("Kotlin, Python sonucunu doğrulamadan kullanmaz: şema, 0–36 aralığı (37 değer), olasılık toplamı = 1, sonlu/negatif olmayan değerler. Geçersizse status = ERROR yazılır (LR-E-PY-001) ve Kotlin-only’ye düşülür.", C.text, 12) }
    LrButton("Python’u yeniden başlat", { confirm = true }, "ghost", true, Modifier.fillMaxWidth())
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        confirmButton = { TextButton(onClick = { confirm = false; Engine.restartPython() }) { Text("Yeniden başlat") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } },
        title = { Text("Python yeniden başlatılsın mı?") }, text = { Text("Kilitli tahmin etkilenmez. Bu sırada Kotlin-only fallback etiketi görünür; Python durumu yeniden kurulur.") })
}

@Composable
private fun TableCouncil(ui: EngineUi) {
    val ctx by rememberLabCtx()
    val p = ui.pred
    LrCard(title = "TABLE · güncel seçimler") {
        if (p == null) T("Kilitli tahmin yok.", C.dim, 12)
        else for (c in p.table) TableDistributionRow(c, dense = true)
        T("Her yüzde sınıf + 0 dağılımıdır. Frequency (decay), son 50 ve 1. derece geçiş birleşir; ağırlıklar yalnızca gerçek sonuç girildikten sonra güncellenir. Bu model tahminidir, garanti değildir.", C.dim2, 10)
    }
    val c = ctx
    if (c == null) Banner("Bağımsızlık testi için LAB taban koşusu gerekir (LAB sekmesi → Overview).", "info")
    else for (sec in LabManager.cachedSecs("side", "") { LabTabs.build(c, "side") }) SecView(sec)
}

@Composable
private fun RefereeTab() {
    val ctx by rememberLabCtx()
    val c = ctx
    LrCard(title = "HAKEM · Meta-Ensemble") { T("Kotlin ve Python meclislerinin karışımı validation üzerinde ölçülür; ağırlık seçimi test (OOS) setinde yapılmaz.", C.text, 12) }
    if (c == null) Banner("LAB taban koşusu henüz hazır değil. LAB sekmesini açıp “Hesapla” de.", "info") else for (sec in Referee.secs(c)) SecView(sec)
}

@Composable
private fun ChampionTab() {
    val ctx by rememberLabCtx()
    val versions by Engine.dao.modelVersionsFlow().collectAsState(initial = emptyList())
    var confirm by remember { mutableStateOf<String?>(null) }
    val s = Engine.settings; val champ = s.championVersion
    LrCard(title = "CHAMPION / CHALLENGER") {
        KV("Aktif Champion", champ, "gold")
        T("Her artımlı güncelleme sürümlüdür. Champion değişikliği yalnızca MANUEL onayla, kapılar geçilirse yapılır; en yüksek hit oranı otomatik seçilmez.", C.dim2, 10)
    }
    val c = ctx
    if (c == null) { Banner("Kapılar LAB taban koşusuna dayanır; LAB sekmesinden hesapla.", "info"); }
    else {
        val r = c.run.result; val rs = c.robust; val det = c.determinism
        val sens = c.suites?.sens
        val periodsOk = c.regime.periods.isNotEmpty() && c.regime.periods.count { it >= 0 } * 3 >= c.regime.periods.size * 2
        val gates = listOf(
            Triple("Minimum sample (OOS n ≥ 300)", r.n >= 300, "n = ${r.n}"),
            Triple("OOS değerlendirmesi", r.n > 0, "Δ ${S.sg(r.deltaPp)}"),
            Triple("Baseline üstü (CI alt sınırı > 0)", r.ciLo > 0, "[${S.f(r.ciLo)}; ${S.f(r.ciHi)}]"),
            Triple("Calibration (ECE ≤ 0,05)", r.ece <= 0.05, "ECE ${S.f(r.ece, 3)}"),
            Triple("Robustness (skor ≥ 60)", rs.score >= 60, "${rs.score}/100 · ${rs.cls}"),
            Triple("Leakage yok", r.leakFree, if (r.leakFree) "kapılar temiz" else "LEAKAGE"),
            Triple("Tekrarlanabilir (determinizm)", det != null && det.first == det.second, if (det == null) "çalıştırılmadı" else if (det.first == det.second) "AYNI" else "FARKLI"),
            Triple("Dönem ve parametre stabilitesi", periodsOk && sens != null && sens.spike == null, if (sens == null) "hassasiyet taraması yok" else if (sens.spike != null) "tek nokta" else "dönemler ${c.regime.periods.count { it >= 0 }}/${c.regime.periods.size}"),
            Triple("Kabul edilebilir overfit (val − OOS ≤ 3 pp)", r.valDeltaPp - r.deltaPp <= 3.0, "val ${S.sg(r.valDeltaPp)} → OOS ${S.sg(r.deltaPp)}")
        )
        LrCard(title = "KAPI LİSTESİ") {
            for (g in gates) KV((if (g.second) "✓ " else "✗ ") + g.first, g.third, if (g.second) "ok" else "bad")
        }
        val all = gates.all { it.second }
        Banner(if (all) "Tüm kapılar geçti. Geçiş yine de manuel onay ister." else "Kapılardan biri kaldı → “Champion yap” pasif: " + gates.filter { !it.second }.joinToString(", ") { it.first }, if (all) "ok" else "warn")
        LrButton("Champion yap (manuel onay)", { confirm = "new" }, "gold", all, Modifier.fillMaxWidth())
    }
    LrCard(title = "SÜRÜM GEÇMİŞİ") {
        KV("v1.0.0 · başlangıç", if (champ == "v1.0.0") "Champion" else "", if (champ == "v1.0.0") "gold" else "")
        for (v in versions) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T(v.version + " · " + v.note, C.text, 12, modifier = Modifier.weight(1f))
            if (v.version == champ) Pill("Champion", "gold") else LrButton("Geri dön", { confirm = "back:" + v.version }, "ghost")
        }
        if (champ != "v1.0.0") LrButton("v1.0.0’a geri dön", { confirm = "back:v1.0.0" }, "ghost", true, Modifier.fillMaxWidth())
    }
    val cf = confirm
    if (cf != null) AlertDialog(onDismissRequest = { confirm = null },
        confirmButton = { TextButton(onClick = {
            confirm = null
            Engine.scope.launch {
                val dao = Engine.dao
                if (cf == "new") {
                    val n = dao.modelVersions().size + 1; val ver = "v1.$n.0"
                    dao.clearChampion(); dao.insertModelVersion(ModelVersionE(0, ver, System.currentTimeMillis(), true, "", "manuel onay · LAB kapıları geçti")); s.championVersion = ver
                } else { val ver = cf.removePrefix("back:"); dao.clearChampion(); dao.modelVersions().firstOrNull { it.version == ver }?.let { dao.updateModelVersion(it.copy(champion = true)) }; s.championVersion = ver }
                Engine.applySettings()
            }
        }) { Text("Onayla") } },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("Vazgeç") } },
        title = { Text("Champion değişsin mi?") }, text = { Text("Yeni tahminler bu sürüm etiketiyle kilitlenir. Önceki kilitli tahminler değişmez. Bu işlem kaydedilir.") })
}
