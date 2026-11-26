package de.hubiplan.app

import de.hubiplan.app.data.Ansicht
import de.hubiplan.app.data.BrowserLogin
import de.hubiplan.app.data.Einmalablage
import de.hubiplan.app.data.GOOGLE_MAPS_HOSTS
import de.hubiplan.app.data.LocalAccountData
import de.hubiplan.app.data.OSM_HOSTS
import de.hubiplan.app.data.UnsafePath
import de.hubiplan.app.data.VideoRoom
import de.hubiplan.app.data.allowedExternalStandUrl
import de.hubiplan.app.data.allowedStandResource
import de.hubiplan.app.data.apiUrl
import de.hubiplan.app.data.httpsOrigin
import de.hubiplan.app.data.imprintSuffix
import de.hubiplan.app.data.normalizeServer
import de.hubiplan.app.data.safeExternalUrl
import de.hubiplan.app.data.sameInstance
import de.hubiplan.app.ui.AdminAway
import java.io.File
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Sicherheitsprüfung 26.11.2026 (SEC-01 bis SEC-08); alle Namen und Adressen sind erfunden. */
class Sicherheitspruefung1126Test {
    @get:Rule val temporary = TemporaryFolder()
    private val server = "https://verein.example.org"
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private val filesDir get() = File(temporary.root, "files").apply { mkdirs() }
    private val cacheDir get() = File(temporary.root, "cache").apply { mkdirs() }

    // --- SEC-01: Anfragen bleiben auf der Instanz ---

    @Test
    fun anfragenBleibenAufDerInstanz() {
        assertEquals("https://verein.example.org/api/app/v1/me/", apiUrl(server, "/api/app/v1/me/").toString())
        assertEquals("https://verein.example.org/api/x/?suche=a%20b&page=2", apiUrl(server, "/api/x/?suche=a%20b&page=2").toString())
        assertEquals("https://verein.example.org:8443/bild.png", apiUrl("$server:8443", "/bild.png").toString())
        // Örtliche Tests sprechen mit einem Server auf dem eigenen Rechner
        assertEquals("http://127.0.0.1:8123/api/", apiUrl("http://127.0.0.1:8123", "/api/").toString())
    }

    @Test
    fun pfadeZuFremdenHostsWerdenAbgelehnt() {
        val boese = listOf(
            "@boese.example/bild", ".boese.example/bild", "//boese.example/bild", "/\\boese.example/bild",
            "bild.png", "", "/bild mit leer.png", "/bild\n.png", "/bild\t.png", ":8443/bild", "https://boese.example/bild",
        )
        for (path in boese) {
            assertThrows("Pfad „$path“", UnsafePath::class.java) { apiUrl(server, path) }
        }
    }

    @Test
    fun ungueltigeInstanzIstKeinPfadfehler() {
        assertNull(apiUrl("verein.example.org", "/api/"))
        assertNull(apiUrl("https://verein.example.org:99999", "/api/"))
        assertNull(apiUrl("https://nutzer:geheim@verein.example.org", "/api/"))
    }

    // --- SEC-02: Export nur für das angemeldete Konto ---

    private fun box(name: String, server: String, email: String, since: Long): File =
        File(filesDir, "${LocalAccountData.QUARANTINE}/$name").apply {
            mkdirs()
            File(this, ".konto").writeText("$server\n$email\nerfundener-schluessel\n$since")
        }

    private val offen: (String, ByteArray) -> ByteArray = { _, data -> data }

    @Test
    fun exportNurFuerAngemeldetesKonto() {
        val old = now - 100 * day
        File(box("entwuerfe-k-eins", server, "kasse@example.org", old), "a.bin").writeText("{\"zweck\":\"Erfundener Beleg A\"}")
        File(box("quittungen-k-zwei", server, "anderer@example.org", old), "b.bin").writeText("{\"zweck\":\"Fremder Beleg B\"}")
        File(box("entwuerfe-k-drei", "https://anderer-verein.example", "kasse@example.org", old), "c.bin").writeText("{\"zweck\":\"Fremder Beleg C\"}")
        // Ohne lesbaren Eigentümer: nie exportieren
        File(filesDir, "${LocalAccountData.QUARANTINE}/entwuerfe-k-vier").apply { mkdirs(); setLastModified(old) }
            .let { File(it, "d.bin").writeText("{\"zweck\":\"Herrenloser Beleg D\"}"); it.setLastModified(old) }
        // Noch nicht alt genug
        File(box("quittungen-k-fuenf", server, "kasse@example.org", now - 10 * day), "e.bin").writeText("{\"zweck\":\"Neuer Beleg E\"}")

        val mine = listOf("https://VEREIN.example.org" to "Kasse@Example.org")
        assertEquals(1, LocalAccountData.exportableStaleCount(filesDir, mine, now))
        val (file, count) = LocalAccountData.exportStale(filesDir, cacheDir, mine, now, offen)!!
        assertEquals(1, count)
        val text = file.readText()
        assertTrue("Erfundener Beleg A" in text)
        for (fremd in listOf("Fremder Beleg B", "Fremder Beleg C", "Herrenloser Beleg D", "Neuer Beleg E")) assertFalse(fremd in text)

        // Niemand angemeldet, anderes Konto oder leere E-Mail: nichts
        for (accounts in listOf(emptyList(), listOf(server to "dritte@example.org"), listOf(server to ""))) {
            assertEquals(0, LocalAccountData.exportableStaleCount(filesDir, accounts, now))
            assertNull(LocalAccountData.exportStale(filesDir, cacheDir, accounts, now, offen))
        }
        // Die Rückfrage zählt weiter alle alten Vorgänge (Löschen bleibt möglich)
        assertEquals(4, LocalAccountData.staleCount(filesDir, now))
    }

    @Test
    fun exportLiegtJedesMalWoanders() {
        File(box("entwuerfe-k-eins", server, "kasse@example.org", now - 100 * day), "a.bin").writeText("{}")
        val mine = listOf(server to "kasse@example.org")
        val first = LocalAccountData.exportStale(filesDir, cacheDir, mine, now, offen)!!.first
        val second = LocalAccountData.exportStale(filesDir, cacheDir, mine, now, offen)!!.first
        assertNotEquals(first.path, second.path)
        assertEquals(File(cacheDir, LocalAccountData.EXPORT), first.parentFile!!.parentFile)
        // Der frühere Export ist weg
        assertFalse(first.exists())
        assertTrue(second.exists())
    }

    // --- SEC-03: nach der Abmeldung durch den Server bleibt nur die Quarantäne ---

    @Test
    fun abmeldungDurchServerLoeschtAllesAusserUngesendetem() {
        val key = LocalAccountData.key(server, 7)
        val other = LocalAccountData.key(server, 8)
        val folders = listOf("entwuerfe", "quittungen", "ausweise", "wappen", "formularanhaenge", "offline", "standzugaenge")
        for (k in listOf(key, other)) {
            for (folder in folders) File(filesDir, "$folder/k-${LocalAccountData.id(k)}/x.bin").apply { parentFile!!.mkdirs(); writeText("x") }
        }
        LocalAccountData.retireFolders(filesDir, key)
        val id = LocalAccountData.id(key)
        for (folder in listOf("ausweise", "wappen", "formularanhaenge", "offline", "standzugaenge")) {
            assertFalse(folder, File(filesDir, "$folder/k-$id").exists())
        }
        // Ungesendete Buchungen und Quittungen gehen danach in die Quarantäne
        for (folder in listOf("entwuerfe", "quittungen")) assertTrue(folder, File(filesDir, "$folder/k-$id/x.bin").exists())
        // Das andere Konto bleibt unberührt
        for (folder in folders) assertTrue(folder, File(filesDir, "$folder/k-${LocalAccountData.id(other)}/x.bin").exists())
    }

    // --- SEC-04: Standmodus ---

    @Test
    fun standmodusBleibtAufDerInstanz() {
        assertTrue(sameInstance(server, "$server/stand/zugang/abc"))
        assertFalse(sameInstance(server, "https://nutzer@verein.example.org/stand/"))
        assertFalse(sameInstance(server, "https://verein.example.org@boese.example/stand/"))
        assertFalse(sameInstance("https://nutzer@verein.example.org", "$server/stand/"))

        assertTrue(allowedExternalStandUrl("https://hilfe.example/seite"))
        for (url in listOf(
            "intent://scan/#Intent;scheme=zxing;end", "hubiplan://verbinden?server=x&code=y", "tel:+4900000000",
            "mailto:fiktiv@example.org", "javascript:alert(1)", "http://hilfe.example/", "file:///etc/hosts",
            "content://de.hubiplan.app.dateien/x", "https://nutzer:geheim@hilfe.example/", "",
        )) {
            assertFalse(url, allowedExternalStandUrl(url))
        }

        assertTrue(allowedStandResource(server, "$server/static/stand.js"))
        assertTrue(allowedStandResource(server, "data:image/png;base64,AAAA"))
        assertTrue(allowedStandResource(server, "blob:https://verein.example.org/1234"))
        assertTrue(allowedStandResource(server, "about:blank"))
        assertFalse(allowedStandResource(server, "https://cdn.example/skript.js"))
        assertFalse(allowedStandResource(server, "http://verein.example.org/static/stand.js"))
        assertFalse(allowedStandResource(server, "wss://boese.example/"))
    }

    // --- SEC-05: Dateien für andere Apps je Konto und je Ausgabe getrennt ---

    @Test
    fun ablageJeKontoUndAusgabeGetrennt() {
        val a = LocalAccountData.key(server, 1)
        val b = LocalAccountData.key(server, 2)
        val first = Einmalablage.write(cacheDir, Ansicht.FOLDER, a, "rechnung.pdf", "A1".toByteArray())
        val foreign = Einmalablage.write(cacheDir, Ansicht.FOLDER, b, "rechnung.pdf", "B1".toByteArray())
        val second = Einmalablage.write(cacheDir, Ansicht.FOLDER, a, "rechnung.pdf", "A2".toByteArray())
        assertNotEquals(first.path, second.path)
        assertNotEquals(first.path, foreign.path)
        // Die frühere Datei desselben Kontos ist weg, die des anderen bleibt
        assertFalse(first.exists())
        assertEquals("A2", second.readText())
        assertEquals("B1", foreign.readText())
        assertEquals(Einmalablage.accountDir(cacheDir, Ansicht.FOLDER, a), second.parentFile!!.parentFile)

        Einmalablage.forget(cacheDir, Ansicht.FOLDER, a)
        assertFalse(second.exists())
        assertTrue(foreign.exists())
    }

    // --- SEC-06: Links aus Serverantworten ---

    @Test
    fun kartenlinksNurZuDenKartendiensten() {
        val osm = "https://www.openstreetmap.org/search?query=Musterstra%C3%9Fe+1"
        val google = "https://www.google.com/maps/dir/?api=1&destination=51.0%2C7.0"
        assertEquals(osm, safeExternalUrl(osm, OSM_HOSTS))
        assertEquals(google, safeExternalUrl(google, GOOGLE_MAPS_HOSTS))
        assertNull(safeExternalUrl(google, OSM_HOSTS))
        for (url in listOf(
            null, "", "http://www.openstreetmap.org/", "intent://x#Intent;end", "geo:0,0", "javascript:alert(1)",
            "https://www.openstreetmap.org@boese.example/", "https://openstreetmap.org.boese.example/", "https://nutzer@www.openstreetmap.org/",
        )) {
            assertNull(url, safeExternalUrl(url, OSM_HOSTS))
        }
    }

    @Test
    fun videoraumNurHttpsOhneBenutzerangabe() {
        assertTrue(VideoRoom(url = "https://cloud.example.org/call/abc").openable)
        assertFalse(VideoRoom(url = "http://cloud.example.org/call/abc").openable)
        assertFalse(VideoRoom(url = "https://nutzer:geheim@cloud.example.org/call/abc").openable)
        assertFalse(VideoRoom(url = "intent://call#Intent;end").openable)
        assertFalse(VideoRoom().openable)
    }

    @Test
    fun impressumsschluesselOhneSonderzeichen() {
        assertEquals("/sv-musterstadt_1", imprintSuffix("sv-musterstadt_1"))
        assertEquals("", imprintSuffix(null))
        for (key in listOf("", "../admin", "a/b", "a?b=c", "a#b", "@boese.example", "a b", "ä", "x".repeat(65))) {
            assertEquals(key, "", imprintSuffix(key))
        }
    }

    // --- SEC-07: Verwaltung im Hintergrund ---

    @Test
    fun verwaltungBleibtNurNachEigenemAbsprungOffen() {
        // Ohne Absprung aus der Verwaltung: sofort sperren
        assertFalse(AdminAway.consume(1_000L))
        AdminAway.allow(5_000L)
        assertTrue(AdminAway.consume(5_400L))
        // Die Erlaubnis gilt nur einmal
        assertFalse(AdminAway.consume(5_500L))
        // Und nur kurz: ein späterer Wechsel aus der App sperrt
        AdminAway.allow(10_000L)
        assertFalse(AdminAway.consume(10_000L + AdminAway.WINDOW_MS + 1))
    }

    // --- SEC-08: Adressen und Anmeldung im Browser ---

    @Test
    fun instanzadresseImmerKleingeschrieben() {
        assertEquals(server, httpsOrigin(URI("https://Verein.Example.ORG")))
        assertEquals("$server:8443", httpsOrigin(URI("https://VEREIN.example.org:8443")))
        assertEquals(normalizeServer("verein.example.org"), normalizeServer("Verein.Example.Org"))
        assertEquals(
            BrowserLogin.verifierFor(de.hubiplan.app.data.PendingBrowserLogin(normalizeServer("VEREIN.example.org")!!, "pruefwert", now), server, now),
            "pruefwert",
        )
    }

    @Test
    fun pushEndpunktNurHttps() {
        assertEquals("https://push.example/abc", safeExternalUrl("https://push.example/abc"))
        for (url in listOf("http://push.example/abc", "file:///data/x", "kaputt", "", "https://nutzer:geheim@push.example/abc")) {
            assertNull(url, safeExternalUrl(url))
        }
    }
}
