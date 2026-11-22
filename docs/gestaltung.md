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
