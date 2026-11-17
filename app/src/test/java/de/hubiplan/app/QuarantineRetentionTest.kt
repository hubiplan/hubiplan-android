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

    private fun source(vararg names: String): File =
        File(filesDir, "entwuerfe/k-erfunden").apply {
            mkdirs()
            names.forEach { File(this, it).writeBytes(seal("Erfundener Entwurf $it")) }
        }

    private fun target(): File = File(filesDir, "${LocalAccountData.QUARANTINE}/entwuerfe-k-erfunden")

    private fun quarantine(source: File, target: File, move: (File, File) -> Boolean = { from, to -> from.renameTo(to) }) =
        LocalAccountData.quarantine(source, target, "https://verein.example", "fiktiv@example.org", alias, now, move)

    private fun since(box: File) = File(box, ".konto").readLines()[3].toLong()

    @Test
    fun eigentuemerWirdVorDemVerschiebenGeschrieben() {
        val source = source("eins.bin", "zwei.bin")
        val target = target()
        var seen = 0
        assertTrue(quarantine(source, target) { from, to ->
            val owner = File(to.parentFile, ".konto")
            assertTrue(owner.exists())
            assertEquals(listOf("https://verein.example", "fiktiv@example.org", alias, "$now"), owner.readLines())
            seen++
            from.renameTo(to)
        })
        assertEquals(2, seen)
        assertEquals(now, since(target))
        assertFalse(source.exists())
    }

    @Test
    fun abbruchBeimVerschiebenHinterlaesstQuarantaeneMitEigentuemer() {
        val source = source("eins.bin")
        val target = target()
        assertThrows(IllegalStateException::class.java) {
            quarantine(source, target) { _, _ -> throw IllegalStateException("App beendet") }
        }
        assertEquals(now, since(target))
        assertTrue(File(source, "eins.bin").exists())
    }

    @Test
    fun nichtsVerschobenHinterlaesstKeineLeereQuarantaene() {
        val source = source("eins.bin")
        val target = target()
        // Verschieben scheitert: Entwurf bleibt in der Quelle, Schlüssel muss bleiben
        assertTrue(quarantine(source, target) { _, _ -> false })
        assertFalse(File(target, ".konto").exists())
        assertFalse(target.exists())
        assertTrue(File(source, "eins.bin").exists())

        // Nichts zu verschieben: keine Quarantäne, Schlüssel darf weg
        val empty = File(filesDir, "quittungen/k-leer").apply { mkdirs() }
        val none = File(filesDir, "${LocalAccountData.QUARANTINE}/quittungen-k-leer")
        assertFalse(LocalAccountData.quarantine(empty, none, "https://verein.example", "fiktiv@example.org", alias, now))
        assertFalse(none.exists())
        assertFalse(empty.exists())
    }

    @Test
    fun quarantaeneOhneLesbarenEigentuemerWirdErstNach120TagenGeloescht() {
        val missing = File(filesDir, "${LocalAccountData.QUARANTINE}/entwuerfe-k-ohne").apply { mkdirs() }
        File(missing, "eins.bin").writeBytes(seal("Erfundener Beleg"))
        val broken = File(filesDir, "${LocalAccountData.QUARANTINE}/quittungen-k-kaputt").apply { mkdirs() }
        File(broken, ".konto").writeText("unlesbar")
        File(broken, "zwei.bin").writeBytes(seal("Erfundene Quittung"))
        listOf(missing, broken).forEach { assertTrue(it.setLastModified(now - 10 * day)) }

        // Auch ohne nachweisbaren Schlüssel nicht sofort löschen
        assertEquals(LocalAccountData.Sweep(0, 0), sweep(hasKey = { false }))
        assertTrue(missing.exists())
        assertTrue(broken.exists())
        assertEquals(0, LocalAccountData.staleCount(filesDir, now))
        assertNull(LocalAccountData.finalDeletion(filesDir, now))

        listOf(missing, broken).forEach { assertTrue(it.setLastModified(now - 100 * day)) }
        assertEquals(2, LocalAccountData.staleCount(filesDir, now))
        assertEquals(now + 20 * day, LocalAccountData.finalDeletion(filesDir, now))
        assertEquals(LocalAccountData.Sweep(0, 0), sweep(hasKey = { false }))

        listOf(missing, broken).forEach { assertTrue(it.setLastModified(now - 121 * day)) }
        assertEquals(LocalAccountData.Sweep(2, 0), sweep(hasKey = { false }))
        assertFalse(missing.exists())
        assertFalse(broken.exists())
    }

    @Test
    fun zweitesAbmeldenBehaeltAelteresDatum() {
        val target = target().apply { mkdirs() }
        File(target, "alt.bin").writeBytes(seal("Erfundener alter Entwurf"))
        File(target, ".konto").writeText("https://Verein.example\nFiktiv@Example.org\n$alias\n${now - 50 * day}")
        assertTrue(quarantine(source("neu.bin"), target))
        assertEquals(now - 50 * day, since(target))
        assertTrue(File(target, "alt.bin").exists())
        assertTrue(File(target, "neu.bin").exists())
    }

    @Test
    fun zweitesAbmeldenOhneEigentuemerNimmtOrdnerdatum() {
        val target = target().apply { mkdirs() }
        File(target, "alt.bin").writeBytes(seal("Erfundener alter Entwurf"))
        assertTrue(target.setLastModified(now - 30 * day))
        assertTrue(quarantine(source("neu.bin"), target))
        assertEquals(now - 30 * day, since(target))
    }
}
