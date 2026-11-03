# Hinweise für die Arbeit an hubiplan-android

Projektregeln (gelten wie im Hauptprojekt `hubiplan`):

- **Nur lokal bauen und testen**, mit `scripts/test.sh` und installiertem Android-SDK.
  Auf gemeinsam genutzten Test- oder Produktivservern keine Tests ausführen.
- **Höchstens zwei Testläufe gleichzeitig, jeder höchstens 8 Kerne**, projektübergreifend für
  alle Projekte auf demselben Rechner: `scripts/test.sh` holt sich einen Platz mit `testplatz`
  (aus `hubiplan/scripts/testplatz.sh`; Einrichtung siehe `README.md`, „Bauen und testen“).
- Temporäre Dateien im lokal konfigurierten Temp-Verzeichnis ablegen; keine festen Laufwerke
  oder persönlichen Verzeichnisse voraussetzen.
- Keine Passwörter, Signaturschlüssel oder Geheimnisse ins Repo oder in Chats.
- Keine internen Chat- oder Sitzungslinks, privaten Konzeptpfade, nicht öffentlichen Hostnamen
  oder realen Betriebsdaten in Dateien, Commit-Nachrichten oder Pull Requests veröffentlichen.
- Nur erfundene Daten in Tests und Beispielen.
- CI (GitHub Actions) nur von Hand (workflow_dispatch); keine automatischen Auslöser.
- Sprache in Oberfläche, Doku und Commits: Deutsch.
- **Neue Texte in der App gleich auch auf Englisch**: `tr(…)`/`trn(…)` und Eintrag in
  `app/src/main/assets/texte/en.json`; `TexteTest` schlägt sonst fehl.
- **Jeder PR füllt „Für die Änderungshinweise“** der PR-Vorlage aus (Deutsch und Englisch).
  App-Versionen bleiben `versionName` (SemVer, im Store ab 1.0.0) + `versionCode`; Tags `v0.x.y`
  nur nach ausdrücklicher Freigabe durch den Herausgeber (siehe `docs/veroeffentlichen.md`).
- Variante `foss` ohne jede Google-Bibliothek (F-Droid); Google-Dienste nur in `play`.
- Die App spricht nur mit der HUBIplan-Instanz des Mitglieds (`/api/app/v1/`, `/api/portal/…`),
  Beschreibung in `hubiplan/docs/app.md`.
- Ohne lokal verfügbares Android-SDK keine erfolgreichen Builds oder Tests behaupten;
  stattdessen die tatsächlich durchgeführten Prüfungen dokumentieren.
