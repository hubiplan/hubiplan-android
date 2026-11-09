package de.hubiplan.app

import de.hubiplan.app.data.Blaettern
import de.hubiplan.app.data.FolderItem
import de.hubiplan.app.data.Mappe
import de.hubiplan.app.data.NotenStore
import de.hubiplan.app.data.NotenSuche
import de.hubiplan.app.data.NotenSync
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.PultStelle
import de.hubiplan.app.data.PultTasten
import de.hubiplan.app.data.ScoreFolders
import de.hubiplan.app.data.Zuschnitt
import de.hubiplan.app.data.json
import de.hubiplan.app.data.liveTarget
import de.hubiplan.app.ui.Tab
import de.hubiplan.app.ui.adminInHeader
import de.hubiplan.app.ui.barTabs
import de.hubiplan.app.ui.nummerSchluessel
import de.hubiplan.app.ui.wideNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** Noten in der App (hubiplan konzepte/noten-app.md, N3–N6); alle Daten erfunden. */
class NotenTest {
    private val antwort = """
        {"folders":[{"id":3,"key":"folder-3","can_lead":true,"kind":"marchbook","name":"Marschbuch","note":"",
          "items":[
            {"kind":"piece","arrangement":11,"title":"Alte Kameraden","composer":"Carl Teike","scores":[
              {"id":5,"title":"Tenorhorn","version":9,"content_type":"application/pdf","notation":"","formats":[],"url":"/api/portal/7/music/files/5/"},
              {"id":6,"title":"Hörbeispiel","version":2,"content_type":"audio/midi","notation":"midi","formats":["midi"],"url":"/api/portal/7/music/files/6/"}
            ],"position":1,"number":"27"},
            {"kind":"piece","arrangement":12,"title":"Florentiner Marsch","composer":"Julius Fučík","scores":[
              {"id":8,"title":"Partitur","version":4,"content_type":"application/vnd.recordare.musicxml+xml","notation":"musicxml","formats":["musicxml","pdf","midi"],"url":"/api/portal/7/music/files/8/"}
            ],"position":2,"number":"2"},
            {"kind":"piece","arrangement":13,"title":"Schützenliesel","composer":"","scores":[],"position":3,"number":"270"}
          ]}],
         "events":[{"key":"rehearsal-4-2026-11-12T19:00:00+01:00","live_key":"rehearsal-4","can_lead":false,"type":"rehearsal",
           "title":"Gesamtprobe","starts_at":"2026-11-12T19:00:00+01:00","ends_at":"2026-11-12T21:00:00+01:00","location":"Musterhalle",
           "cancelled":false,"focus":"","items":[
             {"kind":"pause","arrangement":null,"title":"Pause","scores":[],"position":1,"number":""},
             {"kind":"piece","arrangement":12,"title":"Florentiner Marsch","composer":"Julius Fučík","scores":[],"position":2,"number":""}
           ],"unbekannt":1}]}
    """.trimIndent()

    private val folders = json.decodeFromString(ScoreFolders.serializer(), antwort)
    private val mappen = folders.mappen
    private val marschbuch = mappen.first()

    @Test
    fun antwortLesen() {
        assertEquals(listOf("folder-3", "rehearsal-4-2026-11-12T19:00:00+01:00"), mappen.map { it.key })
        assertEquals("rehearsal-4", mappen[1].liveKey)
        assertTrue(marschbuch.isMarchbook && marschbuch.canLead)
        assertEquals(1, mappen[1].pieces.size)
        val alte = marschbuch.pieces[0]
        assertEquals(listOf(5), alte.sheets.map { it.id })
        assertEquals(6, alte.audio?.id)
        val florentiner = marschbuch.pieces[1]
        // MusicXML: Notenbild als vom Server gesetztes PDF, Hörbeispiel aus den Noten
        assertEquals("pdf", florentiner.sheets.single().sheetFormat)
        assertTrue(florentiner.sheets.single().canPlay)
        assertEquals("", alte.sheets.single().sheetFormat)
    }

    @Test
    fun sucheUmlauteWortanfaengeNummern() {
        assertEquals("schuetzenliesel", NotenSuche.normalize("Schützenliesel"))
        assertEquals("fucik", NotenSuche.normalize("Fučík"))
        // Wortanfänge, ohne Groß-/Kleinschreibung
        assertEquals(11, NotenSuche.suche("alte kam", mappen).single().arrangement)
        assertEquals(11, NotenSuche.suche("KAMERADEN", mappen).single().arrangement)
        // „ue“ findet „ü“, und „ü“ findet „ü“
        assertEquals(13, NotenSuche.suche("schuetzen", mappen).single().arrangement)
        assertEquals(13, NotenSuche.suche("Schütz", mappen).single().arrangement)
        // Komponist ohne Akzent
        assertEquals(12, NotenSuche.suche("fucik", mappen).single().arrangement)
        // Mitten im Wort zählt nicht
        assertTrue(NotenSuche.suche("meraden", mappen).isEmpty())
        // Nummer genau vor „beginnt mit“
        assertEquals(listOf(11, 13), NotenSuche.suche("27", mappen).map { it.arrangement })
        // Gleiches Stück in zwei Mappen: ein Treffer mit beiden Stellen
        val flor = NotenSuche.suche("florentiner", mappen).single()
        assertEquals(2, flor.stellen.size)
        assertTrue(NotenSuche.suche("   ", mappen).isEmpty())
    }

    @Test
    fun schnellwahlNummer() {
        // „2“ ist nicht eindeutig (27, 270 beginnen auch so), aber vorhanden
        assertNull(NotenSuche.eindeutigeNummer("2", marschbuch))
        assertEquals(12, NotenSuche.nummer("2", marschbuch)?.arrangement)
        assertNull(NotenSuche.eindeutigeNummer("27", marschbuch))
        assertEquals(13, NotenSuche.eindeutigeNummer("270", marschbuch)?.arrangement)
        assertNull(NotenSuche.nummer("99", marschbuch))
        assertTrue(nummerSchluessel("2") < nummerSchluessel("10"))
        assertTrue(nummerSchluessel("10") < nummerSchluessel("10a"))
        assertTrue(nummerSchluessel("99") < nummerSchluessel("Zugabe"))
    }

    @Test
    fun blaettern() {
        val pages = mapOf(0 to 2, 1 to 3)
        val p: (Int) -> Int = { pages[it] ?: 1 }
        assertEquals(PultStelle(0, 1), Blaettern.vor(PultStelle(0, 0), p, 2))
        // Am Ende des Stücks ins nächste
        assertEquals(PultStelle(1, 0), Blaettern.vor(PultStelle(0, 1), p, 2))
        // Am Ende der Mappe bleibt es
        assertNull(Blaettern.vor(PultStelle(1, 2), p, 2))
        // Zurück aus dem Stückanfang auf die letzte Seite des vorigen
        assertEquals(PultStelle(0, 1), Blaettern.zurueck(PultStelle(1, 0), p))
        assertNull(Blaettern.zurueck(PultStelle(0, 0), p))
        // Halbe Seite: erst halb, dann ganz
        val half = Blaettern.vor(PultStelle(1, 0), p, 2, halfTurn = true)
        assertEquals(PultStelle(1, 0, half = true), half)
        assertEquals(PultStelle(1, 1), Blaettern.vor(half!!, p, 2, halfTurn = true))
        assertEquals(PultStelle(1, 0), Blaettern.zurueck(half, p))
        // Zwei Seiten nebeneinander
        assertEquals(PultStelle(1, 2), Blaettern.vor(PultStelle(1, 0), p, 2, step = 2))
        assertEquals(PultStelle(0, 0), Blaettern.zurueck(PultStelle(1, 0), p, step = 2))
        assertTrue(Blaettern.letzteSeite(PultStelle(1, 2), 3))
        assertFalse(Blaettern.letzteSeite(PultStelle(1, 1), 3))
        // Seitenzahl des vorigen Stücks noch unbekannt: groß, das Pult begrenzt nach dem Laden
        assertEquals(Int.MAX_VALUE - 1, Blaettern.zurueck(PultStelle(1, 0), { if (it == 0) Int.MAX_VALUE else 1 })?.page)
    }

    @Test
    fun tastenUndTippen() {
        assertEquals(PultTasten.VOR, PultTasten.richtung(PultTasten.PAGE_DOWN, volume = true))
        assertEquals(PultTasten.VOR, PultTasten.richtung(PultTasten.DPAD_RIGHT, volume = false))
        assertEquals(PultTasten.ZURUECK, PultTasten.richtung(PultTasten.PAGE_UP, volume = true))
        assertEquals(PultTasten.VOR, PultTasten.richtung(PultTasten.VOLUME_DOWN, volume = true))
        assertEquals(PultTasten.ZURUECK, PultTasten.richtung(PultTasten.VOLUME_DOWN, volume = true, swapVolume = true))
        assertEquals(0, PultTasten.richtung(PultTasten.VOLUME_UP, volume = false))
        assertEquals(0, PultTasten.richtung(4 /* Zurück */, volume = true))
        assertEquals(PultTasten.ZURUECK, PultTasten.tippen(10f, 500f, 900f, 1600f))
        assertEquals(PultTasten.VOR, PultTasten.tippen(800f, 100f, 900f, 1600f))
        assertEquals(PultTasten.MITTE, PultTasten.tippen(450f, 800f, 900f, 1600f))
        // Oben in der Mitte blättert vor (breite Vor-Fläche)
        assertEquals(PultTasten.VOR, PultTasten.tippen(450f, 100f, 900f, 1600f))
    }

    @Test
    fun raenderAbschneiden() {
        val w = 100
        val h = 100
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val pixels = IntArray(w * h) { white }
        for (y in 30 until 60) for (x in 20 until 80) pixels[y * w + x] = black
        val b = Zuschnitt.grenzen(pixels, w, h, luft = 0f)!!
        assertEquals(listOf(20, 30, 80, 60), b.toList())
        // Leere Seite: nichts abschneiden
        assertNull(Zuschnitt.grenzen(IntArray(w * h) { white }, w, h))
        // Einzelner Staubpunkt zählt nicht
        val dust = IntArray(w * h) { white }.also { it[5 * w + 5] = black }
        assertNull(Zuschnitt.grenzen(dust, w, h))
    }

    @Test
    fun navigationMitNoten() {
        // Ohne Noten unverändert
        assertEquals(listOf(Tab.TERMINE, Tab.MITTEILUNGEN, Tab.ICH), barTabs(adminVisible = false, tasksVisible = false))
        assertEquals(
            listOf(Tab.TERMINE, Tab.NOTEN, Tab.AUFGABEN, Tab.MITTEILUNGEN, Tab.ICH),
            barTabs(adminVisible = true, tasksVisible = true, notesVisible = true),
        )
        // Telefon mit sechs Zielen: Verwaltung in die Kopfleiste
        val phone = barTabs(adminVisible = true, tasksVisible = true, notesVisible = true, wide = false)
        assertTrue(adminInHeader(true, phone))
        // Tablet: alle sechs
        val tablet = barTabs(adminVisible = true, tasksVisible = true, notesVisible = true, wide = true)
        assertEquals(6, tablet.size)
        assertFalse(adminInHeader(true, tablet))
        // Fünf passen: Verwaltung bleibt unten
        assertEquals(Tab.VERWALTUNG, barTabs(adminVisible = true, tasksVisible = false, notesVisible = true).last())
        assertTrue(wideNavigation(800, 1280))
        assertFalse(wideNavigation(411, 891))
        assertFalse(wideNavigation(891, 411))
    }

    @Test
    fun musikerZugang() {
        fun access(musician: Boolean, modules: List<String>?) = PortalAccess(id = 7, clubName = "Musikverein Musterstadt", musician = musician, modules = modules)
        assertTrue(access(true, listOf("music", "scores", "events")).hasScores)
        assertFalse(access(false, listOf("music", "scores")).hasScores)
        assertFalse(access(true, listOf("music")).hasScores)
        // Ältere Server ohne Modulliste: kein Bereich
        assertFalse(access(true, null).hasScores)
    }

    @Test
    fun vorladenAmVortag() {
        val now = OffsetDateTime.parse("2026-11-11T20:00:00+01:00")
        val probe = mappen[1]
        assertTrue(NotenStore.dueForPrefetch(probe, now))
        assertFalse(NotenStore.dueForPrefetch(probe, now.minusDays(3)))
        assertFalse(NotenStore.dueForPrefetch(probe.copy(cancelled = true), now))
        assertFalse(NotenStore.dueForPrefetch(marschbuch, now))
    }

    @Test
    fun satzFuerDenBildschirm() {
        val pdf = marschbuch.pieces[0].sheets.single()
        val xml = marschbuch.pieces[1].sheets.single()
        assertEquals("", NotenSync.variantOf(pdf, "2", "1778x50"))
        assertEquals("pdf", NotenSync.variantOf(xml))
        assertEquals("pdf-t-3-l1778x50", NotenSync.variantOf(xml, "-3", "1778x50"))
        // Handy hoch, Tablet quer, Grenzen wie auf dem Server
        assertEquals("2222x50", NotenSync.layoutFor(1080, 2400, 50))
        assertEquals("800x120", NotenSync.layoutFor(2560, 800, 200))
        assertEquals("3000x25", NotenSync.layoutFor(300, 2400, 10))
        assertEquals("", NotenSync.layoutFor(0, 100, 50))
    }

    @Test
    fun liveZiel() {
        assertEquals("folder-3", liveTarget("folder-3"))
        assertEquals("rehearsal-4", liveTarget("rehearsal-4"))
        listOf("folder-3/../x", "anders-1", "rehearsal-4-2026-11-12", "").forEach {
            assertTrue(runCatching { liveTarget(it) }.isFailure)
        }
    }

    @Test
    fun pausenSindKeineStuecke() {
        val pause = FolderItem(kind = "pause", title = "Pause")
        assertFalse(pause.isPiece)
        assertNull(NotenSuche.rang("Pause", pause, Mappe("k", "k", "Probe", "rehearsal", false, items = listOf(pause))))
    }
}
