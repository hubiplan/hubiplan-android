package de.hubiplan.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Gemeinsamer Symbolsatz der beiden Apps (Gestaltung „Frisch mobil“): Strichsymbole im Raster 24,
 * Strich 1,75, runde Enden. Die Formen stehen in `assets/design/symbole.json`; dieselbe Datei
 * liegt in der iOS-App, damit Anleitungen für beide gelten. Jedes Symbol hat hier einen Namen,
 * `DesignWerteTest` hält Liste und Datei zusammen.
 */
enum class Strich(val schluessel: String) {
    START("start"), TERMINE("termine"), NEUES("neues"), ICH("ich"), VERWALTUNG("verwaltung"),
    NOTEN("noten"), AUFGABEN("aufgaben"),
    AUSWEIS("ausweis"), EINCHECKEN("einchecken"), KALENDER_ABO("kalender-abo"), GELD("geld"),
    RECHNUNG("rechnung"), NACHWEIS("nachweis"), BESTELLUNG("bestellung"), TRAINING("training"),
    WETTBEWERB("wettbewerb"), HELFER("helfer"), ABWESENHEIT("abwesenheit"), BELEGUNG("belegung"),
    VERSAMMLUNG("versammlung"), DOKUMENT("dokument"), ORDNER("ordner"), MEINE_DATEN("meine-daten"),
    PINNWAND("pinnwand"), UMFRAGE("umfrage"), MITTEILUNG("mitteilung"), FIRMA("firma"),
    MITGLIEDER("mitglieder"), ANTRAG("antrag"), JUBILAEUM("jubilaeum"), QUALIFIKATION("qualifikation"),
    FRIST("frist"), PARTNER("partner"), KASSENBUCH("kassenbuch"), EINLASS("einlass"),
    ZU_ERLEDIGEN("zu-erledigen"),
    PLUS("plus"), SUCHE("suche"), KAMERA("kamera"), SCANNEN("scannen"), HILFE("hilfe"),
    WALLET("wallet"), HELLIGKEIT("helligkeit"), ORT("ort"), UHR("uhr"), TELEFON("telefon"),
    TEILEN("teilen"), EINSTELLUNGEN("einstellungen"), SPERRE("sperre"), OFFLINE("offline"),
    WEITER("weiter"), ZURUECK("zurueck"), UNTEN("unten"), HAKEN("haken"), SCHLIESSEN("schliessen"),
    INFO("info"), WARNUNG("warnung"), KONTO("konto"),
}

object Strichsymbole {
    const val ASSET = "design/symbole.json"
    const val RASTER = 24f
    const val STRICH = 1.75f

    /** Teilpfade eines Symbols: mit Strich gezeichnet und (selten) gefüllt. */
    data class Form(val strich: List<String>, val flaeche: List<String>)

    /** Liest `symbole.json`: Name → Form. */
    fun lesen(json: String): Map<String, Form> {
        val symbole = Json.parseToJsonElement(json).jsonObject["symbole"]?.jsonObject ?: return emptyMap()
        fun pfade(o: JsonObject, key: String) = o[key]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        return symbole.mapValues { (_, v) -> Form(pfade(v.jsonObject, "strich"), pfade(v.jsonObject, "flaeche")) }
    }

    /** Vektorbild aus der Form; die Farbe setzt erst `Icon` (Tönung). */
    fun bild(name: String, form: Form): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = RASTER, viewportHeight = RASTER).apply {
            for (d in form.strich) {
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STRICH,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            for (d in form.flaeche) addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
        }.build()

    @Volatile
    private var geladen: Map<String, ImageVector>? = null

    /** Alle Symbole, einmal aus den Assets gelesen. */
    fun alle(context: Context): Map<String, ImageVector> = geladen ?: synchronized(this) {
        geladen ?: runCatching {
            context.applicationContext.assets.open(ASSET).bufferedReader().use { lesen(it.readText()) }
        }.getOrDefault(emptyMap()).mapValues { (name, form) -> bild(name, form) }.also { geladen = it }
    }
}

/** Das Strichsymbol als Vektorbild für `Icon`. Fehlt die Datei, bleibt ein leeres Bild. */
@Composable
fun strichsymbol(symbol: Strich): ImageVector {
    val context = LocalContext.current
    return remember(symbol) {
        Strichsymbole.alle(context)[symbol.schluessel]
            ?: Strichsymbole.bild(symbol.schluessel, Strichsymbole.Form(emptyList(), emptyList()))
    }
}
