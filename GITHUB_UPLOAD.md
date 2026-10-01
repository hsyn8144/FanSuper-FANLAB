# GitHub'a Yükleme

Bu paket FanSuper v1.2 + FAN LAB kaynak kodlarının GitHub'a yüklenmeye hazır halidir.

## Önerilen kullanım

1. GitHub'da yeni bir repository oluşturun.
2. Repository'yi Public veya Private seçebilirsiniz.
3. Bu ZIP'i bilgisayarda/telefonda açın.
4. ZIP'in içindeki **proje kökündeki tüm dosya ve klasörleri** repository köküne yükleyin.
5. Özellikle `.github/workflows/build.yml`, `gradlew`, `gradle/`, `app/`, `settings.gradle.kts` ve `.gitignore` dosyaları yüklenmelidir.
6. GitHub Actions sekmesinden **FAN SUPER Build** workflow'unu çalıştırabilirsiniz.

## Önemli

- `build/`, `.gradle/`, `.idea/`, `local.properties`, APK/AAB ve yerel Python ortamları repository'ye dahil edilmemelidir.
- API anahtarı, token, keystore veya özel anahtar commit etmeyin.
- GitHub Actions workflow'u Python 3.11 kurarak Chaquopy tabanlı Python meclisi için gerekli build ortamını hazırlamayı dener.
