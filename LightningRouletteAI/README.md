# ⚡ Lightning Roulette AI

0–36 **Avrupa ruleti** verisini araştıran ve sonraki spin için **kilitli tahmin** üreten Android uygulaması (Kotlin + Jetpack Compose + Room + gömülü Python/numpy meclisi).
FanSuper v1.4 FanLab mantığının (Kotlin + Python meclisleri, Hakem/meta-ensemble, Room hafıza, replay/sızıntı denetimi, LAB, yüzer overlay, hata kodları) ruletin 37 sonuçlu dünyasına taşınmış, **FanSuper’dan bağımsız** halidir.
Spesifikasyon: [`Lightning_Roulette_AI_TAM_KAPSAMLI_PROMPT_V3.txt`](../Lightning_Roulette_AI_TAM_KAPSAMLI_PROMPT_V3.txt) · Ekran tasarımları: [`mockups/`](mockups/README.md) (69 sayfa).

> ## Hiçbir sonuç garanti değildir
> Sistem **geleceği bildiğini iddia etmez.** Akış her zaman: **tahmin → 🔒 LOCK → gerçek sonuç → değerlendirme → öğrenme.**
> Tahmin yalnızca geçmişten üretilir, sonuç girilmeden kilitlenir ve sonradan değiştirilemez; öğrenme yalnızca sonuç açıldıktan sonra yapılır.
> Rastgele ve bağımsız bir veriden kalıcı avantaj çıkması zorunlu değildir; uygulama bunu gizlemez, hipotezleri ciddi biçimde (walk-forward, OOS koruması, çoklu test düzeltmesi, başarısız hipotez hafızası, robustness ve leakage kapıları) araştırır.
> “Yüksek hit oranı tek başına avantaj kanıtı değildir.”

## APK

* **GitHub Actions** her push’ta derler: artifact **`LIGHTNING_ROULETTE_AI_APK`**.
* Commit mesajında **`[release]`** varsa (ya da `main`’e birleşince) **GitHub Release** oluşur; APK doğrudan indirilebilir.
* Release imzası debug anahtarıdır (Play Store için değil, doğrudan kurulum içindir).

## Özellikler (ekran haritası: `mockups/README.md`)

| Sekme | İçerik |
|---|---|
| **Ana** | durum çipleri · yaşam döngüsü şeridi · 🔒 kilit kartı · **NEXT** (3–5 aday, merkez ve komşu aralığı yüzdeleri; `29-k2` = merkez ±2 cep) · **TABLE** (renk, tek/çift, 1–18/19–36, üç düzine, üç sütun ve her satırda ayrı 0 olasılığı) · canlı giriş (3×4 klavye, DEL×2 = son spini sil, çift ENTER yoksayılır, “07 → 7”, 37+ kırmızı) · sonuç değerlendirmesi · son OOS özeti (Exact ≠ Candidate, taban, fark, %95 CI, sınıf A–F/O/L/S) · tahmin detayı (37 sonucun tam olasılık sıralaması, çark, geometri, skor katkıları, meclis uyuşmazlığı) |
| **Meclisler** | Kotlin (8 üye) · Python (8 üye + güven λ, durum OK/ERROR/OFF, Kotlin-only fallback) · Table · **Hakem** (20/80…80/20 validation testi, OOS kilidi, konformal küme) · **Champion/Challenger** (9 kapı, manuel onay, sürüm geçmişi) |
| **Wheel** | fiziksel çark (aday halkaları, sektör çizgileri, bölgeler, SON 8, dokunarak cep seçimi, k/yön) · masa düzeni · sektör/bölge editörü |
| **LAB** | **16 sekme**: Overview · Models · Side/Table · Wheel · Sectors · Neighbors · Patterns · Transitions · Replay · Calibration · Diversity · Regime · Counterfactual · Ablation · Robustness (skor/hassasiyet/stres/hata matrisi) · Experiments; deney oluşturucu, kalıcı kuyruk (checkpoint/resume), rapor, hipotez üretici + **silinemez** başarısız hipotez hafızası |
| **Veri** | spin listesi (süzgeç, arama, seçim, **silme etkisi onayı**, düzenleme) · CSV/TXT/JSON içe aktarma sihirbazı (TOTAL/VALID/INVALID/DUPLICATE/NEW, overlap, `SYNTHETIC_IMPORT`) · dataset sürümleri · dışa aktar/yedek/geri yükle (`.lrexport`, round-trip testi) |
| **Ayarlar** | Tahmin · Overlay · Python/LAB · Veri ve güvenlik · İşlem logları + `LR-E-*` sözlüğü · **Tanılama (20 self-test)** · Hakkında; ayar satırının tamamı dokunulabilir, seçenekler dar ekranda yatay kaydırılır |
| **Overlay** | 5 mod (dikey · yatay · kompakt · metin · simge), 190 dp kart, tüm masa bahis sınıfları + 0 yüzdesi, NEXT merkez/aralık yüzdeleri, sürükleme/kenara yapışma, aynı klavye/doğrulama, ön plan bildirimi (Gizle/Kapat) |

## Tahmin yüzdeleri ve kilit davranışı

TABLE kartı ve overlay artık yalnızca en yüksek seçimi değil, her kategorinin tüm alternatiflerini gösterir: Kırmızı/Siyah, Çift/Tek, 1–18/19–36 (18 alt gruptadır), 1–12/13–24/25–36, Sütun 1/2/3 ve her kategori için ayrı 0 olasılığı. Bu olasılıklar 37 Avrupa ruleti cebi üzerinden hesaplanır. Teorik tabanlar: ikili dış bahisler 18/37 (%48,6), düzine/sütun 12/37 (%32,4), 0 ise 1/37 (%2,7).

Table tahmincileri azalan ağırlıklı frekans, son 50 spin frekansı ve birinci derece geçiştir. Ağırlıklar gerçek sonuç girildikten sonra Fixed-Share Hedge ile güncellenir; gösterilen dağılım %60 model karışımı + %40 teorik tabandır. Bu **model tahminidir**; kalibre başarı, kazanma olasılığı veya garanti değildir. NEXT adayları her kilitteki güncel 37 cep dağılımından sıralanır; farklı geçmişler farklı olasılık ve öneri üretebilir. Tam eşit olasılıkta sıralama tekrarlanabilirlik için cep numarasına göre yapılır; bu sıralama bir tahmin avantajı değildir ve yapay rastgelelikle gizlenmez. NEXT için tek cep P'si ile k-komşu aralığının toplam P'si ayrı gösterilir. Tahmin kilidi sonuç girilene kadar sabittir; ayar değişikliği mevcut kilidi geriye dönük değiştirmez, sonraki kilitte uygulanır. Tahmin detayında 37 cebin tamamı sıralanır.

## Mimari

```
LightningRouletteAI/app/src/main
├── java/fan/lightningroulette
│   ├── core/      SAF KOTLİN çekirdek (Android bağımsız; yerelde test edilir)
│   │     Wheel · Model · Members (8 üye + Hedge) · Brain (kalibrasyon, adaylar, Table) · Replay (kesim, sızıntı kapıları, resume)
│   │     Session (LOCK/ENTER/undo/tail catch-up) · Stats · Importer/Exporter/SampleData
│   │     LabRun/LabSuites/LabTabs/LabDsl/LabReport/Referee (LAB analizleri, 16 sekme verisi)
│   ├── data/      Room: datasets · spins · predictions(unique) · evaluations · experiments · state_blobs · logs · model_versions · import_batches
│   ├── engine/    Engine (tek iş parçacığı, StateFlow) · PyBridge (Chaquopy) · DataOps · SelfTest · Settings
│   ├── lab/       LabManager (ayrı düşük öncelikli iş parçacığı, kuyruk, checkpoint) · LabService (ön plan bildirimi)
│   ├── overlay/   OverlayService · OverlayView (View tabanlı, WindowManager)
│   └── ui/        Compose ekranları · SecView (LAB için genel çizici) · WheelView
├── python/lr_council.py      8 üye numpy meclisi (LSTM-BPTT, Mini Transformer, 1D-CNN, Gradient Boosting, HMM, kNN-DTW, Bağlam, Motif)
└── assets/sample_2000.csv    2 000 sentetik spin (sabit tohum; sınıf C beklenir)
```

**Tasarım güvenceleri**

* **Fiziksel kesim (PAST | CUT | FUTURE):** replay’de ve Python’da üyeler yalnızca `values[:i]` dilimini görür; gelecek indekslenemez (IndexError). Testler gelecek değerleri değiştirip geçmiş tahminlerin birebir aynı kaldığını doğrular.
* **Kilit atomiktir:** `predictions(datasetId, refCount)` UNIQUE; ikinci tahmin üretilemez (`LR-E-LOCK-002`). ENTER tek transaction: kayıt → değerlendirme → öğrenme → yeni kilit; hata olursa bellek ve veritabanı geri alınır.
* **Exact ve Candidate ayrı** raporlanır; tüm tabanlar boyut/kapsama düzeltmelidir (komşu, sektör, bölge). Çoklu test için Bonferroni; permütasyon testleri iki aşamalıdır.
* **Python doğrulaması:** şema, 0–36 (37 değer), olasılık toplamı; geçersizse `status=ERROR` + açık **Kotlin-only fallback** (Python sonucu gibi gösterilmez). Her Python üyesi çevrimiçi öğrenilen λ ile düzgün dağılıma çekilir (aşırı güven yok).
* **Çevrimdışı:** manifestte `INTERNET` izni yoktur.

## Hata kodları (`LR-E-*`)

`LEAK-001` sızıntı · `LOCK-001/002/003` kilit · `SEQ-001` kayıt sırası · `STATE-001/002` model durumu · `PY-001` Python · `DB-001` veritabanı · `REPLAY-001` replay · `REC-001` geçersiz kayıt · `IMP-001` içe aktarma · `EXP-001` deney/dışa aktarma · `REPRO-001` tekrarlanabilirlik (FanSuper’daki `FAN-E-*` karşılıkları + yeni IMP/EXP/REPRO).

## Derleme ve test

```bash
# Android (CI ile aynı): JDK 17 + Python 3.11 gerekir (Chaquopy numpy kurar)
cd LightningRouletteAI && ./gradlew testDebugUnitTest assembleRelease     # APK: app/build/outputs/apk/release/

# Yerel hızlı döngü (Android SDK gerekmez): saf çekirdek + 42 Kotlin testi (Kotlin 1.9.22 / JDK 17 ile CI’daki dil sürümü)
tools/kc.sh          # çekirdeği derler
tools/kc.sh test     # çekirdeği ve testleri AYRI modül olarak derler + çalıştırır (smart-cast kısıtları Gradle’daki gibi)
tools/kc.sh gensample   # app/src/main/assets/sample_2000.csv'yi yeniden üretir (test: asset = üretici çıktısı)

# Python meclisi (13 test: sızıntı yok, canlı = replay, determinizm, geri alma, durum kaydı, rastgele veride aşırı güven yok)
pip install "numpy<2" && python -m unittest -v python_tests.test_council
```

* **Birim testleri (JVM):** `CoreTest`, `ResumeTest`, `ImportTest`, `LabTest`, `SessionTest` (çekirdek, Session, LAB, içe/dışa aktarma, örnek veri).
* **Uçtan uca (emülatör, CI `e2e` işi):** `JourneyTest` sıfır kurulumdan başlayıp ilk kurulum akışını, örnek veriyi, ENTER/geri al, tüm sekmeleri, LAB taban koşusu + 16 sekmeyi, tanılamayı ve overlay servisini gezer.
* **Uygulama içi tanılama:** Ayarlar › Tanılama (veri bütünlüğü, canlı giriş, sızıntı/replay, kalıcılık, istatistik, aktarım, model kuralları).

## Varsayımlar ve dürüst sınırlar

* “Kotlin ML” üyesi softmax regresyondur (görsellerdeki “GRU · ESN” adı bir tasarım önizlemesiydi); Python meclisi 8 üyeyi numpy ile (el yazımı BPTT/dikkat/CNN/boosting/HMM/kNN-DTW/bağlam/motif) uygular. Üyelerin ağırlıkları Fixed-Share Hedge ile, sınırlı adımlarla güncellenir.
* Örnek veri **sentetiktir** (`SYNTHETIC_IMPORT`); gerçek performans olarak yorumlanmaz. Sınıf C (tabandan ayrışma yok) beklenir.
* Veritabanı şeması v1’dir; sonraki sürümler için `Migrations` altyapısı hazırdır (yıkıcı geri dönüş yok).
* Overlay ve bildirimler isteğe bağlıdır; izin verilmezse işlev bozulmaz (ilerleme uygulama içinde görünür).
