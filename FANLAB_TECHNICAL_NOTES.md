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
