#!/bin/bash
# Yerel derleme + test (yalnızca saf Kotlin çekirdek). Ön koşul: JDK + kotlinc (bkz. tools/README.md).
# Kullanım: tools/kc.sh [test]   → çekirdeği derler; "test" verilirse birim testlerini de çalıştırır.
set -e
J=${LRA_JVM:-/home/user/jvm-tools}
export JAVA_HOME=$J/jdk4py/jdk4py/java-runtime
export PATH=$JAVA_HOME/bin:$J/kc/package/bin:$PATH
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=${LRA_OUT:-/tmp/lra_build}
mkdir -p "$OUT"
SRC="$ROOT/app/src/main/java/fan/lightningroulette/core"
if [ "$1" = "test" ]; then
  kotlinc "$SRC"/*.kt "$ROOT"/tools/junit-stubs/org/junit/*.kt "$ROOT"/app/src/test/java/fan/lightningroulette/*.kt "$ROOT"/tools/LocalRunner.kt -d "$OUT/test.jar" -Xskip-prerelease-check -nowarn 2>&1 | grep -v "^warning" || true
  java -Dfile.encoding=UTF-8 -cp "$OUT/test.jar:$J/kc/package/lib/kotlin-stdlib.jar" LocalRunnerKt fan.lightningroulette.CoreTest fan.lightningroulette.ResumeTest fan.lightningroulette.ImportTest fan.lightningroulette.LabTest
else
  kotlinc "$SRC"/*.kt -d "$OUT/core.jar" -nowarn 2>&1 | grep -v "^warning" || true
  echo "çekirdek derlendi: $OUT/core.jar"
fi
