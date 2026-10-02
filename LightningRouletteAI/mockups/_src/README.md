# Tasarım görsellerinin üreticisi

Görseller elle çizilmedi; bu klasördeki **yalnızca standart kütüphaneli Python** üretici HTML/SVG sayfaları üretir, **Chromium** ise PNG (2×) ve tek PDF’e çevirir.
Böylece bir ekranı değiştirmek tek bir Python fonksiyonunu düzeltmek kadar kolaydır ve tüm sayfalar aynı tasarım sistemini kullanır.

```bash
bash setup_tools.sh                                  # Chromium (npm) + yazı tipleri + fonttools (ortam sıfırlanırsa yeniden)
python3 build.py html                                # _build/*.html + manifest.json
/home/user/render-tools/venv/bin/python glyphcheck.py   # kutucuk (eksik glif) denetimi
node render.mjs all                                  # ../png/*.png + ../LightningRouletteAI_Tasarim_Katalogu.pdf (+ taşma/rozet denetimi)
python3 build.py readme                              # ../README.md galerisi + ../thumbs
python3 build.py zip                                 # ../LightningRouletteAI_Gorseller.zip
```

| Dosya | Görev |
|---|---|
| `data.py` | Wheel sırası, sektör/bölge tanımları, örnek veri (tüm sayfalarda tutarlı) |
| `lib.py` · `common.py` · `ovl.py` · `charts.py` | Bileşenler, ortak bloklar, overlay kartı, SVG grafikler (wheel, masa …) |
| `s_*.py` | Bölümler: GENEL, A Açılış, B Ana, C Wheel/Masa, D Meclisler, E LAB, F Veri, G Ayarlar, H Overlay |
| `style.css` · `style_extra.css` · `sheet.js` | Görünüm ve rozet yerleştirme / taşma denetimi |

Otomatik denetimler: eksik glif, dışarı taşan öğe, rozet ↔ açıklama eşleşmesi, çakışan rozetler.
