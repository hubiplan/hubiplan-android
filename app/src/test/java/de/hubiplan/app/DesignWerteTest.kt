package de.hubiplan.app

import de.hubiplan.app.data.Licenses
import de.hubiplan.app.ui.Abstand
import de.hubiplan.app.ui.GETOENT
import de.hubiplan.app.ui.NEUTRAL_DUNKEL
import de.hubiplan.app.ui.NEUTRAL_HELL
import de.hubiplan.app.ui.Neutral
import de.hubiplan.app.ui.RADIUS
import de.hubiplan.app.ui.RADIUS_DIALOG
import de.hubiplan.app.ui.RADIUS_GROSS
import de.hubiplan.app.ui.RADIUS_KLEIN
import de.hubiplan.app.ui.SYMBOLKOPF
import de.hubiplan.app.ui.Strich
import de.hubiplan.app.ui.Strichsymbole
import de.hubiplan.app.ui.ZEILE_HOEHE
import de.hubiplan.app.ui.Zustand
import de.hubiplan.app.ui.parseHexColor
import de.hubiplan.app.ui.zaehlerText
import de.hubiplan.app.ui.zustandFarben
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Gestaltung „Frisch mobil“: Die Werte-Datei und der Symbolsatz sind in Android und iOS dieselben
 * Dateien. Die Prüfsummen stehen in beiden Repos im Test; wer eine Datei ändert, ändert sie in
 * beiden Apps und zieht beide Tests nach.
 */
class DesignWerteTest {
    // Unit-Tests laufen im Ordner app/
    private val werteDatei = File("src/main/assets/design/werte.json")
    private val symbolDatei = File("src/main/assets/${Strichsymbole.ASSET}")
    private val werte: JsonObject by lazy { Json.parseToJsonElement(werteDatei.readText()).jsonObject }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun obj(vararg path: String): JsonObject = path.fold(werte) { o, key -> o.getValue(key).jsonObject }

    @Test
    fun dateienSindDieselbenWieInDerIosApp() {
        assertEquals("bd5ec229a52c6d716c822c52dc7d5cfcadee166524abfdfd37c504f42a41735c", sha256(werteDatei))
        assertEquals("856e26da5760d165a35744ec1671dbb137f9f74aee396c97f1b803c039299f51", sha256(symbolDatei))
    }

    @Test
    fun jedesSymbolHatEinenNamenUndJederNameEinSymbol() {
        val formen = Strichsymbole.lesen(symbolDatei.readText())
        assertEquals(formen.keys.sorted(), Strich.entries.map { it.schluessel }.sorted())
        assertEquals(Strich.entries.size, Strich.entries.map { it.schluessel }.toSet().size)
        for ((name, form) in formen) assertTrue("$name ist leer", form.strich.isNotEmpty() || form.flaeche.isNotEmpty())
    }

    /** Beide Apps lesen nur M, L, C und Z mit absoluten Koordinaten im Raster 24. */
    @Test
    fun pfadeNutzenNurDieVierBefehle() {
        val zahl = """-?\d+(\.\d+)?"""
        val teil = Regex("""M $zahl $zahl( (L $zahl $zahl|C $zahl $zahl $zahl $zahl $zahl $zahl))*( Z)?""")
        for ((name, form) in Strichsymbole.lesen(symbolDatei.readText())) for (d in form.strich + form.flaeche) {
            assertTrue("$name: $d", teil.matches(d))
            val zahlen = Regex(zahl).findAll(d).map { it.value.toDouble() }.toList()
            assertTrue("$name ragt aus dem Raster", zahlen.all { it in 0.0..Strichsymbole.RASTER.toDouble() })
        }
        val symbole = Json.parseToJsonElement(symbolDatei.readText()).jsonObject
        assertEquals(Strichsymbole.RASTER.toDouble(), symbole.getValue("raster").jsonPrimitive.double, 0.0)
        assertEquals(Strichsymbole.STRICH.toDouble(), symbole.getValue("strich").jsonPrimitive.double, 0.0)
    }

    @Test
    fun neutraleFarbenStimmenMitDerWerteDatei() {
        fun pruefen(n: Neutral, modus: String) {
            val o = obj("neutral", modus)
            fun farbe(key: String) = parseHexColor(o.getValue(key).jsonPrimitive.content)
            assertEquals(farbe("surface"), n.surface)
            assertEquals(farbe("onSurface"), n.onSurface)
            assertEquals(farbe("onSurfaceVariant"), n.onSurfaceVariant)
            assertEquals(farbe("containerLowest"), n.containerLowest)
            assertEquals(farbe("containerLow"), n.containerLow)
            assertEquals(farbe("container"), n.container)
            assertEquals(farbe("containerHigh"), n.containerHigh)
            assertEquals(farbe("containerHighest"), n.containerHighest)
            assertEquals(farbe("outline"), n.outline)
            assertEquals(farbe("outlineVariant"), n.outlineVariant)
            assertEquals(farbe("inverseSurface"), n.inverseSurface)
            assertEquals(farbe("inverseOnSurface"), n.inverseOnSurface)
            assertEquals(farbe("dim"), n.dim)
            assertEquals(farbe("bright"), n.bright)
        }
        pruefen(NEUTRAL_HELL, "hell")
        pruefen(NEUTRAL_DUNKEL, "dunkel")
    }

    @Test
    fun zustandsfarbenStimmenMitDerWerteDatei() {
        val namen = mapOf(
            Zustand.GUT to "gut", Zustand.HINWEIS to "hinweis", Zustand.WARNUNG to "warnung",
            Zustand.FEHLER to "fehler", Zustand.NEUTRAL to "neutral",
        )
        assertEquals(Zustand.entries.toSet(), namen.keys)
        for ((zustand, name) in namen) for ((dunkel, modus) in listOf(false to "hell", true to "dunkel")) {
            val o = obj("zustand", name, modus)
            val farben = zustandFarben(zustand, dunkel)
            assertEquals("$name $modus", parseHexColor(o.getValue("schrift").jsonPrimitive.content), farben.schrift)
            assertEquals("$name $modus", parseHexColor(o.getValue("flaeche").jsonPrimitive.content), farben.flaeche)
        }
    }

    @Test
    fun masseStimmenMitDerWerteDatei() {
        val radius = obj("radius")
        assertEquals(radius.getValue("klein").jsonPrimitive.int, RADIUS_KLEIN)
        assertEquals(radius.getValue("normal").jsonPrimitive.int, RADIUS)
        assertEquals(radius.getValue("gross").jsonPrimitive.int, RADIUS_GROSS)
        assertEquals(radius.getValue("blatt").jsonPrimitive.int, RADIUS_DIALOG)
        val abstand = obj("abstand")
        assertEquals(abstand.getValue("zeile").jsonPrimitive.int.toFloat(), Abstand.zeile.value, 0f)
        assertEquals(abstand.getValue("eng").jsonPrimitive.int.toFloat(), Abstand.eng.value, 0f)
        assertEquals(abstand.getValue("klein").jsonPrimitive.int.toFloat(), Abstand.klein.value, 0f)
        assertEquals(abstand.getValue("mittel").jsonPrimitive.int.toFloat(), Abstand.mittel.value, 0f)
        assertEquals(abstand.getValue("rand").jsonPrimitive.int.toFloat(), Abstand.rand.value, 0f)
        assertEquals(abstand.getValue("gross").jsonPrimitive.int.toFloat(), Abstand.gross.value, 0f)
        assertEquals(obj("ziel").getValue("zeile").jsonPrimitive.int, ZEILE_HOEHE)
        assertEquals(obj("symbol").getValue("kopf").jsonPrimitive.int, SYMBOLKOPF)
        assertEquals(obj("marke").getValue("getoent").jsonPrimitive.double, GETOENT.toDouble(), 0.0001)
    }

    @Test
    fun schriftInterLiegtBeiUndIstEingebunden() {
        assertEquals("Inter", obj("schrift").getValue("familie").jsonPrimitive.content)
        val schrift = File("src/main/res/font/inter.ttf")
        assertTrue(schrift.isFile)
        // Gekürzt auf lateinische Zeichen: deutlich unter 300 KB
        assertTrue(schrift.length() in 100_000..300_000)
        assertTrue("typography = HubiTypography" in File("src/main/java/de/hubiplan/app/ui/Theme.kt").readText())
        val lizenz = File("src/main/assets/${Licenses.FONT_LICENSE_ASSET}")
        assertTrue(lizenz.isFile)
        assertTrue("SIL Open Font License, Version 1.1" in lizenz.readText())
        assertTrue("Licenses.FONT_LICENSE_ASSET" in File("src/main/java/de/hubiplan/app/ui/LicensesScreen.kt").readText())
    }

    @Test
    fun zaehlerKuerztAbHundert() {
        assertEquals("7", zaehlerText(7))
        assertEquals("99", zaehlerText(99))
        assertEquals("99+", zaehlerText(100))
    }
}
