# Veröffentlichen

Stand Schritt A6. Wer die App im Play Store herausgibt, ist noch offen (Konzept, Frage 11);
bis dahin gibt es die APK zum Herunterladen und die Vorbereitung für F-Droid.

## Versionen

`versionCode` steigt mit jeder Veröffentlichung um 1, `versionName` folgt dem Schritt
(0.6.0 = A6). Zu jedem `versionCode` gehört `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt`
(höchstens 500 Zeichen).

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

## Google Play (Variante `play`)

1. Firebase-Projekt des Herausgebers anlegen, Android-App `de.hubiplan.app` eintragen.
   Aus der Projektübersicht die vier Werte in `firebase.properties` übernehmen (nicht ins Repo).
2. Dienstkonto mit der Rolle „Firebase Cloud Messaging API Admin“ anlegen, Schlüssel (JSON)
   nur auf dem Server des Push-Gateways ablegen (`PUSH_GATEWAY_FCM_CREDENTIALS`).
3. Push-Gateway betreiben (Beschreibung in `hubiplan/docs/app.md`, Abschnitt Push-Gateway)
   und in jeder Instanz `PUSH_GATEWAY_URL` setzen.
4. `./gradlew bundlePlayRelease`, im Play-Konto hochladen (Play App Signing).
   Datensicherheit-Angaben siehe unten.

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
> UnifiedPush-App wie ntfy (mit deren Server) oder, in der Fassung aus Google Play, Google
> Firebase Cloud Messaging über das Push-Gateway von [Herausgeber]. Der Push-Dienst erfährt
> nur, dass zu einem Zeitpunkt eine Nachricht bestimmter Größe an Ihr Gerät geht, nicht
> deren Inhalt. Bei Google gelten dessen Datenschutzbestimmungen; dabei kann eine
> Übermittlung in die USA stattfinden (Angemessenheitsbeschluss EU-US Data Privacy
> Framework). Rechtsgrundlage ist Ihre Einwilligung durch das Einschalten (Art. 6 Abs. 1
> lit. a DSGVO); Sie können Push in der App jederzeit ausschalten.
>
> **Kalender.** Auf Wunsch trägt die App Termine in einen Kalender auf Ihrem Gerät ein. Ob
> dieser Kalender mit einem Dienst (etwa Google oder einem anderen Anbieter) abgeglichen
> wird, bestimmen Sie in den Einstellungen Ihres Geräts.
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
- Weitergabe an Dritte: keine (Push-Inhalte sind verschlüsselt; Firebase als Auftragsverarbeiter).
- Verschlüsselung bei der Übertragung: ja. Löschung auf Wunsch: ja (im Verein und per Abmelden).
