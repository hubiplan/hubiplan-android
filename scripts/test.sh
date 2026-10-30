#!/usr/bin/env bash
# Baut die App und führt die Unit-Tests aus (lokal in WSL auf Ralfs PC).
#
#   scripts/test.sh [Gradle-Aufgaben …]
#
# Regeln: nie auf dem Testserver; alle Tests zusammen höchstens 12 Kerne (gemeinsamer
# Lock mit hubiplan/scripts/test.sh, Gradle auf 12 Worker begrenzt).
set -euo pipefail

LOCK="${HUBIPLAN_TEST_LOCK:-$HOME/.hubiplan-tests.lock}"
exec 9> "$LOCK"
if ! flock -n 9; then
    echo "Ein anderer Testlauf läuft gerade, warte …"
    flock 9
fi

cd "$(dirname "$0")/.."
if [ -z "${ANDROID_HOME:-}" ]; then
    echo "Abbruch: ANDROID_HOME ist nicht gesetzt (Android-SDK)." >&2
    exit 2
fi
TASKS=("$@")
[ ${#TASKS[@]} -eq 0 ] && TASKS=(testFossDebugUnitTest assembleFossDebug lintFossDebug)
./gradlew --no-daemon --max-workers=12 "${TASKS[@]}"
echo "OK: $(git rev-parse --short HEAD) gebaut und getestet."
