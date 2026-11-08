# Bedienung im Einzelnen

Ergänzung zur Übersicht im [README](../README.md): wie sich einzelne Bereiche der App verhalten.
Jeder Abschnitt hat am Ende eine englische Kurzfassung.

## Passwort vergessen

Trage unter „Oder mit E-Mail und Passwort anmelden“ zuerst die gültige Adresse deines Vereins
ein. „Passwort vergessen? Im Browser zurücksetzen“ öffnet die vorhandene
Passwortwiederherstellung genau dieser Instanz. E-Mail und Passwort werden nicht im
Browserlink mitgegeben. Setze das Passwort dort zurück, kehre zur App zurück und melde dich
mit dem neuen Passwort an. Ist kein Browser verfügbar, zeigt die App die kopierbare
Zieladresse. Bei einer leeren, ungültigen oder unverschlüsselten HTTP-Adresse bleibt die
Aktion deaktiviert; während der Anmeldung ebenfalls.

**English:** Under “Or sign in with email and password”, first enter your club’s valid
address. “Forgot password? Reset in browser” opens that instance’s existing password recovery
page without adding your email or password to the link. Reset the password there, return to
the app and sign in with the new password. If no browser is available, the app displays a
selectable destination address. The action is disabled for empty, invalid or unencrypted HTTP
addresses, and while signing in.

## Aufgaben im Mitgliederbereich

Der Bereich **Aufgaben** zeigt ausdrücklich zugewiesene Aufgaben und offene Angebote deiner
aktuell gewählten Mitgliedschaft. Du kannst ein Angebot übernehmen und, soweit freigegeben,
erledigen oder wieder öffnen. Eine noch offene Übernahme kannst du zurückgeben. Dafür ist kein
Vorstandszugang nötig; Verwaltungsrechte werden dadurch nicht erteilt. Anhänge öffnest du über
den Browser im selben Mitgliederbereich.

Aufgabenhinweise öffnen genau das betroffene Konto und den freigegebenen Mitgliedszugang. Ein
nicht mehr verfügbarer Zugang fällt nicht auf eine andere Mitgliedschaft oder die Verwaltung
zurück. Die App lädt die Berechtigung und den Aufgabenstand frisch; nach Fehlern werden alte
Inhalte verworfen. Aufgaben werden nur im Arbeitsspeicher gehalten. Auch gemeinsame
Instanzaufgaben lassen sich bei ausgefallener Quelle nicht scheinbar offline erledigen.
Instanzübergreifende Aufgaben gleicht der eigene HUBIplan-Server ab; die App verbindet sich
weiterhin nur mit der eigenen Instanz.

**English:** The **Tasks** tab lists tasks assigned to the selected membership and open
offers you can take on, complete or reopen where permitted. This grants no administration
rights. Task notifications open exactly the affected account and membership; content is
reloaded and kept in memory only.

## Helferdienste im Mitgliederbereich

Nutzt der Verein Helferdienste, schaltet der Bereich **Aufgaben** oben zwischen „Aufgaben“ und
„Helferdienste“ um; ohne Modul Aufgaben zeigt er nur die Helferdienste. Unter **Meine Dienste**
stehen die eigenen Schichten; bis zur Frist des Plans lassen sie sich mit **Dienst absagen**
zurückgeben, danach nur über die Verwaltung. Darunter zeigen die veröffentlichten Dienstpläne
freie Schichten mit Ort und Mindestalter; mit **Mich eintragen** trägst du dich ein, wenn der
Plan das Selbsteintragen erlaubt. Die Kalenderdatei „Meine Dienste“ gibt es über **Im Browser
öffnen**. Inhalte werden nur im Arbeitsspeicher gehalten.

**English:** If the club uses volunteer shifts, the **Tasks** tab switches between tasks and
shifts. **My shifts** lists your own shifts, which you can cancel until the plan’s deadline;
published plans show open shifts you can sign up for when self sign-up is allowed.

## Mitteilungen lesen und Anhänge öffnen

Der Eingang **Mitteilungen** enthält dieselben empfangenen Schreiben wie **Mein Bereich ›
Mitteilungen** im Web. Absätze, fett/kursiv, Listen und sichere Web-/E-Mail-Links werden nativ
dargestellt. Die Dateinamen der Anhänge stehen beim Schreiben. **Dieses Schreiben mit Anhängen
im Browser öffnen** führt zum ausgewählten Schreiben und Mitgliederzugang. Eine nötige
Browseranmeldung bleibt unabhängig von der App und führt danach zum Ziel zurück. Fehlt ein
Browser, lässt sich die angezeigte Zieladresse kopieren.

Terminantworten und Aufgabenaktionen bestätigen einen erfolgreichen Serverabschluss als
höfliche Statusmeldung. Fehler entfernen vorherige Erfolge und bleiben mit Korrektur- oder
Wiederholungsmöglichkeit sichtbar.

**English:** **Messages** shows the same received circulars as the member area on the web,
with native formatting. Attachments open in the browser for the selected message and
membership.

## Aufgaben in der Verwaltung

Unter **Verwaltung › Aufgaben** wählst du die Einheit oder Abteilung und „Offen“ oder
„Erledigt“. Eine neue Aufgabe ist zunächst privat. Eine Uhrzeit braucht ein Datum; die Frist
gilt in der angezeigten Zeitzone Europe/Berlin. Zum Teilen und für Anhänge, Serienregeln und
Verteilung über Ebenen dient die Aufgabenansicht im Browser. In der App lassen sich auch
freigegebene Aufgaben und lokale Ausgaben solcher Serien erledigen, soweit der Server es
erlaubt. Eine zentrale Vorlage für einzelne Zielkopien wird nicht als Ganzes erledigt.
Wiederöffnen kann bei einer bereits bearbeiteten Folgeausgabe abgelehnt werden; dann neu
laden. Bei einem Versionskonflikt wird keine Änderung erzwungen.

Push-Hinweise für Verwaltungsaufgaben öffnen die konkrete Aufgabe im zugehörigen Konto und
Bereich nach der Verwaltungsentsperrung. Die App lädt Inhalt und Rechte neu; alte Hinweise oder
entzogene Freigaben geben keinen Zugriff. Auf dem Sperrbildschirm steht standardmäßig nur ein
neutraler Hinweis. Titel und Versandkanäle kannst du unter „Mein Konto › Benachrichtigungen“
in HUBIplan einstellen, den Android-Kanal „Aufgaben“ in den Systemeinstellungen. Ohne Push
werden Aufgaben beim Öffnen oder Aktualisieren der Liste geladen. Aufgabeninhalte und Entwürfe
werden nicht lokal gespeichert. Vor dem Verlassen eines Formulars fragt die App nach; eine
Gerätesperre verwirft den privaten Entwurf. Nach einem Verbindungsabbruch beim Anlegen zuerst
die Liste aktualisieren, bevor du dieselbe Aufgabe erneut anlegst: Es kann sein, dass sie den
Server bereits erreicht hat.

**English:** In **Administration › Tasks**, choose a unit or department and view open or
completed tasks. Create a private task with an optional due date and time (Europe/Berlin), or
complete and reopen tasks when permitted. Sharing, attachments, recurrence rules and
distribution are managed in the browser. Push opens the exact task in the matching account
after administration is unlocked; current permissions are always checked by the server. Task
contents and drafts are kept only in memory. Leaving a form requires confirmation; locking
administration discards the draft. If creating a task loses connection, refresh the list
before trying again.

## Unterschrift auf dem Display

Beim Unterschreiben passen sich die Aktionsknöpfe dem verfügbaren Platz an. Bei großer Schrift
stehen sie bei Bedarf in mehreren Zeilen; die Beschriftungen bleiben zusammenhängend. Die
Hilfslinie bleibt oberhalb der Rollenbezeichnung. Du kannst weiterhin hochkant unterschreiben.

**English:** Signature actions adapt to the available space and move onto additional rows
with large text, keeping each label intact. The guide line stays above the role label. Signing
in portrait remains available.

## Benachrichtigungen und Kontowechsel

Benachrichtigungen gehören zu genau einer Server-/Geräteverbindung. Beim Abmelden oder
erneuten Verbinden werden ihre Hinweise entfernt; ein alter Hinweis öffnet kein neu belegtes
Konto. Beim ersten Start einer Fassung mit dieser Zuordnung werden ältere, noch nicht
zugeordnete HUBIplan-Hinweise einmal entfernt. Hinweise anderer Konten bleiben bei späteren
Abmeldungen erhalten. Eine neue Verbindung erhält eigene Einstellungen.

Verspätete Hintergrundantworten dürfen nach dem Abmelden oder erneuten Verbinden weder das
neue Konto entfernen noch Kalender, Hinweise oder den letzten Abgleich verändern. Lokale
Seiteneffekte werden mit dem Kontowechsel serialisiert; Netzwerkabrufe laufen außerhalb dieser
kurzen Sperren.

**English:** Notifications target a specific server/device identity and are cleared when that
connection is removed or replaced. Old notifications cannot open a reused account slot. Legacy
unassigned HUBIplan notifications are cleared once after upgrading. Later logout only clears
that account's notifications. A new connection starts with its own settings. Late background
responses cannot remove a replacement account or update its calendar, notifications or sync
timestamp. Local effects are serialized with account replacement; network calls run outside
those locks.
