package de.hubiplan.app

import de.hubiplan.app.data.GeteilteDateien
import de.hubiplan.app.data.LocalAccountData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Gesamtaudit 10.11., V04: geteilte Exporte und PDFs nach etwa einer Stunde weg. */
class GeteilteDateienTest {
    private val hour = GeteilteDateien.MAX_AGE_MS

    private fun file(dir: File, path: String, modified: Long) =
        File(dir, path).apply { parentFile!!.mkdirs(); writeText("x"); setLastModified(modified) }

    @Test
    fun alte_dateien_weg_neue_bleiben() {
        val cache = Files.createTempDirectory("cache").toFile()
        val now = 1_800_000_000_000L
        val oldReceipt = file(cache, "quittungen/k-1/abc/Quittung-1.pdf", now - hour - 1000)
        val oldExport = file(cache, "${LocalAccountData.EXPORT}/hubiplan-belege.json", now - 2 * hour)
        val newPass = file(cache, "${LocalAccountData.WALLET_PASS}/mitgliedsausweis.pkpass", now - 10 * 60 * 1000)
        val newView = file(cache, "ansicht/rechnung.pdf", now - 1000)
        // Notenpult und Kamerafotos räumen sich selbst auf
        val pult = file(cache, "notenpult/n1.pdf", now - 3 * hour)
        val foto = file(cache, "fotos/beleg.jpg", now - 3 * hour)

        // Die älteste Datei bestimmt die nächste Runde
        assertEquals(oldExport.lastModified() + hour, GeteilteDateien.nextExpiry(cache))
        assertEquals(2, GeteilteDateien.clear(cache, now))

        assertFalse(oldReceipt.exists() || oldExport.exists())
        assertFalse(File(cache, "quittungen/k-1").exists())
        assertTrue(File(cache, "quittungen").isDirectory)
        assertTrue(newPass.exists() && newView.exists() && pult.exists() && foto.exists())
        assertEquals(newPass.lastModified() + hour, GeteilteDateien.nextExpiry(cache))

        assertEquals(2, GeteilteDateien.clear(cache, now + hour + 1000))
        assertNull(GeteilteDateien.nextExpiry(cache))
        cache.deleteRecursively()
    }
}
