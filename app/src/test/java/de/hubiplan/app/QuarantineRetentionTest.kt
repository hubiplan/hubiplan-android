package de.hubiplan.app

import de.hubiplan.app.data.LocalAccountData
import java.io.File
import java.io.IOException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuarantineRetentionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private val alias = "erfundener-kontoschluessel"
    private val deletedKeys = mutableListOf<String>()
    private val filesDir get() = File(temporary.root, "files").apply { mkdirs() }
    private val cacheDir get() = File(temporary.root, "cache").apply { mkdirs() }

    private fun seal(plain: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain.toByteArray())
    }

    private fun open(unused: String, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        return cipher.doFinal(data, 12, data.size - 12)
    }

    private fun box(name: String, since: Long = now): File =
        File(filesDir, "${LocalAccountData.QUARANTINE}/$name").apply {
            mkdirs()
            File(this, ".konto").writeText("https://verein.example\nfiktiv@example.org\n$alias\n$since")
        }

    private fun corrupt(file: File) {
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertThrows(AEADBadTagException::class.java) { open(alias, bytes) }
    }

    private fun sweep(hasKey: (String) -> Boolean = { true }, read: (String, ByteArray) -> ByteArray = ::open) =
        LocalAccountData.sweep(filesDir, cacheDir, now, hasKey, read, deletedKeys::add)

    @Test
    fun beschaedigteErsteDateiErhaeltNachbarnUndSchluessel() {
        val box = box("quittungen-k-erfunden")
        File(box, "auswahl-7.dat").writeBytes(seal("Erfundene Auswahl"))
        File(box, "eins.bin").writeBytes(seal("Erfundene unterschriebene Quittung A"))
        File(box, "zwei.bin").writeBytes(seal("Erfundene unterschriebene Quittung B"))
        // Die tatsächliche erste Datei beschädigen, unabhängig von der Dateisystemreihenfolge.
        val entries = box.listFiles()!!.filter { it.name != ".konto" }
        corrupt(entries.first())
        val before = box.listFiles()!!.associate { it.name to it.readBytes() }

        assertEquals(LocalAccountData.Sweep(0, 0), sweep())
        before.forEach { (name, bytes) -> assertArrayEquals(bytes, File(box, name).readBytes()) }
        entries.drop(1).forEach { assertTrue(open(alias, it.readBytes()).isNotEmpty()) }
        assertTrue(deletedKeys.isEmpty())
    }

    @Test
    fun beschaedigterAuswahlcacheBleibtMitIntaktenBelegenErhalten() {
        val cache = File(box("quittungen-k-cache"), "auswahl-7.dat")
        cache.writeBytes(seal("Erfundene Auswahl"))
        corrupt(cache)
        val receipt = File(box("entwuerfe-k-belege"), "eins.bin")
        receipt.writeBytes(seal("Erfundener Beleg"))
        val before = receipt.readBytes()

        assertEquals(LocalAccountData.Sweep(0, 0), sweep())
        assertTrue(cache.exists())
        assertArrayEquals(before, receipt.readBytes())
        assertEquals("Erfundener Beleg", String(open(alias, receipt.readBytes())))
        assertTrue(deletedKeys.isEmpty())
    }

    @Test
    fun gesperrterOderVoruebergehendUnlesbarerSpeicherWirdNichtGeloescht() {
        val receipt = File(box("quittungen-k-gesperrt"), "eins.bin")
        receipt.writeBytes(seal("Erfundener Beleg"))
        val before = receipt.readBytes()
        assertEquals(LocalAccountData.Sweep(0, 0), sweep(read = { _, _ -> throw IOException("gesperrt") }))
        assertArrayEquals(before, receipt.readBytes())
        assertTrue(deletedKeys.isEmpty())
    }

    @Test
    fun nachgewiesenFehlenderSchluesselErlaubtWeiterhinBereinigung() {
        val box = box("quittungen-k-ohne-schluessel")
        File(box, "eins.bin").writeBytes(seal("Erfundener Beleg"))
        assertEquals(LocalAccountData.Sweep(0, 1), sweep(hasKey = { false }))
        assertFalse(box.exists())
        assertEquals(listOf(alias), deletedKeys)
    }

    @Test
    fun abgelaufenerOrdnerEntferntKeinenNochVerwendetenSchluessel() {
        val old = box("quittungen-k-alt", now - 121 * day)
        File(old, "alt.bin").writeBytes(seal("Erfundener alter Beleg"))
        val current = File(box("entwuerfe-k-neu"), "neu.bin")
        current.writeBytes(seal("Erfundener aktueller Beleg"))
        assertEquals(LocalAccountData.Sweep(1, 0), sweep())
        assertFalse(old.exists())
        assertTrue(current.exists())
        assertTrue(deletedKeys.isEmpty())
    }
}
