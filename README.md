# HUBIplan-App für Android

Begleit-App zu [HUBIplan](https://github.com/DeltaRP/hubiplan), der Vereinsverwaltung.
Sie ergänzt den Mitgliederbereich im Browser:

- Termine der eigenen Einheiten (Verein, Zug, weitergegebene Termine des Dachverbands)
- Zu- und Absage mit einem Fingertipp
- Mitteilungen und Rundschreiben lesen
- Farben und Wappen des Vereins aus HUBIplan
- Ansicht für Telefon und Tablet (Liste und Detail nebeneinander)
- Termine im Gerätekalender mit Erinnerungen, Hinweis „Rückmeldung fehlt“
- Mitteilungen des Vorstands sofort per Push über eine UnifiedPush-App (z. B. ntfy), ohne
  Google; der Inhalt ist Ende-zu-Ende verschlüsselt (Web-Push, RFC 8291). Die Play-Fassung
  nutzt ohne UnifiedPush-App Google (FCM) über das Push-Gateway; auch dann sieht Google nur
  verschlüsselte Daten. Ohne Push holt die App neue Mitteilungen alle paar Stunden ab.
- Vorstand und Zugführung: Kurzmitteilung an alle Mitglieder der Einheit senden, mit
  Empfängerzahl vorab
- Eigenes Profil ansehen, Änderungen der Kontaktdaten melden (der Verein prüft sie)
- Mitgliederfoto mit der Kamera aufnehmen oder aus der Galerie wählen; mit Einwilligung,
  der Verein gibt es frei. Foto und Einwilligung lassen sich in der App widerrufen.
- Mehrere Konten, wenn du in Vereinen mit verschiedenen HUBIplan-Adressen bist (Wechsel
  unter „Konto“); jedes Konto mit eigenem Geräteschlüssel, eigenen Kalendern und eigenem Push

## Verbinden

Im Browser unter **Mein Konto › App auf dem Telefon** auf „App verbinden“ tippen und den
QR-Code in der App scannen. Alternativ mit E-Mail, Passwort und ggf. Zwei-Faktor-Code anmelden.
Die App erhält einen eigenen Geräteschlüssel, der nur für den Mitgliederbereich gilt und
sich jederzeit unter „Mein Konto“ abmelden lässt.

## Bauen

Voraussetzungen: JDK 17 oder neuer (in WSL Debian: 21), Android-SDK (Plattform 35), `ANDROID_HOME` gesetzt.

```
scripts/test.sh            # Unit-Tests, Lint, Debug-APKs; prüft „foss ohne Google“
./gradlew assembleFossRelease bundlePlayRelease
```

Varianten: `foss` (F-Droid, APK, ohne Google-Bibliotheken) und `play` (Google Play, zusätzlich
Firebase Cloud Messaging). Mindestversion Android 8.0.

Nur auf dem Rechner des Herausgebers, nie im Repo:

- `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`): Signatur
  der Veröffentlichung. Ohne die Datei bleibt `release` unsigniert (F-Droid signiert selbst).
- `firebase.properties` (`app_id`, `project_id`, `api_key`, `sender_id` aus dem
  Firebase-Projekt): nur für `play`. Ohne die Datei hat `play` keinen Google-Push.

## Veröffentlichen

Siehe [docs/veroeffentlichen.md](docs/veroeffentlichen.md): F-Droid, Google Play, APK zum
Herunterladen und der Textbaustein für die Datenschutzhinweise des Vereins.

## Lizenz

GNU Affero General Public License 3.0 oder neuer (`AGPL-3.0-or-later`), siehe `LICENSE`.
