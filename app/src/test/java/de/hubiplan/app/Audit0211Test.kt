package de.hubiplan.app

import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.LocalAccountData
import de.hubiplan.app.data.MAX_JSON_BYTES
import de.hubiplan.app.data.RenewChallenge
import de.hubiplan.app.data.SafeBitmap
import de.hubiplan.app.data.apiOrigin
import de.hubiplan.app.data.decodeApi
import de.hubiplan.app.data.readLimited
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Gesamtaudit 02.11.: WA-S01 (Grenzen), WA-F01 (Schemafehler), WA-V01 (Herkunft). */
class Audit0211Test {
    private val jsonType = "application/json".toMediaType()

    private fun expectApiError(block: () -> Unit): ApiException {
        try {
            block()
        } catch (e: ApiException) {
            return e
        }
        fail("ApiException erwartet")
        throw IllegalStateException()
    }

    /** Körper ohne Längenangabe, wie bei chunked oder entpacktem gzip. */
    private fun chunked(size: Long): ResponseBody {
        val buffer = Buffer()
        val block = ByteArray(64 * 1024) { 'x'.code.toByte() }
        var left = size
        while (left > 0) {
            val n = minOf(left, block.size.toLong()).toInt()
            buffer.write(block, 0, n)
            left -= n
        }
        return buffer.asResponseBody(jsonType, -1)
    }

    @Test
    fun normale_antwort_wird_gelesen() {
        val data = """{"challenge":"abc"}""".toByteArray()
        assertArrayEquals(data, readLimited(data.toResponseBody(jsonType), MAX_JSON_BYTES))
    }

    @Test
    fun zu_grosse_laengenangabe_wird_abgelehnt() {
        val body = "{}".toResponseBody(jsonType)
        val big = object : ResponseBody() {
            override fun contentType() = jsonType
            override fun contentLength() = MAX_JSON_BYTES + 1
            override fun source() = body.source()
        }
        assertEquals(502, expectApiError { readLimited(big, MAX_JSON_BYTES) }.status)
    }

    @Test
    fun zu_grosser_koerper_ohne_laenge_wird_abgelehnt() {
        assertEquals(502, expectApiError { readLimited(chunked(MAX_JSON_BYTES + 1), MAX_JSON_BYTES) }.status)
        // genau an der Grenze geht es noch
        assertEquals(1000, readLimited(chunked(1000), 1000).size)
    }

    @Test
    fun kaputte_erfolgsantworten_werden_zu_fehlern() {
        for (text in listOf("<html>Wartung</html>", """{"challenge":""", "{}", """{"challenge":[1]}""", "[]", "")) {
            val e = expectApiError { decodeApi<RenewChallenge>(text) }
            assertEquals(502, e.status)
            assertTrue(e.message!!.isNotEmpty())
            // Der Antworttext steht nicht in der Meldung
            assertTrue(!e.message!!.contains("Wartung"))
        }
        assertEquals("abc", decodeApi<RenewChallenge>("""{"challenge":"abc"}""").challenge)
    }

    @Test
    fun pixelbombe_wird_verkleinert_geladen() {
        // 30 000 × 30 000 Bildpunkte: höchstens MAX_PIXELS nach dem Verkleinern
        val sample = SafeBitmap.sampleSize(30_000, 30_000, 1600)
        assertTrue((30_000L / sample) * (30_000L / sample) <= SafeBitmap.MAX_PIXELS)
        assertTrue(30_000 / sample >= 800)
        // Schmale, sehr lange Bilder ebenso
        val long = SafeBitmap.sampleSize(200, 2_000_000, 432)
        assertTrue((200L / long) * (2_000_000L / long) <= SafeBitmap.MAX_PIXELS)
        // Kleine Bilder unverändert
        assertEquals(1, SafeBitmap.sampleSize(400, 300, 432))
    }

    @Test
    fun herkunft_unterscheidet_konten_ohne_schluessel() {
        val a = apiOrigin("https://a.example", "hpd_eins")
        assertEquals(a, apiOrigin("https://a.example", "hpd_eins"))
        assertNotEquals(a, apiOrigin("https://b.example", "hpd_eins"))
        assertNotEquals(a, apiOrigin("https://a.example", "hpd_zwei"))
        assertTrue(!a.contains("hpd_eins"))
    }

    @Test
    fun fq24_fristen_fuer_alte_belege() {
        // Rückfrage mit Export nach 90 Tagen, endgültig gelöscht 30 Tage danach
        assertEquals(90L, LocalAccountData.STALE_DAYS)
        assertEquals(120L, LocalAccountData.FINAL_DAYS)
    }
}
