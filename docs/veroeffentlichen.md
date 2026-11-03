# Veröffentlichen

Herausgeber im Play Store ist **Philipsen EDV** mit einem Organisationskonto (Entscheidung
30.10.2026, Konzept Frage 11). Bis das Konto steht, gibt es die APK zum Herunterladen und die
Vorbereitung für F-Droid.

## Versionen

`versionCode` steigt mit jeder Veröffentlichung um 1, `versionName` ist SemVer (im Store ab
1.0.0). Zu jedem `versionCode` gehören `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt`
und `…/en-US/changelogs/<versionCode>.txt` (je höchstens 500 Zeichen). Jede Version, die
erscheint, bekommt den Tag `v<versionName>` und ein GitHub-Release, aber nur auf Ralfs Wort.

Ablauf in WSL (gh dort als `GH=gh.exe`):

1. `scripts/release.sh entwurf --version 0.32.0` setzt `versionName`, erhöht `versionCode` um 1
   und schreibt die Hinweise aller PRs seit dem letzten Tag (Abschnitt „Für die
   Änderungshinweise“ der PR-Vorlage) in beide Changelogs. Solange es noch keinen Tag gibt:
   `--seit <Commit>`. PRs mit „Braucht HUBIplan“ zählt es auf; diese gehören in die
   Verträglichkeitstabelle in `hubiplan/docs/versionen.md`.
2. Changelogs lesen und kürzen, als PR „App 0.32.0“ squashen und mergen.
3. Auf Ralfs PC bauen und testen (`scripts/test.sh`), APK/AAB aus diesem Stand bauen.
4. Auf Ralfs Wort `scripts/release.sh taggen`: prüft Form und Reihenfolge der Version, dass
   der Stand das neueste Release enthält und dass beide Changelogs da sind und passen, zeigt die
   Notizen und legt nach Rückfrage Tag und GitHub-Release an. F-Droid baut aus dem Tag; APK und
   AAB lädt Ralf hoch.

Die App fragt `features` in `/api/app/v1/instance/` ab und blendet aus, was eine ältere
HUBIplan-Instanz noch nicht kann (`hubiplan/docs/versionen.md`).

## F-Droid (Variante `foss`)

- Keine Google-Bibliothek: `scripts/test.sh` bricht ab, wenn `fossReleaseRuntimeClasspath`
  Firebase oder Play-Dienste enthält.
- Beschreibung, Titel und Änderungen liest F-Droid aus `fastlane/metadata/android/`.
- F-Droid baut selbst aus einem Git-Tag (`v0.6.0`) und signiert mit eigenem Schlüssel.
  Antrag: Merge-Request an `fdroiddata` mit `metadata/de.hubiplan.app.yml`
  (`Builds: … gradle: [foss]`, `AutoUpdateMode: Version`, `UpdateCheckMode: Tags`).
- Hinweis für F-Droid: keine Anti-Features außer ggf. `NonFreeNet` (nein: HUBIplan ist frei).

## APK zum Herunterladen

`./gradlew assembleFossRelease` mit `keystore.properties` ergibt eine signierte APK
(`app/build/outputs/apk/foss/release/`). Den Schlüssel nie verlieren: Updates müssen mit
demselben Schlüssel signiert sein. Fingerabdruck (SHA-256) neben der Datei angeben.

## Google Play

**Entscheidung 30.10.2026:** Im Play Store erscheint die Variante `foss`, also ohne Google-Push
und ohne Google-Dienste (kein Firebase, keine Play-Dienste; freie Bibliotheken wie ZXing oder
AndroidX stammen zwar von Google, stehen aber unter der Apache-Lizenz). So gibt es kein Firebase-Projekt und kein Push-Gateway, und der
Herausgeber ist für keinen Verein Auftragsverarbeiter. Push kommt über UnifiedPush (z. B. ntfy);
ohne Push-App gleicht die App alle 15 Minuten ab, mit Push alle 6 Stunden. Die Variante `play`
(Firebase über das Push-Gateway) bleibt im Code, wird aber nicht veröffentlicht.

Voraussetzungen von Google (Stand 10/2026): `targetSdk` mindestens 36 (Android 16) für neue Apps
und Updates; die App zielt schon auf 37 (Android 17, AGP 9, Gradle 9). `minSdk` ist 28 (Android 9, seit 0.25.0: Schlüsselschutz bei gesperrtem Telefon). Ein Weg
zum Löschen von Konto und Daten in der App (Konto › „Konto und Daten löschen“) und als Webseite des
Herausgebers. Prüferzugang auf dem Testserver mit erfundenen Daten. Ausführliche Anleitung:
`anleitungen/play-store-veroeffentlichung.md` im Projektordner.

1. `./gradlew bundleFossRelease` (mit `keystore.properties`, Upload-Schlüssel), Datei
   `app/build/outputs/bundle/fossRelease/app-foss-release.aab`.
2. Im Play-Konto hochladen, Play App Signing einschalten. Datensicherheit siehe unten.

## Textbaustein für die Datenschutzhinweise des Vereins

Den Abschnitt kann der Verein in seine Datenschutzhinweise (HUBIplan › Einstellungen ›
Datenschutz) übernehmen. Eckige Klammern anpassen.

> **HUBIplan-App**
>
> Für Mitglieder gibt es die HUBIplan-App für Android. Sie verbindet sich ausschließlich mit
> unserer HUBIplan-Instanz [Adresse] und verarbeitet dieselben Daten wie der
> Mitgliederbereich im Browser (Termine, Rückmeldungen, Mitteilungen, eigenes Profil). Bei
> der Verbindung speichern wir für das Gerät einen Geräteschlüssel, die Bezeichnung des
> Geräts und den Zeitpunkt der letzten Nutzung; nach 180 Tagen ohne Nutzung wird das Gerät
> abgemeldet. Geräte lassen sich jederzeit unter „Mein Konto“ abmelden.
>
> **Push-Mitteilungen.** Wenn Sie Push einschalten, senden wir Mitteilungen
> Ende-zu-Ende-verschlüsselt (Web-Push) an den von Ihnen gewählten Push-Dienst: eine
> UnifiedPush-App wie ntfy (mit deren Server). Der Push-Dienst erfährt nur, dass zu einem
> Zeitpunkt eine Nachricht bestimmter Größe an Ihr Gerät geht, nicht deren Inhalt. Ohne
> Push-App fragt die App selbst regelmäßig bei uns nach neuen Mitteilungen. Rechtsgrundlage ist Ihre Einwilligung durch das Einschalten (Art. 6 Abs. 1
> lit. a DSGVO); Sie können Push in der App jederzeit ausschalten.
>
> **Kalender.** Auf Wunsch trägt die App Termine in einen Kalender auf Ihrem Gerät ein. Ob
> dieser Kalender mit einem Dienst (etwa Google oder einem anderen Anbieter) abgeglichen
> wird, bestimmen Sie in den Einstellungen Ihres Geräts. Termine im Gerätekalender können
> auch andere Apps lesen, denen Sie den Zugriff auf den Kalender erlaubt haben.
>
> **Mitgliederfoto.** Ein Foto, das Sie in der App aufnehmen oder auswählen, wird erst nach
> Ihrer Einwilligung hochgeladen und vor der Verwendung von uns freigegeben. Ortsangaben
> und andere Metadaten entfernt die App vor dem Hochladen. Einwilligung und Foto können Sie
> in der App jederzeit widerrufen bzw. löschen.
>
> Die App enthält keine Werbung und keine Analyse- oder Tracking-Dienste.

## Google Play: Angaben zur Datensicherheit

- Erhobene Daten: E-Mail-Adresse, Name, Telefonnummer, Anschrift (Profil, nur an die
  Instanz des Vereins), Fotos (nur auf Wunsch), Kalendertermine (nur auf dem Gerät),
  Geräte-IDs (Push-Kennung).
- Weitergabe an Dritte: keine (Push-Inhalte sind Ende-zu-Ende verschlüsselt; kein Google-Dienst).
- Verschlüsselung bei der Übertragung: ja. Löschung auf Wunsch: ja (im Verein und per Abmelden).
