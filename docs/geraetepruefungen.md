# Geräteprüfungen: Bedienungshilfen und Unterbrechungen

Ergänzung zu „Bauen und testen“ im [README](../README.md). Diese Prüfungen laufen nur lokal
auf einem eigens angelegten Emulator.

`scripts/test.sh connectedFossDebugAndroidTest` prüft die produktiven Komponenten auf einem
lokalen Gerät. Die zusätzlichen PIN- und TalkBack-Prüfungen sind ausdrücklich freizuschalten
und sonst als übersprungen erkennbar:

```bash
scripts/test.sh connectedFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.fixturePin="$HUBIPLAN_FIXTURE_PIN" \
  -Pandroid.testInstrumentationRunnerArguments.fixtureTalkBack=1
```

Nur einen eigens dafür angelegten Emulator ohne vorhandene Konten oder Geräte-PIN verwenden.
`HUBIPLAN_FIXTURE_PIN` enthält eine ausschließlich synthetische sechsstellige Test-PIN. Die
PIN-Prüfungen setzen und entfernen diese Geräte-PIN. Der TalkBack-Test benötigt den
installierten und einmal initialisierten TalkBack-Dienst. Vorab den Benachrichtigungsdialog
des Dienstes abschließen, damit kein Systemdialog die Prüfansicht überdeckt. Die vorherige
Accessibility-Einstellung wird anschließend wiederhergestellt. Beide Prüfungen verlangen
zusätzlich Emulator-Hardware; persönliche Geräte und bestehende Testkonten sind dafür nicht
vorgesehen.

Die nicht exportierten Prüfansichten gehören ausschließlich zum Debug-Build. Die Buchungsform
nutzt echte Activity-Rotation, Systemschrift, IME und Geräteauthentifizierung, aber erfundene
Konten und einen synthetischen Transport. Die explizite Sperre ist kein Nachweis des
produktiven Fünf-Minuten-Timers. Native Accessibility-Aktionen mit aktivem TalkBack ersetzen
weder eine Hörprüfung noch Nutzertests.

Der zusätzliche `ZTalkBackSpeechTest` protokolliert auf einem ausdrücklich freigegebenen,
eigenen Emulator den echten TalkBack-Ausgabetext an eine stumme Prüfsynthese. Der nur im
Debug-Build vorhandene TTS-Dienst ist standardmäßig deaktiviert; der Test aktiviert ihn
vorübergehend und stellt die vorherigen Einstellungen wieder her. Das prüft die tatsächliche
Textübergabe, ersetzt aber weder eine Hörprüfung noch Tests mit Betroffenen.
