# Yerel geliştirme araçları (Android SDK gerekmez)

Android/Compose/Room/Chaquopy katmanı yalnızca CI’da (GitHub Actions) derlenir. **Saf Kotlin çekirdek** (`app/src/main/java/fan/lightningroulette/core`) ve JVM testleri yerelde de çalışır:

| Dosya | İşlev |
|---|---|
| `kc.sh` | `tools/kc.sh` çekirdeği derler · `tools/kc.sh test` testleri AYRI modül olarak derleyip çalıştırır (Gradle’daki smart-cast/internal kuralları) · `tools/kc.sh gensample` örnek veri asset’ini üretir |
| `LocalRunner.kt`, `junit-stubs/` | JUnit olmadan `@Test` yöntemlerini çalıştıran küçük koşucu |
| `GenSample.kt` | `app/src/main/assets/sample_2000.csv` üreticisi |
| `e2e.sh` | CI emülatör işinin betiği (debug APK kur → overlay izni → `connectedDebugAndroidTest` → log topla) |

## Araç zinciri (CI ile aynı sürümler)

```bash
# JDK 17 (PyPI üzerinden, apt/dl.google.com gerekmez) ve Kotlin 1.9.22 derleyicisi (npm)
mkdir -p ~/jvm-tools/jdk17 ~/jvm-tools/kc19 && cd ~/jvm-tools
(cd jdk17 && pip download "jdk4py==17.*" --no-deps -d . && unzip -q jdk4py-*.whl)
(cd kc19  && npm pack kotlin-compiler@1.9.22 && tar -xzf kotlin-compiler-1.9.22.tgz && chmod +x package/bin/*)
cd <repo>/LightningRouletteAI && tools/kc.sh test
```

Python meclisi testi: `pip install "numpy<2" && python -m unittest -v python_tests.test_council`
