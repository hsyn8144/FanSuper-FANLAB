#!/bin/bash
# Emülatörde (android-emulator-runner içinde) çalışır: debug APK kur → overlay iznini ver → uçtan uca Compose testi → log topla.
# Çıkış kodu burada yorumlanmaz; sonuç e2e_rc.txt'ye yazılır ve sonraki CI adımı değerlendirir.
set +e
cd "$(dirname "$0")/.."
chmod +x gradlew
./gradlew --no-daemon installDebug 2>&1 | tail -5
adb shell appops set fan.lightningroulette SYSTEM_ALERT_WINDOW allow
adb shell pm grant fan.lightningroulette android.permission.POST_NOTIFICATIONS 2>/dev/null
adb logcat -c
./gradlew --no-daemon connectedDebugAndroidTest 2>&1 | tee e2e.log | tail -40
RC=${PIPESTATUS[0]}
adb logcat -d -v time > logcat_full.txt
grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in|LR-E-|chaquopy|lr_council|Traceback|Exception" logcat_full.txt | grep -v "StrictMode\|W/System\|Choreographer" | head -300 > logcat_errors.txt
echo $RC > e2e_rc.txt
exit 0
