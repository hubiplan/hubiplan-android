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
DEFAULT=0
if [ ${#TASKS[@]} -eq 0 ]; then
    DEFAULT=1
    TASKS=(testFossDebugUnitTest assembleFossDebug lintFossDebug assemblePlayDebug)
fi
./gradlew --no-daemon --max-workers=12 "${TASKS[@]}"
if [ "$DEFAULT" = 1 ]; then
    # F-Droid: in der foss-Fassung darf keine Google-Bibliothek stecken (Firebase, Play-Dienste)
    if ./gradlew --no-daemon -q :app:dependencies --configuration fossReleaseRuntimeClasspath \
        | grep -E "com\.google\.(firebase|android\.gms|android\.play)|com\.google\.android\.datatransport"; then
        echo "Abbruch: Google-Bibliothek in der foss-Fassung." >&2
        exit 1
    fi
fi
echo "OK: $(git rev-parse --short HEAD) gebaut und getestet."
