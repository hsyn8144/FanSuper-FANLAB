package fan.superai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fan.superai.EngineHost

@Composable
fun ResearchScreen() {
    val r by EngineHost.research.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        FCard("🧪 FAN LAB · Model doğrulama") {
            Text(r?.status ?: "Analiz hazırlanıyor…", color=C.orange, fontSize=18.sp, fontWeight=FontWeight.Bold)
            Muted("Bu ekran mevcut motorun kronolojik tahmin kayıtlarını kullanır. Gelecek bilgi geçmiş tahmine geri sızdırılmaz.", Modifier.padding(top=5.dp))
        }
        r?.let { x ->
            FCard("Walk-forward sonuçları") {
                KV("Değerlendirilen tahmin", "${x.n}")
                KV("Top-1", pct(x.walkForwardTop1), if(x.walkForwardTop1>=0.25) C.lightGreen else C.danger)
                KV("Top-2", pct(x.walkForwardTop2), if(x.walkForwardTop2>=0.50) C.lightGreen else C.danger)
                KV("Random baseline", pct(x.randomBaseline), C.muted)
                KV("Bootstrap %95 aralık", "${pct(x.bootstrapLo)} – ${pct(x.bootstrapHi)}")
            }
            FCard("Olasılık kalitesi") {
                KV("Log loss", "%.4f".format(x.logLoss))
                KV("Brier score", "%.4f".format(x.brier))
                KV("Ortalama entropy", "%.4f".format(x.entropy))
                KV("Kalibrasyon farkı", "%.4f".format(x.calibrationGap))
                KV("Son 100 Top-1", pct(x.recentTop1))
                KV("Son 100 log loss", "%.4f".format(x.recentLogLoss))
            }
            FCard("Model çeşitliliği") {
                KV("Üyeler farklı tahmin verdi", pct(x.memberDisagreement))
                KV("Ortalama JS diversity", "%.4f".format(x.memberDiversity))
                Muted("Yüksek çeşitlilik modellerin aynı bilgiyi tekrar etmediğini; çok düşük değerler ise güçlü korelasyonu gösterir.", Modifier.padding(top=4.dp))
            }
            FCard("Permutation testi") {
                KV("p-değeri", "%.4f".format(x.permutationP))
                KV("Percentile", pct(x.permutationPercentile))
                Muted("Gerçek sonuç sırası rastgele karıştırılarak aynı tahmin dizisi üzerinde karşılaştırma yapılır.", Modifier.padding(top=4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                NumButton("↻ FAN LAB'ı yeniden çalıştır", C.blue, 13) { EngineHost.rerunResearch() }
            }
        } ?: Muted("Henüz yeterli kayıt yok.")
    }
}
