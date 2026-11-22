# Gestaltung „Frisch mobil“

Die Android- und die iOS-App teilen sich Werte, Schrift und Symbole, damit beide gleich aussehen
und eine Anleitung für beide gilt. Die Bedienung bleibt die des Systems (Material 3).

## Gemeinsame Dateien

| Datei | Inhalt |
|---|---|
| `app/src/main/assets/design/werte.json` | neutrale Farben (hell, dunkel), Zustandsfarben, Rundungen, Abstände, Zielgrößen, Schriftstufen |
| `app/src/main/assets/design/symbole.json` | Symbolsatz: Raster 24, Strich 1,75, je Symbol Teilpfade nur mit `M`, `L`, `C`, `Z` |

Dieselben beiden Dateien liegen in `hubiplan-ios`. `DesignWerteTest` hält die Prüfsummen fest
und vergleicht die Werte im Code (`Colors.kt`, `Design.kt`, `Abstand.kt`, `Bausteine.kt`) mit
der Datei. Wer einen Wert oder ein Symbol ändert, ändert die Datei in beiden Apps und zieht in
beiden den Test nach.

## Schrift

Inter (SIL Open Font License 1.1) als variable Schrift in `app/src/main/res/font/inter.ttf`,
gekürzt auf lateinische Zeichen. `Schrift.kt` setzt sie für alle Textstufen von Material 3;
die Größen wachsen mit der Schriftgröße des Systems. Der Lizenztext steht auf der Lizenzseite
der App (`assets/lizenzen/Inter-OFL-1.1.txt`).

## Symbole

`Strich` (in `Strichsymbole.kt`) nennt jedes Symbol; `strichsymbol(Strich.TERMINE)` liefert das
Vektorbild für `Icon`. Neue Symbole kommen in `symbole.json` (beide Apps) und in die Liste
`Strich`.

## Bausteine

`Bausteine.kt`: `Karte` (flach, mit Rand), `Abschnittstitel`, `SymbolKopf`, `Zeile` mit
`ZeilenTrenner`, `Zaehler`, `Leerzustand`, `Platzhalter`. Dazu aus `Design.kt` `Abzeichen` und
`DatumKaestchen`. Neue Bildschirme setzen sich aus diesen Teilen zusammen; Vereinsfarbe steht
nur als Akzent (Hauptknopf, Symbolkopf, Zähler, gewählter Reiter).

## Reiter

Beide Apps haben dieselben Reiter: **Start · Termine · Neues · Ich** und einen fünften je
Rolle. Vorgabe für den fünften ist Verwaltung, sonst Noten, sonst Aufgaben
(`fuenfteReiter`, `fuenfterReiter` in `MainScreen.kt`); wer mehrere dieser Bereiche hat, wählt
ihn unter „Mein Konto › Einstellungen › Fünfter Reiter“. Was nicht in der Leiste steht, ist auf
„Start“ unter „Schnell erreichbar“ zu finden, die Verwaltung zusätzlich als Symbol im Kopf.
Tablets zeigen in der seitlichen Leiste alle Bereiche.

Liste und Detail (Termine, Neues, Aufgaben, Noten) stehen erst ab 840 dp Breite nebeneinander,
also auf 7- und 10-Zoll-Tablets quer; die Liste ist dann 340 bis 420 dp breit. Ein 10-Zoll-Tablet
hochkant zeigt sie nacheinander wie das Handy. Alle Inhalte stehen auf breiten Bildschirmen
mittig in höchstens 720 dp (`Lesebreite`, wie `lesebreite()` in iOS), damit Karten und Knöpfe
nicht über die ganze Breite laufen.

„Start“ (`StartPane.kt`) zeigt den nächsten Termin mit Zusage und Absage, was auf eine Antwort
wartet, die Kacheln der Startseite und die kurzen Wege. Der Kopf steht nur auf „Start“ in der
Vereinsfarbe (`kopfInVereinsfarbe`); alle anderen Seiten haben einen ruhigen Kopf. Zurück führt
von jedem Reiter zu „Start“ und beendet erst dort die App.

Im Kopf steht der Kurzname der Einheit (`club_display_name`, in HUBIplan unter dem Verein
pflegbar), sonst der volle Name. Passt er nicht in eine Zeile, steht er etwas kleiner auf zwei
Zeilen statt mit „…“ (`KopfName`).

„Start“ kommt in einem Abruf vom Server (`GET /api/portal/<Zugang>/start/`: nächster Termin, „Für
dich zu tun“, Kacheln; siehe `docs/app.md` in HUBIplan). Kennt ein älterer HUBIplan-Stand den
Abruf nicht (404), baut die App die Seite aus der Terminliste (`startAusTerminen`). Zeilen unter
„Für dich zu tun“ führen in den Bereich unter „Ich“ (`startBereich`); unbekannte Ziele zeigt die
App nicht. Die Terminliste hat Zwischenüberschriften je Monat und, solange Rückmeldungen
fehlen, den Filter „… ohne Rückmeldung“.

„Neues“ fasst zusammen, was der Verein mitteilt oder fragt: Mitteilungen, Pinnwand und Umfragen
(`NeuesPane`, `neuesTeile`); die Auswahl darüber zeigt, wo etwas wartet, und fehlt, wenn es nur
Mitteilungen gibt. „Ich“ zeigt den Ausweis und darunter „Mein Bereich“ in vier Gruppen: Geld,
Mitmachen, Verein, Meine Daten (`ichGruppen`); jede Zeile hat Symbol, Kurzstand und Zähler. Unter „Mein
Konto › Einstellungen“ lässt sich die Darstellung wählen: wie das Gerät, hell oder dunkel
(`darstellungDunkel`).

Die Verwaltung beginnt als Arbeitsplatz (`AdminStart`): oben „Ausweis prüfen“ und „Neu“ (Quittung,
Buchung, Termin, Aufgabe je nach Rechten, `neuAktionen`), darunter „Zu erledigen“ mit Zähler, die
Kacheln der Startseite und die Bereiche in den Gruppen des Hauptmenüs im Browser (Planen, Verein,
Vereinsleben, Verwaltung; `sectionGroups`). Die Suche schränkt die Bereiche nach Name und
Beschreibung ein (`sectionSearch`).

Formulare schützen angefangene Eingaben überall (`rememberFormDraft`): Zurück und ein Wechsel des
Reiters fragen nach, bevor etwas verloren geht; `FormulareTest` prüft, dass kein Formular des
Mitgliederbereichs ohne diesen Schutz ist. Datum und Uhrzeit kommen aus der Auswahl des Systems
(`DatePickButton`, `TimePickButton`), die Felder nehmen dafür nur Ziffern.

Nach dem Umbau zeigt „Start“ einmal die Karte „So findest du dich zurecht“ (`Einfuehrung`,
`zeigtEinfuehrung`; der Stand `EINFUEHRUNG_STAND` steht in den Einstellungen des Geräts, eine
höhere Zahl zeigt sie allen noch einmal). Eine gespeicherte Rückmeldung bestätigt das Gerät mit
einem kurzen Tippen (`rememberBestaetigung`).
