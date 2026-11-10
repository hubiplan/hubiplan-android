package de.hubiplan.app

import de.hubiplan.app.data.Licenses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Lizenzaudit 31.10.2026: Lizenzseite der App (L01) und Lizenzbeilagen (L09). */
class LicensesTest {
    @Test
    fun liestDieBibliotheksliste() {
        val libs = Licenses.parse(
            "com.squareup.okhttp3:okhttp:4.12.0\tApache-2.0\n" +
                "com.google.android.gms:play-services-base:18.0.1\tAndroid-SDK\n" +
                "kaputt\n\n" +
                "androidx.core:core:1.15.0\tApache-2.0\n",
        )
        assertEquals(3, libs.size)
        val grouped = Licenses.byLicense(libs)
        assertEquals(listOf("Apache-2.0", "Android-SDK"), grouped.map { it.first })
        assertEquals("androidx.core:core:1.15.0", grouped[0].second.first().coordinate)
    }

    @Test
    fun lizenztexteLiegenBei() {
        // Unit-Tests laufen im Ordner app/
        for ((id, info) in Licenses.KNOWN) {
            val asset = info.asset ?: continue
            val file = File("src/main/assets/$asset")
            assertTrue("Lizenztext fehlt für $id: $file", file.isFile)
        }
        assertTrue(File("src/main/assets/lizenzen/Apache-2.0.txt").readText().contains("Version 2.0, January 2004"))
        assertTrue(File("src/main/assets/lizenzen/MPL-2.0.txt").readText().contains("Mozilla Public License Version 2.0"))
        assertTrue(File("../gradle/wrapper/LICENSE-Apache-2.0.txt").isFile)
        assertNotNull(Licenses.KNOWN["Android-SDK"])
    }

    /** Lizenzaudit 10.11.2026, L02: Der Link führt zum Quellcode genau dieser Version. */
    @Test
    fun quellcodeLinkZeigtAufDenTagDerVersion() {
        assertEquals("https://github.com/hubiplan/hubiplan-android/tree/v1.3.0", Licenses.sourceUrl("1.3.0"))
    }

    /** Lizenzaudit 10.11.2026, L08: Quellform der Public Suffix List zur eingebauten OkHttp-Version. */
    @Test
    fun publicSuffixListMitQuelleDerEingebautenFassung() {
        val libs = Licenses.parse("com.squareup.okhttp3:okhttp:4.12.0\tApache-2.0\n")
        assertEquals(
            "https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/resources/okhttp3/internal/publicsuffix/publicsuffixes.gz",
            Licenses.publicSuffixSource(libs),
        )
        assertEquals(null, Licenses.publicSuffixSource(emptyList()))
        // Der Pfad gilt für OkHttp 4.x; bei einem Wechsel auf 5.x Licenses.publicSuffixSource anpassen
        val version = Regex("""(?m)^okhttp = "([^"]+)"""").find(File("../gradle/libs.versions.toml").readText())
        assertTrue("OkHttp-Version nicht gefunden", version != null)
        assertTrue("Pfad der Public Suffix List für OkHttp ${version!!.groupValues[1]} prüfen", version.groupValues[1].startsWith("4."))
    }

    /** Lizenzaudit 10.11.2026, L03: Die Zusatzerlaubnis nimmt nur die Google-Bibliotheken aus. */
    @Test
    fun zusatzerlaubnisNimmtDenAppCodeNichtAus() {
        val readme = File("../README.md").readText().replace(Regex("\\s*\n>\\s*"), " ")
        assertTrue(readme.contains("need not include the source code for the parts of those libraries used, but shall include the source code of the covered work"))
        assertTrue(!readme.contains("shall not include the source code for the parts of those libraries used as well as"))
    }
}
