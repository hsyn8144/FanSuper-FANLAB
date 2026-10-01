# Sürüm notları

## 1.1 — 2026-09-27

- Onaylanan eski mavi overlay düzeni: Kotlin rakam, Python rakam, Kotlin yan ve Python yan.
- Kalıp ve hakem/meclis detayları overlay'den kaldırıldı; uygulama içindeki modeller değişmedi.
- Son 6 veri en yenisi solda; hızlı girişlerde işlenmiş kayıtların tekrar gösterilmesi önlendi.
- Dikey kartta DEL, 4, 3, 2, 1 tam genişlikli ve alt alta; yatay ayarı korunuyor.
- Tahmin alanı/son sayılar/kenarlardan sürükleme; yeniden ölçüm sonrası ekran içine sığdırma.
- Tahmin kaynağı, Python yokluğu, veri sırası, dokunma ve yerleşim regresyon testleri eklendi.
- versionCode 2; sürümlü APK, kaynak ZIP ve SHA-256 dosyaları workflow tarafından üretilir.


## 1.2
- FAN LAB research screen added.
- Chronological walk-forward reporting from prediction logs.
- Bootstrap confidence interval for Top-1 accuracy.
- Permutation benchmark against shuffled outcomes.
- Log loss, Brier score, entropy and calibration-gap metrics.
- Ensemble member disagreement and Jensen-Shannon diversity metrics.
- Recent-window performance and research rerun action.
- Existing prediction, council, discovery, chart, overlay and undo flows preserved.


## 1.3 — 2026-10-01
Araştırma / öğrenme / replay / ensemble sistemi. v1.2'de çalışan hiçbir özellik kaldırılmadı.

**Tek nihai tahmin**
- Kotlin + Python (rakam ve yan) çıktıları ortak olasılık dağılımı biçiminde olasılık seviyesinde birleştirilir (probability-level meta-ensemble).
  Ağırlıklar: performans (log-skor kazancı, uzun/kısa EWMA), kalibrasyon, rejim, örnek sayısı, model çeşitliliği (korelasyon/uyum/JSD).
- `FinalPrediction`: tek rakam + tek yan (1 BÜYÜK/KÜÇÜK + 1 TEK/ÇİFT), dağılımlar, güven, entropi, rejim, ensemble sürümü, zaman damgası, kilit kimliği.
- Yan tahmin rakamdan türetilmez: BS ve OE için ayrı Kotlin motorları (frekans, son pencere, Markov, seri, rejim koşullu, kalıp) +
  Python yan meclisi (**Kalıp Arama 2.0 dahil — her iki eksende**, n-gram, lojistik) + birleşik 4'lü analiz.
- Rakam alfabesi 1..4 (N=4); rastgele baseline rakam %25, yan %50, birleşik %25.

**Kalıcı hafıza (Room + SQLite)**
- Tablolar: FanRecord, PredictionRecord, ModelState, ModelPerformance, RegimeState, CalibrationState, EnsembleState, ReplayState, PredictionExplanation.
- CSV artık ana veritabanı değildir (ilk kurulum aktarımı / dışa aktarım / yedek). v1.2 kullanıcılarının CSV'si ilk açılışta DB'ye aktarılır.
- Açılışta yalnızca yeni kayıtlar işlenir (durum parçaları + Python önek yüklemesi). Tam replay yalnızca ilk kurulum, bozuk/uyuşmayan durum
  veya elle "Full Replay" durumunda.
- Her kayıtta DB baştan yazılmaz: yalnızca değişen durum parçaları yazılır; son 8 geri alma anlık görüntüsü DB'de.

**Replay, sızıntı koruması, tekrarlanabilirlik**
- Kronolojik replay: geçmiş → tahmin → LOCK → gerçek sonuç → değerlendirme → öğrenme. Gelecek veri fiziksel olarak erişilemez (önek kopyaları).
- Kilit: predictionTimestamp, predictionSequence, lastKnownRecordId, kilit özeti (CRC); yeniden başlatmada kilit yeniden üretilip doğrulanır.
- LIVE ve RESEARCH/REPLAY modları ayrı (sandbox brain + Python sandbox_replay).
- Bootstrap güven aralıkları, permutation testleri, baseline'lar, karşı-olgusal (Python yok / Kotlin yok / ağırlık ±%10 / eşit ağırlık; gerçek state'e yazmaz).

**Açıklamalar**: yalnızca gerçek hesaplardan (grup favorileri, rejim isabeti, kalibrasyon, geçiş matrisi, seri, entropi, model anlaşmazlığı).

**Arayüz**: overlay tasarımı korunarak 4 satır → 2 satır (RAKAM + YAN, örn. `BÜYÜK+TEK`); Kotlin/Python ayrımı FAN LAB'a taşındı.
FAN LAB 8 sekme: Overview (v1.2 araştırma içeriği + sayfalı tahmin geçmişi), Models, Side Analysis, Replay, Calibration, Diversity, Regime, Counterfactual.

**Testler**: v13 çekirdek (sızıntı önek-değişmezliği, replay sırası, tekrarlanabilirlik, kilit, kalıcılık, artımlı = tam replay, undo, yan iki bileşen,
rakam aralığı, tek dağılım, karşı-olgusal), Room (Robolectric), Python yan meclis testleri. versionCode 4.
