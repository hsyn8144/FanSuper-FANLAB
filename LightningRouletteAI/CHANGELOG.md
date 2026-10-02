# Sürüm notları

## 1.0.1 — 2026-10-03

- TABLE ve overlay artık her bahis sınıfının tüm olasılıklarını gösteriyor: kırmızı/siyah, çift/tek, 1–18/19–36, üç düzine, üç sütun ve her kategoride ayrı 0 olasılığı. Altın çerçeve, kategorideki en yüksek model seçimini belirtir.
- Yüzde hesabının dayanağı UI ve README'de açıklandı: decay frekansı + son 50 spin + birinci derece geçiş; sonuç sonrası Hedge ağırlık güncellemesi; %60 model karışımı + %40 Avrupa ruleti teorik tabanı. Yüzdeler garanti veya kalibre başarı gibi sunulmuyor.
- Overlay NEXT satırında merkez olasılığı ve komşu aralığı olasılığı ayrı gösteriliyor. Tahmin detayına 0–36 tam sıralı olasılık görünümü eklendi; farklı geçmişlerin önerileri değiştirdiğini doğrulayan çekirdek testi eklendi. Eşit olasılıkta sıra deterministik kalır, yapay randomizasyon yoktur.
- Settings seçenek satırının tamamı dokunulabilir; yatay seçenekler küçük ekranlarda kaydırılabilir ve tüm modlar erişilebilir.
- TABLE kategori/sıfır sınıfı ve model öğrenmesi için çekirdek regresyon testleri; ayar etkileşimleri için Android uçtan uca testleri eklendi.
- `versionCode = 2`, `versionName = 1.0.1`.

## 1.0.0

- İlk Avrupa ruleti (0–36) sürümü. Ayrıntı: `README.md`.
