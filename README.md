# HUBIplan-App für Android

Begleit-App zu [HUBIplan](https://github.com/DeltaRP/hubiplan), der Vereinsverwaltung.
Sie ergänzt den Mitgliederbereich im Browser:

- Termine der eigenen Einheiten (Verein, Zug, weitergegebene Termine des Dachverbands)
- Zu- und Absage mit einem Fingertipp
- Mitteilungen und Rundschreiben lesen
- Farben und Wappen des Vereins aus HUBIplan
- Ansicht für Telefon und Tablet (Liste und Detail nebeneinander)

Geplant: Termine im Gerätekalender mit Erinnerungen, Push-Nachrichten des Vorstands
(ohne Google über UnifiedPush, in der Play-Fassung verschlüsselt über Google),
eigenes Profil und Mitgliederfoto.

## Verbinden

Im Browser unter **Mein Konto › App auf dem Telefon** auf „App verbinden“ tippen und den
QR-Code in der App scannen. Alternativ mit E-Mail, Passwort und ggf. Zwei-Faktor-Code anmelden.
Die App erhält einen eigenen Geräteschlüssel, der nur für den Mitgliederbereich gilt und
sich jederzeit unter „Mein Konto“ abmelden lässt.

## Bauen

Voraussetzungen: JDK 17, Android-SDK (Plattform 35), `ANDROID_HOME` gesetzt.

```
scripts/test.sh            # Unit-Tests und Debug-APK (Variante foss)
./gradlew assembleFossRelease
```

Varianten: `foss` (F-Droid, APK, ohne Google-Bibliotheken) und `play` (Google Play).
Mindestversion Android 8.0.

## Lizenz

GNU Affero General Public License 3.0 oder neuer (`AGPL-3.0-or-later`), siehe `LICENSE`.
