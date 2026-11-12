#!/usr/bin/env bash
# Schreibt die Prüfsummen aller Gradle-Abhängigkeiten nach gradle/verification-metadata.xml
# (Dependency Verification, SHA-256). Nach jeder Änderung an Abhängigkeiten oder Plugins
# (gradle/libs.versions.toml, build.gradle.kts, Gradle-Fassung) ausführen und die geänderte
# Datei mit einchecken. Neue Einträge vor dem Einchecken ansehen: Gradle trägt ein, was es gerade
# herunterlädt, die Prüfung schützt erst ab dann vor ausgetauschten Dateien.
#
#   scripts/pruefsummen.sh          # neue Abhängigkeiten ergänzen
#   scripts/pruefsummen.sh --neu    # Datei neu aufbauen (entfernt nicht mehr genutzte Einträge)
#
# Läuft wie scripts/test.sh über einen lokalen Testplatz und baut dabei alle Fassungen, damit
# jede Konfiguration (Tests, Lint, foss, play, Veröffentlichung) erfasst ist.
set -euo pipefail

cd "$(dirname "$0")/.."
DATEI=gradle/verification-metadata.xml
if [ "${1:-}" = "--neu" ]; then
    # Die Vertrauensregeln (z. B. für Quelltext-Jars der IDE) bleiben erhalten
    if [ -f "$DATEI" ]; then
        python3 - "$DATEI" <<'PY'
import re, sys
pfad = sys.argv[1]
text = open(pfad, encoding="utf-8").read()
text = re.sub(r"\s*<components>.*</components>", "", text, flags=re.S)
open(pfad, "w", encoding="utf-8").write(text)
PY
    fi
    shift
fi
scripts/test.sh --write-verification-metadata sha256 "$@" \
    testFossDebugUnitTest lintFossDebug \
    assembleFossDebug assemblePlayDebug assembleFossDebugAndroidTest \
    assembleFossRelease assemblePlayRelease bundleFossRelease bundlePlayRelease
