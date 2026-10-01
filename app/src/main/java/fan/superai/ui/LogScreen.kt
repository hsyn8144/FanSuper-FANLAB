package fan.superai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fan.superai.ActivityLogEntry
import fan.superai.EngineHost
import fan.superai.engine.MemberStat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

/**
 * "Ayarlar › Loglar" ile açılır. Her EKLE/GERİ AL işlemini, o anki hakem kararını
 * ve tıklanınca da o adımda HER ÜYE MODELİN (🔵 Kotlin + 🐍 Python) ürettiği
 * tahmin/ağırlığı gösterir. Ekstra hesap yapmaz — motor bu veriyi zaten her
 * adımda üretiyor, burada sadece geçmişi saklanmış hâli listeleniyor.
 */
@Composable
fun LogScreen(onBack: () -> Unit) {
    val log by EngineHost.activityLog.collectAsState()

    Column(Modifier.fillMaxSize().background(C.bg)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Geri", color = C.lightBlue) }
            Text("İşlem Logları (${log.size})", color = C.text, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
            Box(Modifier.fillMaxWidth().padding(start = 8.dp)) {
                TextButton(onClick = { EngineHost.clearActivityLog() }) { Text("Temizle", color = C.danger) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        if (log.isEmpty()) {
            Muted("Henüz kayıt yok. Sayı girdikçe veya geri aldıkça burada birikecek.", Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(log.asReversed()) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
private fun LogRow(e: ActivityLogEntry) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }
                .background(C.card).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text(timeFmt.format(Date(e.time)), color = C.dim, fontSize = 11.sp)
                    Text(e.action, color = C.lightBlue, fontSize = 12.sp)
                }
                Text(
                    "Karar: ${e.verdictLabel ?: "--"}" + (e.confidencePct?.let { "  ·  güven %$it" } ?: ""),
                    color = C.text, fontSize = 13.sp
                )
                Text(if (open) "▲ üye modelleri gizle" else "▼ üye modellerini göster", color = C.muted, fontSize = 10.sp)
            }
        }
        if (open) {
            Column(Modifier.fillMaxWidth().background(C.card).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("🔵 Kotlin Meclisi", color = C.head, fontSize = 11.sp)
                e.kotlinStats.forEach { MemberStatRow(it) }
                Text("🐍 Python Meclisi", color = C.head, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                if (e.pythonStats.isEmpty()) Muted("kapalı / veri yok") else e.pythonStats.forEach { MemberStatRow(it) }
            }
        }
    }
}

@Composable
private fun MemberStatRow(m: MemberStat) {
    val status = when {
        !m.enabled -> "kapalı"
        m.benched -> "yedekte"
        else -> "aktif"
    }
    Text(
        "  ${m.name}: top1 %${(m.top1 * 100).toInt()} · top2 %${(m.top2 * 100).toInt()} · ağırlık %${(m.weight * 100).toInt()} · $status",
        color = C.text, fontSize = 11.sp
    )
}
