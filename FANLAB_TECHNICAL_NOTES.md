# FanSuper v1.2 — FAN LAB teknik notları

## Bu sürümde eklenenler

- Kronolojik, leakage-free tahmin kayıtları: her StepLog tahmin olasılıklarını gerçek sonuç öğrenilmeden önce saklar.
- Walk-forward raporlama: motorun gerçek zamanlı öğrenme sırasındaki tahmin serisini ölçer.
- Top-1 / Top-2 ölçümleri ve %25 random baseline.
- Bootstrap %95 güven aralığı.
- Permutation testi ve percentile ölçümü.
- Log loss, Brier score, entropy ve calibration gap.
- Ensemble üyesi disagreement oranı.
- Üye olasılıkları arasında ortalama Jensen-Shannon diversity.
- Son 100 tahmin için performans ve log-loss.
- FAN LAB ekranı ve yeniden çalıştırma düğmesi.
- Uygulama sürümü 1.2 olarak yükseltildi.

## Mevcut mimari korunmuştur

Kotlin Council, Python Council/Chaquopy, Referee, Discovery, Charts, Overlay, DataStore ve Undo akışları kaldırılmadı. FAN LAB mevcut motorun oluşturduğu kronolojik kayıtları analiz eder.

## Doğrulama

- Python undo/state testleri: **7/7 OK**.
- Analytics çekirdeği, Android bağımlılıklarından ayrılmış olarak `kotlinc` ile derlendi ve örnek veri üzerinde çalıştırıldı.
- Bu ortamda Gradle dağıtımı internetten indirilemediği için tam Android APK build'i burada doğrulanamadı. Code On The Go / Android Studio Gradle wrapper üzerinden ilk build alınmalıdır.

## Yorum

FAN LAB istatistikleri gelecekteki bir veri kaynağında avantaj garantisi değildir. Özellikle veri gerçekten bağımsız ve adil rastgele ise modellerin geçmişteki küçük örneklerde yüksek görünmesi gerçek bir öngörü gücü anlamına gelmeyebilir.


## v1.3 — Teknik notlar

### Alfabe ve problem ayrımı
Gerçek veri 1..4 rakamlarından oluşur (N=4). BS: 0=KÜÇÜK (1,2), 1=BÜYÜK (3,4). OE: 0=ÇİFT, 1=TEK. Birleşik: bs*2+oe.
Rakam ve yan ayrı problemlerdir; yan modeller ayrı BS/OE/COMB dizilerinden öğrenir, rakam tahmininden türetilmez.

### Akış
`predict → LOCK → gerçek sonuç → değerlendir → online öğren → rejim/kalibrasyon/ağırlık → persist → yeni predict+LOCK`.
Kilit açıkken ikinci tahmin üretilmez. DEL: önceki anlık görüntüye dönülür; aynı kilitli tahmin geri gelir.

### Meta-ensemble
Her eksen (NUMBER, BS, OE, COMB) için ayrı `AxisEnsemble`. Üye skoru: log-skor kazancı (uzun α=0.01 / kısa α=0.06 EWMA) + rejim kazancı
(n/(n+20) büzülmeli) − kalibrasyon cezası; örnek ve korelasyon çarpanları; ağırlık yumuşatma 0.10; alt sınır 0.15/n.
Lineer olasılık havuzu + öğrenilen sıcaklık τ∈[0.5,1.5] (argmax'ı değiştirmez). Yan: BS ve OE marjinalleri ile birleşik model,
ürün/doğrudan uzmanları arasında Hedge ile geometrik harmanlanır; tek BS + tek OE seçilir.

### Rejim
Çevrimiçi 3 merkezli k-means (flip oranı, dengesizlik; pencere 30), histerezis; REGIME_A/B/C; rejim bazlı doğruluk gerçek sonuçlardan.

### Kalıcılık
Her bileşenin kendi blob'u: ModelState (memory, side, stats, lock, undo.N), RegimeState, EnsembleState (eksen başına), CalibrationState,
ModelPerformance (eksen:model). Açılış: durum uyuşuyorsa devam, daha kısaysa kuyruk yakalama, değilse tam replay.
Python: pickle (`py_state.pkl`, `py_side_state.pkl`) ve önek yüklemesi.

### Replay / sızıntı
`ReplayEngine` her adımda geçmişi `copyOf(i)` ile fiziksel keser; kontrol: sıra, lastKnownRecordId, predictionTimestamp ≤ actualTimestamp.
Hata kodları: FAN-E-LEAK-001, LOCK-001/002/003, SEQ-001, STATE-001/002, PY-001, DB-001, REPLAY-001, REC-001.

### Dürüstlük notu
Veri neredeyse rastgeledir (yan doğruluk ≈ %50). Bu sistem bir avantaj garantisi vermez; Replay sekmesindeki bootstrap/permutation
sonuçları yalnızca şanstan ayırt edilebilirlik için tanıdır.
