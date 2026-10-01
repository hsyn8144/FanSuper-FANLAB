# FAN SUPER v1.1

Tek uygulamada iki meclis: **🔵 Kotlin Meclisi** (8 üye) ve **🐍 Python Meclisi** (10 üye, Chaquopy ile uygulamaya gömülü).
İki meclis kendi içinde **Fixed-Share Hedge** ile yarışır; **⚖️ Baş Hakem** (stacking + kalibrasyon + konformal tek/çift kararı)
uygulama içinde tek bir **rakam tahmini** ve tek bir **yan tahmin** (T/Ç • K/B) üretir.
Overlay ise v9.6 mavi kartının **Kalıp satırı çıkarılmış** halidir; Kotlin ve Python tahminlerini ayrı gösterir.

- Sürüm: **1.1** (`versionCode = 2`)
- Paket: `fan.superai` (`super` Java/Kotlin'de ayrılmış kelime olduğu için `fan.super.ai` kullanılamaz)
- Hazır veri: `app/src/main/assets/fan_data_live.csv` (893 kayıt) ilk açılışta yüklenir.

## Meclisler
| 🔵 Kotlin | 🐍 Python |
|---|---|
| Kalıp Arama 2.0 (6 görünüm × 30 uzunluk, hash indeksli) | LSTM (BPTT) |
| CTW (Context Tree Weighting) | Mini Transformer (dikkat / induction head) |
| PPM-C | 1D-CNN |
| Frekans / Aralık (hazard) | Kalıp 2.0 bulanık (1 fark + ayna) |
| Seri / Dalga | kNN-DTW |
| Rejim | Spektral + BOCPD |
| GRU (BPTT) | Gradient Boosting |
| ESN | Bağlam modeli · Motif keşfi · HMM |

## Ekranlar
Ana · Meclisler (Kotlin / Python / Yan) · 🔍 Keşif Laboratuvarı · Grafik · Ayarlar

## Derleme
GitHub Actions (`.github/workflows/build.yml`) her push'ta APK üretir; main dalında Releases altına da koyar.
Yerelde: JDK 17 + Python 3.11 kurulu iken `./gradlew assembleDebug`.

## Overlay kullanımı ve testler
Varsayılan dikey kartın sırası:
1. **🔵 K** — Kotlin rakam tahmini
2. **🐍 Py** — Python rakam tahmini
3. **🔵 Yan** — Kotlin yan tahmini (T/Ç • K/B)
4. **🐍 PyYan** — Python yan tahmini (T/Ç • K/B)
5. **Son 6 veri**, en yeni veri **solda**
6. **DEL → 4 → 3 → 2 → 1**, tam genişlikte ve **alt alta**

Kalıp ve hakem/meclis detayları overlay'de gösterilmez; uygulamanın içindeki
meclisler, modeller, veriler, öğrenme ve geri alma işlemleri değişmemiştir.
Tahmin satırlarından, son sayılardan veya kartın boş kenarlarından tutup sürükleyin.
Sürükleme için uzun basmayı beklemek gerekmez; sayı/DEL düğmeleri veri girişi içindir.
Yatay yerleşim ayarı korunur: dört tahmin satırı solda, düğmeler sağdadır.
Saydamlık, yazı boyutu, son sayı dizisini gösterme ve titreşim ayarları korunur.

**Tahmin kaynağı:** K/Py rakamları doğrudan ilgili meclisin olasılık dağılımındaki
ilk iki adaydır ("Her zaman tek" seçilirse yalnızca ilk aday). Otomatik konformal
tek/çift kararı ve kalibrasyon hakeme ait olduğu için bunlar meclis satırlarına
kopyalanmaz; overlay yüzdeleri ilgili adayların ham olasılık toplamıdır. Yan tahmini
her meclisin kendi dağılımından türetilir; yan yüzdesi tek/çift ve küçük/büyük
güvenlerinin ortalamasıdır. Python hazır/aktif değilse iki Python satırı `--` gösterir;
Kotlin veya hakem sonucu Python tahmini gibi gösterilmez.

Son veriler yalnızca gösterimde ters çevrilir, motor geçmişinin sırası değişmez.
Bekleyen girişlerin motor tarafından işlenmiş bölümü tekrar eklenmez; böylece motor
hesap yaparken son 6 satırında aynı giriş iki kez görünmez.

Overlay dokunma regresyon testleri: `./gradlew testDebugUnitTest`.
GitHub Actions bu testleri APK derlemesiyle birlikte çalıştırır ve test raporlarını
`overlay-test-reports`, kurulabilir APK'yı `FAN_SUPER_APK`, tüm kaynak kodları
`FanSuper_v1.1.zip` olarak `FanSuper_SOURCE` artifact'ında saklar (main'de ayrıca Release'e konur).

## Veri girişi düğmeleri ve hızlı geri alma (⌫ / DEL)
- **Anında geri bildirim:** Sayı düğmesine basıldığı anda rakam "Son 6" satırında görünür;
  motor hesabı arka planda sırayla işlenir. Motor henüz hazır değilse (ilk açılış) girişler
  kuyruğa alınır, hiçbir basış kaybolmaz. Kaç işlemin sırada olduğu Ana ekranda gösterilir.
- **Düğmeler her zaman ekran içinde:** Kart artık ekran genişliğini aşamaz; yatay görünümde
  tahmin satırı daralır, sürükleyince kart ekran dışına çıkarılamaz. Böylece 1/2/3/4 ve DEL
  düğmeleri ulaşılabilir kalır.
- **O(1) geri alma:** Her adım için küçük bir geri alma kaydı tutulur (Kotlin meclisi + hakem
  + Python meclisi). ⌫ artık tüm geçmişi baştan öğrenmez, milisaniyeler içinde biter ve
  veriyi birebir eski haline döndürür. Python tarafında adım başına derin kopya (deepcopy)
  kaldırıldı; bunun yerine referans tabanlı görüntü + dayanak (checkpoint) kullanılır,
  durum dosyasıyla birlikte saklanır (uygulama yeniden açıldığında ilk geri alma da hızlıdır).
- Kayıt yığınının dışında (64+ adım derin) geri alma yapılırsa motor doğru sonucu vermek için
  tam yeniden kurmaya düşer; bu normaldir ve logcat'te `undo ... (tam yeniden kurma gerekti)`
  olarak görünür.

Python geri alma testleri (yerelde): `pip install "numpy<2"` sonra
`python app/src/test/python/test_fan_super.py -v`.


### v1.2 FAN LAB
The project now includes a research/validation layer that reports chronological walk-forward performance, bootstrap confidence intervals, permutation benchmarks, log loss, Brier score, entropy, calibration gap, member disagreement and Jensen-Shannon diversity. These metrics are diagnostic; they do not guarantee predictive advantage on a future random process.


### v1.3 — meta-ensemble, kalıcı hafıza, replay
- Tek nihai tahmin: tek rakam + tek yan (BÜYÜK/KÜÇÜK + TEK/ÇİFT). Overlay'de iki satır (RAKAM, YAN); Kotlin/Python ayrıntısı FAN LAB'da.
- Room/SQLite kalıcı hafıza; açılışta yalnızca yeni kayıtlar işlenir; "Full Replay" yalnızca ilk kurulum, durum bozulması ya da elle.
- Yan tahmin rakamdan türetilmez; Python Kalıp Arama 2.0 hem Büyük/Küçük hem Tek/Çift eksenine girer.
- Replay (RESEARCH modu) sandbox'ta çalışır; sızıntı denetimi, bootstrap/permutation, baseline ve karşı-olgusal analiz içerir.
- Ayrıntılı teknik notlar: `FANLAB_TECHNICAL_NOTES.md` (v1.3 bölümü). Hata kodları `FAN-E-*`, olay günlüğü `NEW_RECORD … REPLAY_COMPLETED`.
- Python testleri: `pip install "numpy<2"` ardından `python app/src/test/python/test_fan_super.py -v` ve `test_fan_side.py -v`.
