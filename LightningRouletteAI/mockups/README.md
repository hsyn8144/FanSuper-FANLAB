# ⚡ Lightning Roulette AI — Ekran Tasarımları (onay bekliyor)

> 🚧 **TASLAK:** sayfa üretimi sürüyor — şu an **7** sayfa hazır, toplam yaklaşık 65 sayfa olacak. Bu bir ara sürümdür.

> **Durum:** Bu klasör yalnızca **tasarım önizlemesidir**. Uygulama kodu **henüz yazılmadı**; görseller onaylanınca geliştirme başlayacak.

Her sayfa: telefon ekranı + **numaralı işaretler** + altında her sekme/buton/alanın ne yaptığını anlatan açıklama listesi + ilgili prompt maddeleri (§).
Tüm sayı ve yüzdeler **örnektir**; uygulama hiçbir sonucun garanti olduğunu iddia etmez (tahmin → LOCK → sonuç → değerlendirme → öğrenme).

## İndir

| Dosya | Ne işe yarar |
|---|---|
| [`LightningRouletteAI_Tasarim_Katalogu.pdf`](LightningRouletteAI_Tasarim_Katalogu.pdf) | Tüm sayfalar tek PDF (telefonda kaydırarak gez) |
| [`LightningRouletteAI_Gorseller.zip`](LightningRouletteAI_Gorseller.zip) | Tüm PNG dosyaları (960 px genişlik) tek ZIP |
| [`png/`](png) | Tek tek tam çözünürlüklü PNG sayfaları |

**Toplam 7 sayfa.** Küçük resme dokununca tam boyutlu görsel açılır.

## İçindekiler

- **GENEL** — 3 sayfa
- **A · AÇILIŞ** — 4 sayfa

## GENEL

<table><tr><td align="center" valign="top" width="33%"><a href="png/00_uygulama_haritasi.png"><img src="thumbs/00_uygulama_haritasi.jpg" width="230" alt="Uygulama haritası ve ekran dizini"></a><br><sub><b>00</b> · Uygulama haritası ve ekran dizini</sub></td><td align="center" valign="top" width="33%"><a href="png/01_eslesme_1_4.png"><img src="thumbs/01_eslesme_1_4.jpg" width="230" alt="FanSuper 1.4 → Lightning Roulette AI"></a><br><sub><b>01</b> · FanSuper 1.4 → Lightning Roulette AI</sub></td><td align="center" valign="top" width="33%"><a href="png/02_tasarim_sistemi.png"><img src="thumbs/02_tasarim_sistemi.jpg" width="230" alt="Tasarım sistemi"></a><br><sub><b>02</b> · Tasarım sistemi</sub></td></tr></table>

## A · AÇILIŞ

<table><tr><td align="center" valign="top" width="33%"><a href="png/03_splash.png"><img src="thumbs/03_splash.jpg" width="230" alt="Splash · veritabanı hazırlığı"></a><br><sub><b>03</b> · Splash · veritabanı hazırlığı</sub></td><td align="center" valign="top" width="33%"><a href="png/04_hosgeldin.png"><img src="thumbs/04_hosgeldin.jpg" width="230" alt="Hoş geldin · dürüstlük uyarısı"></a><br><sub><b>04</b> · Hoş geldin · dürüstlük uyarısı</sub></td><td align="center" valign="top" width="33%"><a href="png/05_izinler.png"><img src="thumbs/05_izinler.jpg" width="230" alt="İzinler"></a><br><sub><b>05</b> · İzinler</sub></td></tr></table>
<table><tr><td align="center" valign="top" width="33%"><a href="png/06_baslangic_verisi.png"><img src="thumbs/06_baslangic_verisi.jpg" width="230" alt="Başlangıç verisi"></a><br><sub><b>06</b> · Başlangıç verisi</sub></td><td></td><td></td></tr></table>

## Onay

Beğendiğin / değiştirmek istediğin sayfaları **numarasıyla** söylemen yeterli (ör. “12 numaralı sayfada şunu değiştir”). Onay verdiğinde uygulama (Kotlin + Python, Room, overlay, LAB, test, README) bu tasarıma göre yazılıp CI ile derlenecek.

## Yeniden üretme

Görseller `_src/` altındaki stdlib-only Python üretici + Chromium ile oluşturulur (`_src/README.md`).
