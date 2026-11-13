# Bedienung im Einzelnen

Ergänzung zur Übersicht im [README](../README.md): wie sich einzelne Bereiche der App verhalten.
Jeder Abschnitt hat am Ende eine englische Kurzfassung.

## Passwort vergessen

Trage unter „Oder mit E-Mail und Passwort anmelden“ zuerst die gültige Adresse deines Vereins
ein. „Passwort vergessen? Im Browser zurücksetzen“ öffnet die vorhandene
Passwortwiederherstellung genau dieser Instanz. E-Mail und Passwort werden nicht im
Browserlink mitgegeben. Setze das Passwort dort zurück, kehre zur App zurück und melde dich
mit dem neuen Passwort an. Ist kein Browser verfügbar, zeigt die App die kopierbare
Zieladresse. Fehlt die Adresse oder ist sie ungültig bzw. unverschlüsseltes HTTP, bleibt der
Knopf trotzdem antippbar: Die App springt dann ins Adressfeld und sagt dort, was fehlt. Genauso
zeigt „Anmelden“ bei leeren Feldern einen Hinweis am Feld. Nur während der Anmeldung sind beide
Knöpfe gesperrt.

**English:** Under “Or sign in with email and password”, first enter your club’s valid
address. “Forgot password? Reset in browser” opens that instance’s existing password recovery
page without adding your email or password to the link. Reset the password there, return to
the app and sign in with the new password. If no browser is available, the app displays a
selectable destination address. If the address is missing, invalid or plain HTTP, the button
still responds: the app moves to the address field and explains what is missing there. “Sign
in” likewise shows a hint next to empty fields. Both buttons are only disabled while signing in.

## Navigation

Die Leiste unten (auf dem Tablet seitlich) hat kurze, einzeilige Beschriftungen: **Termine**,
**Aufgaben**, **Neues** (die Mitteilungen), **Ich** und **Verwalten**. Bei großer Schrift
werden sie bei Bedarf etwas kleiner statt umzubrechen. Der aktive Reiter ist farbig hinterlegt.
In jeder Detailansicht (Termin, Mitteilung, Aufgabe, Bereiche der Verwaltung) stehen der Pfeil
zurück und der Titel oben in der Kopfleiste; System-Zurück führt ebenfalls eine Ebene zurück.
Musiker sehen zusätzlich **Noten** (siehe unten). Werden es auf dem Telefon dadurch sechs Ziele,
steht **Verwaltung** als Symbol oben in der Kopfleiste neben „Mein Konto“; auf dem Tablet bleiben
alle sechs in der seitlichen Leiste.

**English:** The bar at the bottom (at the side on tablets) has short single-line labels:
**Events**, **Tasks**, **Messages**, **Me** and **Admin**. With large text they shrink a little
instead of wrapping. The active tab is highlighted in colour. Every detail view (event, message,
task, admin areas) shows the back arrow and title in the top bar; system back also goes up one
level. Musicians also see **Sheet music** (see below). If that makes six targets on a phone,
**Admin** moves to an icon in the top bar next to “My account”; tablets keep all six in the side bar.

## Reiter „Ich“

Unter **Ich** steht alles Persönliche, gegliedert wie „Mein Bereich“ im Browser (Kopf: „Mein
Bereich“). Ganz oben der **Mitgliedsausweis**; Tippen zeigt ihn groß, ohne Netz den zuletzt
gespeicherten. Lange auf das App-Symbol drücken bietet „Ausweis zeigen“, das ihn direkt groß
öffnet. Darunter eine Liste mit Kurzstand rechts und der Zahl offener Punkte:

- **Meine Daten**: Anschrift, Telefon, Foto, „Änderung melden“.
- **Rechnungen**: offene zuerst mit Betrag und Fälligkeit, „PDF ansehen“, darunter Beiträge über
  eine Sammelrechnung und das Lastschriftmandat (nur ansehen; erteilen, ändern und widerrufen im
  Browser). PDFs öffnet die App nur online; sie liegen nur kurz im Cache und werden beim nächsten
  Öffnen, spätestens nach etwa einer Stunde, beim Start und beim Abmelden gelöscht.
- **Versammlungen**: Einladungen mit Ort, Zeit und Tagesordnung, „Antrag stellen“ bis zur Frist,
  eigene Anträge mit Stand und „Antrag zurückziehen“, darunter freigegebene Protokolle als PDF.
  Läuft eine Versammlung, meldet „QR-Code scannen: Ich bin da“ mit dem wechselnden Code vom
  Bildschirm der Leitung an; erlaubt der Verein die Teilnahme aus der Ferne, geht das auch mit
  „Online teilnehmen“. In der Versammlungsansicht stimmt das Mitglied bei laufenden Abstimmungen
  ab (für jede Vollmacht eine eigene Stimme, nicht änderbar), sieht danach das Ergebnis und meldet
  sich mit „Ich gehe“ ab. Die Ansicht fragt alle vier Sekunden nach (HUBIplan ab Versammlungen V7).
- **Meine Ergebnisse**: Plätze, Werte, Titel und Ranglisten.
- **Helferdienste**: führt in den Reiter „Aufgaben“ zu den Helferdiensten.
- **Dokumente**: freigegebene Dokumente des Vereins, nur lesen.

Es erscheinen nur Punkte, deren Modul der Verein eingeschaltet hat. Pfeil oder System-Zurück
führen aus einer Unterseite zurück zur Liste.

**English:** **Me** holds everything personal, organised like “My area” in the browser. Your
**membership card** is at the top; tap it to show it large, offline the last saved one.
Long-pressing the app icon offers “Show ID card”, which opens it large straight away. Below is a
list with a short status on the right and the number of open items: **My data** (address, phone,
photo, “Report change”), **Invoices** (open ones first with amount and due date, “View PDF”,
fees via a collective invoice and your direct debit mandate, view only), **Meetings**
(invitations with agenda, “Submit a motion” until the deadline, your motions with status and
“Withdraw motion”, released minutes as PDF), **My results**, **Volunteer duties** (opens them in
the “Tasks” tab) and **Documents** (read only). PDFs open only online and are kept in the cache
only briefly. Only items whose module is switched on appear.

## Noten und Notenpult

Für Musiker (Modul Musik und Notenarchiv an, aktives Musikerprofil) gibt es den Bereich **Noten**:
oben Favoriten und zuletzt gespielte Stücke, darunter die festen **Notenmappen** des Vereins
(Marschbuch, Repertoire …) und die eigenen **Proben und Auftritte** in Programmreihenfolge. Die
App zeigt nur die Dateien der eigenen Stimme, so wie der Server sie freigibt. Das Suchfeld findet
Stücke über alle Mappen nach Titel, Komponist oder Nummer, ohne Netz, Umlaute tolerant und nach
Wortanfängen („alte kam“ findet „Alte Kameraden“).

- **Für unterwegs laden** legt alle Noten einer Mappe verschlüsselt auf das Gerät (nicht in
  Downloads oder Galerie). Feste Mappen bleiben dann dauerhaft dort; Proben und Auftritte lädt die
  App am Vortag über WLAN selbst (abschaltbar). Was der Server nicht mehr liefert (Stimme entfernt,
  Freigabe entzogen, Termin abgesagt oder älter als 7 Tage), löscht der nächste Abgleich; beim
  Abmelden ist alles weg.
- **Spielen** öffnet das **Notenpult** im Vollbild. Der Bildschirm bleibt an. Blättern: Tippen
  (linkes Drittel zurück, sonst vor), Wischen, Lautstärketasten (leiser = vor) oder ein
  Bluetooth-Fußpedal (Pfeiltasten, Bild auf/ab, Leertaste). Am Ende eines Stücks geht es ins
  nächste. Langes Drücken des Pedals öffnet die Schnellwahl.
- Tipp in die Bildmitte zeigt die Kopfzeile: Stück, Seite, Uhr, Akku, **Stück wählen**
  (Nummernfeld, Suche, Liste, „Zurück zu …“), **Sperren** (Tippen und Wischen aus, lang drücken
  entsperrt), **Anhören** (Hörbeispiel oder aus MusicXML gesetztes MIDI, Tempo 50–100 %),
  Transponieren bei MusicXML-Noten und Einstellungen: Nachtmodus, volle Helligkeit, Ränder
  abschneiden, halbe Seite wenden, zwei Seiten nebeneinander (Tablet quer), Drehung sperren,
  Blätterwege, Lautstärketasten vertauschen.
- **Leitung:** Wer Musik bearbeiten darf oder für die Mappe bzw. den Termin als Leitung
  eingetragen ist, sieht **An alle senden**. Alle Geräte mit derselben Mappe zeigen dann
  „Dirigent: Nr. 27 · Alte Kameraden“ und springen nach 3 Sekunden dorthin, außer man tippt
  **Bleiben** (auf „nur anzeigen“ umstellbar).
- Während das Notenpult offen ist, meldet sich HUBIplan nur still. „Bitte nicht stören“ für andere
  Apps schaltet man selbst ein; die App bietet einen Knopf zu den Einstellungen.

**English:** Musicians (music and score modules on, active musician profile) get **Sheet music**:
favourites and recently played pieces, the club's fixed **music folders** (march book,
repertoire …) and their own **rehearsals and performances** in programme order, always only the
files of their own part. Search works across all folders, offline, by title, composer or number.
**Download for offline use** stores a folder encrypted on the device; rehearsals and performances
are fetched the day before over Wi-Fi. Anything the server no longer delivers is deleted at the
next sync, and everything on logout. **Play** opens the full-screen **music stand**: the screen
stays on; turn pages by tapping, swiping, volume keys or a Bluetooth foot pedal; the next piece
follows at the end. Tap the middle for the header with **Choose piece** (number pad, search),
**Lock**, **Listen**, transposition for MusicXML and display settings. Leaders can **Send to
everyone**; other devices jump to the piece after 3 seconds unless you tap **Stay**.

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

Der Eingang **Mitteilungen** (in der Leiste unten „Neues“) enthält dieselben empfangenen Schreiben wie **Mein Bereich ›
Mitteilungen** im Web. Absätze, fett/kursiv, Listen und sichere Web-/E-Mail-Links werden nativ
dargestellt. Die Dateinamen der Anhänge stehen beim Schreiben. **Dieses Schreiben mit Anhängen
im Browser öffnen** führt zum ausgewählten Schreiben und Mitgliederzugang. Eine nötige
Browseranmeldung bleibt unabhängig von der App und führt danach zum Ziel zurück. Fehlt ein
Browser, lässt sich die angezeigte Zieladresse kopieren.

Terminantworten und Aufgabenaktionen bestätigen einen erfolgreichen Serverabschluss als
höfliche Statusmeldung. Fehler entfernen vorherige Erfolge und bleiben mit Korrektur- oder
Wiederholungsmöglichkeit sichtbar.

Im Termin stehen Ort, Anfahrt und der Knopf **Navigation starten** (öffnet die Navigations-App
des Telefons), dazu **Route in OpenStreetMap** und **Route in Google Maps**, wenn der Verein den
Ort angegeben hat. Anhänge des Termins öffnen sich direkt in der App. Bis zum Anmeldeschluss
lässt sich die Rückmeldung jederzeit ändern; erlaubt der Termin es, gibt es **Vielleicht** und
eine Bemerkung (auf Wunsch mit eigener Frage). Einladungen und Rundschreiben zu einem Termin
kommen als Benachrichtigung mit den Knöpfen Zusage, Absage und gegebenenfalls Vielleicht.

**English:** The event shows place, directions and **Start navigation**, plus route links to
OpenStreetMap and Google Maps. Event attachments open in the app. Replies can be changed until
the deadline; where allowed there is **Maybe** and a comment. Invitations and circulars about an
event arrive as notifications with accept, decline and, if allowed, maybe buttons.

**English:** **Messages** shows the same received circulars as the member area on the web,
with native formatting. Attachments open in the browser for the selected message and
membership.

## Aktualisieren, Fehler und ohne Netz

Termine, Mitteilungen, Aufgaben, Helferdienste und die Listen der Verwaltung lädst du neu, indem
du die Liste nach unten ziehst. Über der Liste steht, von wann der Stand ist („Stand: 14:32 Uhr“);
mit TalkBack bietet diese Zeile die Aktion „Aktualisieren“ an. Beim Aktualisieren bleibt die
bisherige Liste sichtbar. Ohne Netz erscheint ein grauer Hinweis mit Wolke, z. B. „Offline – du
siehst den Stand von 14:32 Uhr.“, mit „Erneut laden“. Andere Fehler stehen in einer roten Fläche
in Alltagssprache („HUBIplan antwortet gerade nicht.“); den Statuscode des Servers zeigt
„Details“.

Den Vorschlag „Auf den Startbildschirm legen?“ zeigt die App nur in der Terminliste und nur
beim ersten Mal; später geht es über „Mein Konto“.

Hat das Telefon keine Bildschirmsperre, führt in der Verwaltung der Knopf „Bildschirmsperre
einrichten“ direkt in die Systemeinstellung. Danach prüft die App beim Zurückkehren von selbst
erneut. In „Zu erledigen“ heißen gemeldete Änderungen der Mitglieder „Datenänderungen“; das
Verteilen von Aufgaben heißt „Aufgaben verteilen“, damit es sich vom Reiter „Aufgaben“ (deine
eigenen) unterscheidet. Unter „Meine Ergebnisse“ steht bei jedem Wert die Einheit („27 Ringe“),
Telefonnummer und E-Mail-Adresse unter „Meine Daten“ lassen sich antippen.

**English:** Pull a list down to reload events, messages, tasks, helper duties and the admin
lists. A line above the list shows when it was loaded (“As of 14:32”); with TalkBack this line
offers the action “Refresh”. The previous list stays visible while reloading. Without a
connection a grey banner with a cloud appears, e.g. “Offline – you are seeing the data from
14:32.”, with “Reload”. Other errors appear in a red box in plain language (“HUBIplan is not
responding right now.”); “Details” shows the server status code. The suggestion “Add to home
screen?” appears only in the event list and only the first time; later use “My account”. If the
phone has no screen lock, the admin area offers “Set up screen lock”, which opens the system
setting; the app checks again when you return. Member-reported changes are called “Data
changes”, and distributing tasks is “Assign tasks”, distinct from the “Tasks” tab. Every value
under “My results” has its unit (“27 rings”); phone number and email under “My data” can be tapped.

## Einheit in der Verwaltung

Wer mehrere Einheiten verwaltet, wählt sie oben in der **Verwaltung**. Die App merkt sich je
Konto die zuletzt gewählte Einheit und öffnet die Verwaltung beim nächsten Mal dort. Gibt es
die Einheit nicht mehr oder fehlen die Rechte, startet sie in der ersten. Die Auswahl bleibt
nur auf dem Gerät und wird beim Abmelden gelöscht.

**English:** If you manage several units, choose one at the top of **Administration**. The
app remembers the unit you chose last for each account and opens Administration there next
time. If that unit is gone or you lack the rights, it starts with the first one. The choice
stays on the device and is deleted when you sign out.

## Aufgaben in der Verwaltung

Unter **Verwaltung › Aufgaben verteilen** wählst du die Einheit oder Abteilung und „Offen“ oder
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

## Geschäftspartner nachschlagen

Wer im Verein das Recht „Geschäftspartner“ hat (lesen oder bearbeiten), findet in der
Verwaltung den Bereich „Geschäftspartner“. Die Suche findet Name, Ort und Ansprechpartner. Die
Akte zeigt Rollen, Telefon, E-Mail und Anschrift des Partners und seine Ansprechpartner; Tippen
auf Telefon oder E-Mail ruft an bzw. öffnet das Mailprogramm. Anlegen und ändern geht nur im
Browser. Braucht HUBIplan ab der Fassung mit Geschäftspartnern (November 2026).

**English:** Whoever has the right “Business partners” in the club (view or edit) finds
the area “Business partners” in Admin. The search finds name, town and contact persons. The
record shows the partner's roles, phone, e-mail and address and its contact persons; tapping
the phone or e-mail calls or opens the mail program. Creating and changing is only possible in
the browser. Needs a HUBIplan version with business partners (November 2026).

## Schießen auswerten (Standmodus)

Wer „Ergebnisse erfassen“ oder „Wettbewerbe verwalten“ darf, sieht in der Verwaltung den
Bereich „Wettbewerbe“ (nur, wenn das Modul eingeschaltet ist). Er listet die eigenen, noch
nicht abgeschlossenen Wettbewerbe, heute zuerst.

- „Standmodus öffnen“ legt einen Standzugang bis zum Ende des Wettbewerbstags an und zeigt den
  Standmodus von HUBIplan im Vollbild in der App. Öffnet man denselben Wettbewerb erneut, nimmt
  die App denselben Zugang, damit offline erfasste Eingaben weiter übertragen werden.
- Der Standmodus arbeitet mit dem Standzugang, nicht mit dem Vorstandsschlüssel; die Sperre der
  Verwaltung nach 5 Minuten Pause schließt ihn deshalb nicht.
- Die eingebettete Ansicht lädt nur Seiten der eigenen Instanz; andere Links öffnet der Browser.
  Kamera (Scheibenfoto, Live-Kamera) gibt die App nach Rückfrage frei, Fotos bleiben nur kurz im
  Cache. Der Bildschirm bleibt an.
- Zurück oder ✕ fragen nach, wenn noch Eingaben auf die Übertragung warten.
- „QR-Code für weiteres Gerät“ zeigt einen neuen Standzugang als QR-Code; „Stand-QR scannen“
  (auch „Ausweis prüfen“ erkennt ihn) öffnet einen solchen Code im Standmodus der App.
- „Standzugang beenden“ beendet alle aktiven Standzugänge des Wettbewerbs.

Unter „Ich › Meine Ergebnisse“ zeigt „Ranglisten“ die Wettbewerbe so, wie die Webseite des Vereins sie zeigt
(nur veröffentlichte, Preisplätze oder ganze Rangliste). Laufende Wettbewerbe fragen alle 15
Sekunden nach einem neuen Stand.

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
