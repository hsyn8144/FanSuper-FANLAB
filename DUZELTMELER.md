# Bu paket için yapılan düzeltmeler

## 1) Overlay döndürme/hizalama düzeltmesi
`OverlayService.kt`'ye `onConfigurationChanged` eklendi. Ekran döndürüldüğünde kart artık
zorla yeniden ölçülüp ekran içine sığdırılıyor — önceden bu yalnızca kartın KENDİ boyu
değiştiğinde tetikleniyordu, ekran boyu değiştiğinde değil.

## 2) Hızlı art arda tuşlamada "echo" sayaç kayması
`EngineHost.kt` / `Echo`: anlık geri bildirim listesi ekranda son 6 sayıyla sınırlı olsa da
(`takeLast(6)`), bekleyen toplam işlem sayısını artık ayrı bir `pendingCount` alanı takip
ediyor. Önceden 6'dan fazla hızlı tuşlamada bu sayaç kırpılan listenin boyutundan
hesaplanıyordu ve nadir durumlarda ekranın erken/geç senkron olmasına yol açabiliyordu.

## 3) Python köprüsü: JSON'suz sıcak yol
`fan_super.py`'de `step_fast` ve `undo_to_fast` eklendi (mevcut `step`/`undo_to` JSON'lu
haliyle, testler için ve geriye dönük uyumluluk için AYNEN duruyor). `PythonBridge.kt`
artık her tuşlamada bu hızlı yolu kullanıyor: `json.dumps` + `JSONArray` ayrıştırma
maliyeti olmadan, Chaquopy'nin otomatik liste dönüşümüyle doğrudan `DoubleArray` alıyor.
Python tarafındaki 7 birim test aynen geçiyor (davranış değişmedi, sadece taşıma biçimi).

## 4) Detaylı işlem logu ekranı
- `EngineHost.kt`: her `EKLE`/`GERİ AL` işleminde o anki hakem kararı + her üye modelin
  (🔵 Kotlin ve 🐍 Python meclisi) top1/top2/ağırlık bilgisini `ActivityLogEntry` olarak
  bellekte tutan `activityLog` (son 500 kayıt) eklendi. Ekstra hesap yok — motor bu veriyi
  zaten her adımda üretiyor.
- Yeni `LogScreen.kt`: Ayarlar → "📋 İşlem logları" ile açılır, her satır tıklanınca o
  adımdaki tüm üye modellerin tahminini gösterir. `SettingsScreen.kt`'ye giriş noktası
  eklendi.
- Bonus: "GERİ AL" logu, geri almanın "anında" mı yoksa "tam yeniden kurma" (yavaş yol)
  ile mi tamamlandığını da gösteriyor — bu, geri alma tuşuyla ilgili olası Python
  senkron sorunlarını adb logcat açmadan doğrudan uygulama içinden teşhis etmenizi sağlar.

## ÖNEMLİ — APK derlemesi hakkında
Bu ortamda (kod asistanının sanal makinesi) Android SDK yok ve ağ erişimi yalnızca
birkaç paket deposuna (PyPI, npm, GitHub vb.) izin veriyor; `services.gradle.org` ve
Google'ın Maven deposu (`dl.google.com`) erişime kapalı. Bu yüzden burada
`./gradlew assembleDebug` çalıştırıp size APK üretemedim (denedim, Gradle dağıtımını
bile indiremedi). Kodu derlemeden gönderiyorum — Python tarafındaki 7 testi burada
çalıştırıp geçtiğini doğruladım, ama Kotlin/Android tarafını gerçek bir Android
araç zinciriyle SİZİN derlemeniz/test etmeniz gerekiyor.

İki kolay yol:
1. **GitHub Actions (önerilen, elinizde zaten var):** Bu projeyi bir GitHub reposuna
   push edin — `.github/workflows/build.yml` otomatik olarak APK üretip
   `FAN_SUPER_APK` adıyla Actions sekmesinde, main dalında ayrıca Releases'e koyuyor.
2. **Android Studio:** Bu zip'i açıp projeyi Android Studio'da açın, JDK 17 + Python 3.11
   kurulu olduğundan emin olun, `./gradlew assembleDebug` (veya Studio'nun "Run" düğmesi).
