# Lizenzen

Die App steht unter der **AGPL-3.0-or-later** ([LICENSE](../LICENSE)). Grundlage dieses
Dokuments sind die Lizenzaudits vom 31.10.2026 und 10.11.2026 (Berichte im Projektordner unter
`audit/`); es ist keine anwaltliche Prüfung.

## Lizenzseite in der App

„Lizenzen“ steht auf der Startseite (ohne Anmeldung) und unter Konto. Dort:

- AGPL-Text der App (beim Bauen aus `LICENSE` kopiert),
- Link zum Quellcode **genau dieser Version** (`…/tree/v<versionName>`, `Licenses.sourceUrl`)
  und zum Repository mit allen Fassungen. Der Link passt nur, wenn jede ausgelieferte Version
  ihren Tag bekommt (siehe unten, „Offen“),
- alle Bibliotheken der jeweiligen Variante mit Version und Lizenz, dazu die Lizenztexte
  (`app/src/main/assets/lizenzen/`: Apache 2.0, MPL 2.0),
- Hinweise der Bibliotheken: OkHttp enthält die Public Suffix List (MPL 2.0). Als Quellform
  (MPL § 3.2) verlinkt die Seite die Datei `publicsuffixes.gz` im Tag der eingebauten
  OkHttp-Version (`Licenses.publicSuffixSource`, Pfad gilt für OkHttp 4.x; `LicensesTest`
  schlägt bei einem Wechsel auf 5.x fehl, damit der Pfad geprüft wird).

Die Bibliotheksliste schreibt die Gradle-Aufgabe `lizenzliste<Variante>` (in
`app/build.gradle.kts`) beim Bauen aus dem tatsächlichen Laufzeit-Abhängigkeitsgraphen der
Variante nach `lizenzen/bibliotheken.tsv` in die Assets. Die Zuordnung Bibliothek → Lizenz
steht in `LizenzlisteTask.licenseOf`. Kommt eine Bibliothek hinzu, deren Gruppe dort fehlt,
**bricht der Bau ab**: dann den Originaltext der Lizenz in genau dieser Version prüfen und
die Zuordnung ergänzen; für eine neue Lizenzart auch den Volltext in die Assets legen und in
`data/Licenses.kt` eintragen. Verlangt eine Bibliothek eine NOTICE-Datei, deren Text in
`Licenses.NOTICES` aufnehmen.

## Varianten

| Variante | Google | Lizenzlage |
|---|---|---|
| `foss` | keine Google-Dienste; freie Bibliotheken von Google (AndroidX, ZXing, Guava-Teil) unter Apache 2.0 | nur freie Lizenzen (Apache 2.0, MPL 2.0 für die Public Suffix List) |
| `play` | zusätzlich Firebase Messaging (Apache 2.0) | sieben Bausteine unter der proprietären Android-SDK-Lizenz: play-services-base, -basement, -cloud-messaging, -stats, -tasks, firebase-iid-interop, firebase-measurement-connector |

Die Variante `play` wird derzeit nicht veröffentlicht (Entscheidung 30.10.2026, siehe
[veroeffentlichen.md](veroeffentlichen.md)). Weitergeben darf man sie trotzdem: Seit
31.10.2026 erlaubt eine Zusatzerlaubnis nach AGPL § 7 die Verbindung mit den Google
Play-Diensten und Firebase (Wortlaut im [README](../README.md#zusätzliche-erlaubnis-für-google-bibliotheken-agpl--7)).
Die Erlaubnis hat Ralf Philipsen als alleiniger Rechteinhaber erteilt. Beiträge von außen
fallen nur darunter, wenn ihre Urheber zustimmen; das gehört deshalb in die Bedingungen für
Beiträge.

## Gradle-Wrapper

`gradlew`, `gradlew.bat` und `gradle/wrapper/gradle-wrapper.jar` stammen von Gradle
(Apache 2.0); Volltext und Herkunft in `gradle/wrapper/`.

## Offen

- **Quellcode:** Das Repository ist seit 31.10.2026 öffentlich. Jede ausgelieferte Version
  (Play Store, F-Droid, APK) braucht ihren Tag `v<versionName>`, damit der Link in der App
  und der passende Quellcode dauerhaft erreichbar sind (AGPL § 6). Stand 10.11.2026 fehlen die
  Tags für die bereits im Play Store erschienenen Versionen 1.0.0 bis 1.3.0 (Lizenzaudit
  10.11.2026, L02); sie setzt der Herausgeber nach Freigabe nachträglich.
- **Variante `play`:** vor einer Veröffentlichung die Weitergaberechte für die sieben
  Google-Bausteine und die Hinweise je Baustein klären (Lizenzaudit 10.11.2026, L03/FQ-03).
- **Symbole:** Die Material-Symbole stammen aus der Bibliothek material-icons-core (Apache 2.0).
- **Storematerial:** Nur erfundene oder freigegebene Inhalte (keine echten Vereinsdaten).
