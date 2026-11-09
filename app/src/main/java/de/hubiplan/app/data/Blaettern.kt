package de.hubiplan.app.data

/**
 * Stelle im Notenpult: Stück [piece] der Mappe, Seite [page] dieses Stücks. [half]: halbe Seite
 * gewendet, oben steht schon der Anfang der nächsten Seite, unten noch das Ende von [page]
 * (Konzept 4.2, „Halbe Seite wenden“).
 */
data class PultStelle(val piece: Int, val page: Int, val half: Boolean = false)

/** Blätterlogik ohne Android, damit sie sich prüfen lässt. */
object Blaettern {

    /**
     * Weiter. [pages]: Seitenzahl je Stück (mindestens 1), [step]: 2 bei zwei Seiten
     * nebeneinander, [halfTurn]: halbe Seite wenden. Am Ende eines Stücks geht es zum nächsten;
     * am Ende der Mappe bleibt die Stelle (null).
     */
    fun vor(at: PultStelle, pages: (Int) -> Int, pieces: Int, step: Int = 1, halfTurn: Boolean = false): PultStelle? {
        val count = pages(at.piece).coerceAtLeast(1)
        if (halfTurn && step == 1) {
            if (at.half) return PultStelle(at.piece, at.page + 1)
            if (at.page + 1 < count) return PultStelle(at.piece, at.page, half = true)
        } else if (at.page + step < count) {
            return PultStelle(at.piece, at.page + step)
        }
        return if (at.piece + 1 < pieces) PultStelle(at.piece + 1, 0) else null
    }

    /** Zurück; am Anfang eines Stücks auf die letzte Seite des vorigen. */
    fun zurueck(at: PultStelle, pages: (Int) -> Int, step: Int = 1): PultStelle? {
        if (at.half) return PultStelle(at.piece, at.page)
        if (at.page > 0) return PultStelle(at.piece, (at.page - step).coerceAtLeast(0))
        if (at.piece == 0) return null
        val prev = pages(at.piece - 1).coerceAtLeast(1)
        // Bei zwei Seiten nebeneinander links mit einer geraden Seite beginnen
        val last = if (step == 2) ((prev - 1) / 2) * 2 else prev - 1
        return PultStelle(at.piece - 1, last)
    }

    /** Ist dies die letzte Ansicht des Stücks (für „Nächstes: …“)? */
    fun letzteSeite(at: PultStelle, pages: Int, step: Int = 1): Boolean = !at.half && at.page + step >= pages.coerceAtLeast(1)
}

/** Tasten fürs Blättern (Konzept 4.2): Lautstärke, Bluetooth-Fußpedal, Kopfhörer. */
object PultTasten {
    const val VOR = 1
    const val ZURUECK = -1

    // Werte aus android.view.KeyEvent (hier als Zahlen, damit die Prüfung ohne Android läuft)
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val SPACE = 62
    const val ENTER = 66
    const val PAGE_UP = 92
    const val PAGE_DOWN = 93
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88

    /** VOR, ZURUECK oder 0 (Taste nicht fürs Blättern). [volume]: Lautstärketasten blättern. */
    fun richtung(keyCode: Int, volume: Boolean, swapVolume: Boolean = false): Int = when (keyCode) {
        DPAD_RIGHT, DPAD_DOWN, PAGE_DOWN, SPACE, ENTER, MEDIA_NEXT -> VOR
        DPAD_LEFT, DPAD_UP, PAGE_UP, MEDIA_PREVIOUS -> ZURUECK
        VOLUME_DOWN -> if (!volume) 0 else if (swapVolume) ZURUECK else VOR
        VOLUME_UP -> if (!volume) 0 else if (swapVolume) VOR else ZURUECK
        else -> 0
    }

    const val MITTE = 2

    /**
     * Tippen: linkes Drittel zurück, rechte zwei Drittel vor (große Vor-Fläche ist Absicht);
     * die Bildmitte holt die Kopfzeile ([MITTE]).
     */
    fun tippen(x: Float, y: Float, width: Float, height: Float): Int = when {
        width <= 0f || height <= 0f -> 0
        x < width / 3f -> ZURUECK
        x < width * 2f / 3f && y > height / 3f && y < height * 2f / 3f -> MITTE
        else -> VOR
    }
}
