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
  Google; der Inhalt ist Ende-zu-Ende verschlüsselt (Web-Push, RFC 8291). Ohne Push-App
  holt die App neue Mitteilungen alle paar Stunden ab.

- Vorstand und Zugführung: Kurzmitteilung an alle Mitglieder der Einheit senden, mit
  Empfängerzahl vorab

Geplant: eigenes Profil und Mitgliederfoto, Play-Fassung.

## Verbinden

Im Browser unter **Mein Konto › App auf dem Telefon** auf „App verbinden“ tippen und den
QR-Code in der App scannen. Alternativ mit E-Mail, Passwort und ggf. Zwei-Faktor-Code anmelden.
Die App erhält einen eigenen Geräteschlüssel, der nur für den Mitgliederbereich gilt und
sich jederzeit unter „Mein Konto“ abmelden lässt.

## Bauen

Voraussetzungen: JDK 17 oder neuer (in WSL Debian: 21), Android-SDK (Plattform 35), `ANDROID_HOME` gesetzt.

```
scripts/test.sh            # Unit-Tests und Debug-APK (Variante foss)
./gradlew assembleFossRelease
```

Varianten: `foss` (F-Droid, APK, ohne Google-Bibliotheken) und `play` (Google Play).
Mindestversion Android 8.0.

## Lizenz

GNU Affero General Public License 3.0 oder neuer (`AGPL-3.0-or-later`), siehe `LICENSE`.
