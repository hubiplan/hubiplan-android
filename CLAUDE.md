# Hinweise für die Arbeit an hubiplan-android

Feste Regeln von Ralf (gelten wie im Hauptprojekt `hubiplan`):

- **Nie Tests auf dem Testserver (srv1422944) ausführen.** Gebaut und getestet wird lokal in WSL
  (Distribution `Debian`) auf Ralfs PC mit `scripts/test.sh`.
- **Alle Tests zusammen nutzen höchstens 12 Kerne**, auch wenn mehrere Sitzungen testen
  (gemeinsamer Lock `~/.hubiplan-tests.lock`, `org.gradle.workers.max=12`).
- Temporäres auf Laufwerk E: (in WSL `/mnt/e/…`).
- Keine Passwörter, Signaturschlüssel oder Geheimnisse ins Repo oder in Chats.
- Nur erfundene Daten in Tests und Beispielen.
- CI (GitHub Actions) nur von Hand (workflow_dispatch); keine automatischen Auslöser.
- Sprache in Oberfläche, Doku und Commits: Deutsch.
- Variante `foss` ohne jede Google-Bibliothek (F-Droid); Google-Dienste nur in `play`.
- Die App spricht nur mit der HUBIplan-Instanz des Mitglieds (`/api/app/v1/`, `/api/portal/…`),
  Beschreibung in `hubiplan/docs/app.md`; Konzept `konzepte/android-app.md` im Projektordner.
- Cloud-Sitzungen erreichen das Android-SDK nicht (dl.google.com gesperrt): Code dort schreiben,
  bauen und testen auf Ralfs PC.
