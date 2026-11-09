package de.hubiplan.app.data

import android.content.Context

/** Einstellungen des Notenpults, nur auf diesem Gerät (Konzept 4.7). */
data class PultEinstellungen(
    /** Lautstärketasten blättern (E9: an) */
    val volumeKeys: Boolean = true,
    /** Lautstärketasten vertauscht (lauter = vor) */
    val swapVolume: Boolean = false,
    /** Tippen am Rand blättert */
    val tap: Boolean = true,
    /** Wischen blättert */
    val swipe: Boolean = true,
    /** Halbe Seite wenden */
    val halfTurn: Boolean = false,
    /** Querformat auf dem Tablet: zwei Seiten nebeneinander */
    val twoPages: Boolean = true,
    /** Weiße Ränder abschneiden */
    val crop: Boolean = true,
    /** Nachtmodus: Farben umkehren */
    val night: Boolean = false,
    /** Volle Helligkeit */
    val bright: Boolean = false,
    /** Drehung auf die Lage beim Öffnen sperren */
    val lockRotation: Boolean = false,
    /** Live-Vorgabe: nach 3 Sekunden selbst springen (E15), sonst nur anzeigen */
    val autoJump: Boolean = true,
    /** Termin-Mappen am Vortag über WLAN laden (E10) */
    val prefetch: Boolean = true,
    /** Hinweis „Bitte nicht stören“ schon gezeigt */
    val dndHintShown: Boolean = false,
    /** Notengröße für vom Server neu gesetzte Noten (MusicXML), Verovio-Skala 25–120 */
    val noteSize: Int = 50,
    /** Zuletzt genutzter Satz („1778x50“), damit Vorladen gleich die passende Form holt */
    val lastLayout: String = "",
) {
    fun save(context: Context) {
        prefs(context).edit()
            .putBoolean("volume", volumeKeys).putBoolean("swap", swapVolume).putBoolean("tap", tap)
            .putBoolean("swipe", swipe).putBoolean("half", halfTurn).putBoolean("two", twoPages)
            .putBoolean("crop", crop).putBoolean("night", night).putBoolean("bright", bright)
            .putBoolean("rotation", lockRotation).putBoolean("jump", autoJump).putBoolean("prefetch", prefetch)
            .putBoolean("dnd", dndHintShown).putInt("size", noteSize).putString("layout", lastLayout)
            .apply()
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("notenpult", Context.MODE_PRIVATE)

        fun load(context: Context): PultEinstellungen {
            val p = prefs(context)
            val d = PultEinstellungen()
            return PultEinstellungen(
                volumeKeys = p.getBoolean("volume", d.volumeKeys),
                swapVolume = p.getBoolean("swap", d.swapVolume),
                tap = p.getBoolean("tap", d.tap),
                swipe = p.getBoolean("swipe", d.swipe),
                halfTurn = p.getBoolean("half", d.halfTurn),
                twoPages = p.getBoolean("two", d.twoPages),
                crop = p.getBoolean("crop", d.crop),
                night = p.getBoolean("night", d.night),
                bright = p.getBoolean("bright", d.bright),
                lockRotation = p.getBoolean("rotation", d.lockRotation),
                autoJump = p.getBoolean("jump", d.autoJump),
                prefetch = p.getBoolean("prefetch", d.prefetch),
                dndHintShown = p.getBoolean("dnd", d.dndHintShown),
                noteSize = p.getInt("size", d.noteSize).coerceIn(25, 120),
                lastLayout = p.getString("layout", d.lastLayout).orEmpty(),
            )
        }
    }
}

/** Ist gerade ein Notenpult offen? Dann zeigt die App keine eigenen Benachrichtigungen (E6). */
object PultOffen {
    @Volatile
    var offen: Boolean = false
}
