# HUBIplan-App für Android

**Die Android-App zu [HUBIplan](https://github.com/hubiplan/hubiplan), der freien Vereinsverwaltung.**

Die App bringt Termine, Mitteilungen, den Mitgliedsausweis und die wichtigsten
Verwaltungsaufgaben aufs Telefon. Sie ergänzt den Mitgliederbereich im Browser und spricht
nur mit der HUBIplan-Instanz des eigenen Vereins, nie mit einem fremden Dienst. Die Variante
`foss` kommt ganz ohne Google-Dienste aus; Push geht dort über UnifiedPush.

> **English:** The Android companion app for [HUBIplan](https://github.com/hubiplan/hubiplan),
> free membership management for clubs. It talks only to the member's own HUBIplan instance;
> the `foss` flavour contains no Google services. The app is available in German and
> English; the developer documentation is in German. Issues and security reports in English
> are welcome.

Paketname `de.hubiplan.app`, Version 1.4.0, ab Android 9, für Telefon und Tablet.

## Inhalt

- [Für Mitglieder](#für-mitglieder)
- [Für Vorstand, Untereinheiten und Kasse](#für-vorstand-untereinheiten-und-kasse)
- [Verbinden](#verbinden)
- [Varianten](#varianten)
- [Technik](#technik)
- [Bauen und testen](#bauen-und-testen)
- [Veröffentlichen](#veröffentlichen)
- [Mitwirken](#mitwirken)
- [Sicherheit](#sicherheit)
- [Lizenz](#lizenz)

## Für Mitglieder

Unten (auf dem Tablet seitlich) stehen Termine, Aufgaben, Neues (die Mitteilungen), Ich und
– mit Rechten – Verwalten, für Musiker zusätzlich Noten. „Mein Konto“ öffnet das Symbol oben
rechts. Die Zurück-Taste führt aus jedem Bereich zuerst zu „Termine“, erst dort schließt sie
die App.

- Termine der eigenen Einheiten (Verein, Untereinheit, weitergegebene Termine des
  Dachverbands), Zu- und Absage (auf Wunsch des Vereins auch „Vielleicht“) mit einem
  Fingertipp, Bemerkung, Anhänge, Anfahrt mit Navigation und Push zur Einladung
- Termine im Gerätekalender mit Erinnerungen und Hinweis „Rückmeldung fehlt“
- Mitteilungen des Vereins lesen, Anhänge im Browser öffnen
- Aufgaben: zugewiesene Aufgaben und offene Angebote übernehmen, erledigen oder zurückgeben;
  Helferdienste: eigene Schichten sehen und absagen, sich in freie Schichten eintragen
- Push ohne Google über eine UnifiedPush-App (z. B. ntfy), Inhalt Ende-zu-Ende
  verschlüsselt (Web-Push, RFC 8291); ohne Push-App holt die App alle 15 Minuten ab
- Reiter „Ich“ wie „Mein Bereich“ im Browser: Mitgliedsausweis, Meine Daten, Rechnungen mit
  Lastschriftmandat, Versammlungen mit Tagesordnung, Anträgen und Protokollen, Meine
  Ergebnisse mit Ranglisten, Helferdienste, Dokumente und für Ansprechpartner einer Firma
  „Mitarbeiter“ (für wen die Firma zahlt, neue Mitarbeiter melden)
- Mitgliedsausweis mit QR-Code oben unter „Ich“ (langes Drücken auf das App-Symbol: „Ausweis
  zeigen“), auch ohne Netz (verschlüsselt gespeichert);
  zum Vorzeigen bildschirmfüllend und mit voller Helligkeit; auf Wunsch in Google Wallet oder
  als Wallet-Pass (`.pkpass`), wenn der Verein das anbietet (auch in `foss`, ohne
  Google-Bibliothek)
- eigenes Profil ansehen und Änderungen melden (der Verein prüft sie)
- Noten für Musiker: Notenmappen und Programme der eigenen Stimme, verschlüsselt für
  unterwegs, Notenpult im Vollbild mit Fußpedal, Anhören und Transponieren
- Mitgliederfoto mit der Kamera aufnehmen oder aus der Galerie wählen, mit Einwilligung;
  der Verein gibt es frei, Foto und Einwilligung lassen sich in der App widerrufen
- Ansprechpartner einer Organisation (Firma, Verein, Behörde …) melden sich wie Mitglieder an;
  die Auswahl zeigt „Verein: Organisation (Ansprechpartner)“, der Ausweis das Logo der Organisation
- die App zeigt nur, was der Verein in HUBIplan eingeschaltet hat (Module je Zugang)
- Farben und Wappen des Vereins aus HUBIplan; Verknüpfung mit dem Wappen des Vereins auf
  dem Startbildschirm
- Deutsch oder Englisch (App-Sprache von Android oder Auswahl unter Mein Konto › Sprache)
- mehrere Konten bei verschiedenen HUBIplan-Instanzen, jedes mit eigenem Geräteschlüssel,
  eigenen Kalendern und eigenem Push

## Für Vorstand, Untereinheiten und Kasse

Der Reiter „Verwaltung“ erscheint nur mit den passenden Rechten. Er wird einmal mit
Passwort und Zwei-Faktor-Code freigeschaltet, danach mit Fingerabdruck oder PIN geöffnet;
Bildschirmfotos sind dort gesperrt. Die Verwaltung beginnt mit einer Liste „Was möchtest du
tun?“: oben „Ausweis prüfen“, darunter „Zu erledigen“ mit Anzahl und die Bereiche
Aufgaben, Mitglieder, Termine, Wettbewerbe, Geschäftspartner, Kassenbuch, Rechnungen,
Jubiläen und Geburtstage, Qualifikationen und Fristen, je nach Rechten.

- Kurzmitteilung an alle Mitglieder der Einheit, mit Empfängerzahl vorab
- offene Aufgaben, Geburtstage und Jubiläen
- Aufgaben in Einheiten und Abteilungen: offene und erledigte Aufgaben lesen, private
  Aufgaben mit optionaler Frist anlegen, mit Bearbeitungsrecht erledigen und wieder öffnen;
  Änderungen lassen sich im Hinweis rückgängig machen. Auch ein Konto nur mit Aufgabenleserecht
  erreicht diesen Bereich nach der üblichen Freischaltung und Entsperrung.
- Mitglieder suchen, anrufen und bearbeiten (Stammdaten mit Titel, Mitgliedsart, Untereinheit,
  Beitrag, Austritt, Wiedereintritt, Ehrungen, Zusatzfelder, Bankverbindung) mit
  Bestätigung der Folgen und „Rückgängig“; neue Mitglieder aufnehmen, mit Dublettenprüfung
- Organisationsmitglieder: Filter „Personen/Organisationen“ in der Liste, Akte mit Rechtsform,
  Ansprechpartnern (anrufen, mailen) und Rechnungsanschrift; Organisationen aufnehmen
  (Ansprechpartner pflegen im Browser)
- Änderungsmeldungen und Fotos freigeben, Mitgliedsausweis per QR-Code prüfen
- Termine mit Zu- und Absagen, Rückmeldung für Mitglieder eintragen, einfache Termine
  anlegen und absagen
- Wettbewerbe: Standmodus eingebettet in der App, auch ohne Empfang, mit QR-Code für weitere
  Geräte
- Geschäftspartner nachschlagen (Suche, Akte, anrufen, mailen; nur lesen)
- Haushalt und Firma in der Akte, Qualifikationen (was bald abläuft) und Fristenkalender (nur lesen)
- Kassenbuch mit Belegfoto (ohne Netz als verschlüsselter Entwurf)
- offene Rechnungen suchen, Barzahlung verbuchen, Quittung als PDF teilen
- Quittung mit Unterschrift auf dem Display (Gegenseite im Vollbild, ohne Netz speicherbar,
  Belegnummer aus dem Kassenbuch)

Wie sich Ich, Noten, Aufgaben, Helferdienste, Mitteilungen, Standmodus, Unterschrift und Benachrichtigungen im
Einzelnen verhalten (mit englischer Kurzfassung): [docs/bedienung.md](docs/bedienung.md).

## Verbinden

Im Browser unter **Mein Konto › App & andere Instanzen › App auf dem Telefon** auf
„App verbinden“ tippen und den QR-Code in der App scannen; im Browser des Telefons selbst
führt „In der HUBIplan-App öffnen“ direkt in die App. Alternativ mit E-Mail, Passwort und ggf.
Zwei-Faktor-Code anmelden. Die App erhält einen eigenen Geräteschlüssel, der nur
für den Mitgliederbereich gilt und sich jederzeit unter „Mein Konto“ abmelden lässt.
Passwort vergessen: Adresse des Vereins eintragen, dann „Passwort vergessen? Im Browser
zurücksetzen“ ([Einzelheiten](docs/bedienung.md#passwort-vergessen)).

## Varianten

| Variante | Vertrieb | Push |
| --- | --- | --- |
| `foss` | vorgesehen für F-Droid, Google Play und als APK zum Herunterladen | UnifiedPush oder Abruf; **ohne Google-Dienste** (kein Firebase, keine Play-Dienste) |
| `play` | derzeit nicht veröffentlicht | zusätzlich Firebase Cloud Messaging über das Push-Gateway von HUBIplan |

`scripts/test.sh` prüft bei jedem Lauf, dass `foss` weder Firebase noch Play-Dienste enthält.
Freie Bibliotheken, die von Google stammen (z. B. AndroidX, ZXing), sind erlaubt; sie stehen
unter der Apache-Lizenz. Für die Google-Bibliotheken in `play` gilt eine
[Zusatzerlaubnis](#zusätzliche-erlaubnis-für-google-bibliotheken-agpl--7).

Die App zeigt unter „Lizenzen“ (Startseite und Konto) den AGPL-Text, den Link zum Quellcode
und alle enthaltenen Bibliotheken mit Lizenztext; die Liste entsteht beim Bauen aus dem
Abhängigkeitsgraphen der Variante. Einzelheiten: [docs/lizenzen.md](docs/lizenzen.md).

## Technik

Kotlin, Jetpack Compose mit Material 3, OkHttp, kotlinx.serialization, WorkManager,
DataStore, ZXing (QR-Codes), AndroidX Biometric. Gebaut mit Gradle und dem Android Gradle
Plugin 9; Ziel- und Kompilierversion SDK 37, Mindestversion SDK 28 (Android 9).

```text
app/src/main/java/de/hubiplan/app/
  data/       Schnittstelle zu HUBIplan, Konten, Geräteschlüssel
  ui/         Oberfläche (Compose), Reiter und Verwaltung
  calendar/   Gerätekalender und Erinnerungen
  push/       UnifiedPush und Web-Push-Entschlüsselung
  sync/       Abruf im Hintergrund
  photo/      Kamera und Fotos
  shortcut/   Verknüpfung mit Vereinswappen auf dem Startbildschirm
app/src/foss/, app/src/play/   Unterschiede der Varianten
app/src/main/assets/texte/     englische Texte (en.json)
app/src/test/                  Unit-Tests
fastlane/metadata/             Texte und Bilder für F-Droid und Play
docs/                          Bedienung im Einzelnen, Geräteprüfungen, Lizenzen, Veröffentlichen
```

**Sprache:** Deutsch oder Englisch. Wie im Browser ist der deutsche Text der Schlüssel:
`tr("Erneut laden")`, Platzhalter `tr("Mitglied seit {jahr}", "jahr" to j)`, Mehrzahl mit
`trn(…)` (`Texte.kt`). Die englischen Texte stehen in `app/src/main/assets/texte/en.json`;
`TexteTest` schlägt fehl, solange einer fehlt. Es gilt die in den Android-Einstellungen gewählte
App-Sprache (ab Android 13), sonst die Sprache des zuerst verbundenen Kontos (änderbar unter
Mein Konto › Sprache, wenn der Server Englisch freigeschaltet hat), sonst Deutsch.

Die Schnittstelle zu HUBIplan (`/api/app/v1/`) beschreibt
[docs/app.md](https://github.com/hubiplan/hubiplan/blob/main/docs/app.md) in hubiplan.

## Bauen und testen

Voraussetzungen: JDK 17 oder neuer, Android-SDK (Plattform 37), `ANDROID_HOME` gesetzt
(oder Android Studio an der Standardstelle).

```bash
scripts/test.sh                                  # Unit-Tests, Lint, Debug-APKs, Prüfung „foss ohne Google“
./gradlew assembleFossDebug                      # nur die Debug-APK der Variante foss
./gradlew assembleFossRelease bundlePlayRelease  # Veröffentlichungsfassungen
```

`scripts/test.sh` läuft unter Linux, WSL und Git Bash unter Windows. Damit sich mehrere
Testläufe auf einem Rechner nicht ausbremsen, holt es sich einen Testplatz mit `testplatz`
(aus [hubiplan/scripts/testplatz.sh](https://github.com/hubiplan/hubiplan/blob/main/scripts/testplatz.sh),
Installation steht im Kopf der Datei).

Gebaut und getestet wird ausschließlich lokal und nur über `scripts/test.sh`. Wie viele
Testläufe gleichzeitig laufen und wie viele Kerne jeder bekommt, stellt jeder Rechner selbst
ein (siehe [hubiplan/docs/testserver.md](https://github.com/hubiplan/hubiplan/blob/main/docs/testserver.md)).
Temporäre Dateien gehören in ein lokal konfiguriertes Temp-Verzeichnis.

Gradle prüft jede heruntergeladene Abhängigkeit und jedes Plugin gegen die SHA-256-Prüfsummen in
`gradle/verification-metadata.xml` und bricht bei Abweichung ab. Wer Abhängigkeiten, Plugins oder
die Gradle-Fassung ändert, ergänzt die Prüfsummen mit `scripts/pruefsummen.sh` (läuft wie
`scripts/test.sh` über einen Testplatz), sieht die neuen Einträge durch und checkt die Datei mit
ein; `scripts/pruefsummen.sh --neu` baut sie neu auf. Plattformabhängige Dateien (aapt2 für Linux,
macOS und Windows) trägt das Skript für alle Betriebssysteme ein, damit der Bau überall gelingt.

Nur beim Herausgeber, nie im Repository:

- `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`): Signatur
  der Veröffentlichung. Ohne die Datei bleibt `release` unsigniert (F-Droid signiert selbst).
- `firebase.properties` (`app_id`, `project_id`, `api_key`, `sender_id`): nur für `play`.
  Ohne die Datei hat `play` keinen Google-Push.

Prüfungen auf dem Emulator (Bedienungshilfen, TalkBack, Unterbrechungen):
[docs/geraetepruefungen.md](docs/geraetepruefungen.md).

## Veröffentlichen

Herausgeber ist Philipsen EDV. F-Droid, Google Play, APK zum Herunterladen und ein
Textbaustein für die Datenschutzhinweise des Vereins:
[docs/veroeffentlichen.md](docs/veroeffentlichen.md).

## Mitwirken

Fehlermeldungen, Ideen und Pull Requests sind willkommen: [CONTRIBUTING.md](CONTRIBUTING.md).
Bitte nur erfundene Daten in Code, Tests und Bildschirmfotos, und keine proprietären
Google-Dienste (Firebase, Play-Dienste) in der Variante `foss`.

## Sicherheit

Sicherheitslücken bitte **nicht** als öffentliches Issue melden, sondern vertraulich über
[GitHub](https://github.com/hubiplan/hubiplan-android/security/advisories/new). Einzelheiten:
[SECURITY.md](SECURITY.md).

## Lizenz

Copyright © 2026 Ralf Philipsen (Philipsen EDV)

Die HUBIplan-App ist freie Software: Sie dürfen sie unter den Bedingungen der **GNU Affero
General Public License**, wie von der Free Software Foundation veröffentlicht, weitergeben
und/oder ändern, entweder gemäß Version 3 der Lizenz oder (nach Ihrer Wahl) jeder späteren
Version (`AGPL-3.0-or-later`), wie HUBIplan selbst.

Die App wird in der Hoffnung verbreitet, dass sie nützlich ist, aber **ohne jede
Gewährleistung**, sogar ohne die implizite Gewährleistung der Marktreife oder der Eignung
für einen bestimmten Zweck. Einzelheiten stehen in der Lizenz. Den vollständigen
Lizenztext (englisch, maßgeblich) enthält die Datei [LICENSE](LICENSE).

### Zusätzliche Erlaubnis für Google-Bibliotheken (AGPL § 7)

Die Variante `play` bindet Bibliotheken von Google ein (Google Play-Dienste, Firebase), von
denen einige nur unter Googles Android-SDK-Lizenz stehen. Damit auch diese Variante
weitergegeben werden darf, erteilt der Rechteinhaber folgende Zusatzerlaubnis. Maßgeblich ist
der englische Wortlaut:

> **Additional permission under GNU AGPL version 3 section 7**
>
> If you modify this Program, or any covered work, by linking or combining it with the
> Google Play services libraries or the Firebase libraries (or modified versions of those
> libraries), containing parts covered by the terms of the Android Software Development Kit
> License Agreement, the licensors of this Program grant you additional permission to convey
> the resulting work. Corresponding Source for a non-source form of such a combination need
> not include the source code for the parts of those libraries used, but shall include the
> source code of the covered work.

Der letzte Satz ist seit 10.11.2026 klarer gefasst; die frühere Fassung („shall not include
… as well as that of the covered work“) ließ sich so lesen, als sei auch der Code der App
ausgenommen. Gemeint war immer, was hier steht (Lizenzaudit 10.11.2026, L03).

Die Erlaubnis gilt nur für diese Google-Bibliotheken. Für den eigenen Code der App bleibt
alles wie in der AGPL: Wer die App weitergibt, muss ihren Quellcode anbieten. Wer die App
verändert, darf diese Zusatzerlaubnis für seine Fassung beibehalten oder streichen
(AGPL § 7 Absatz 3).
