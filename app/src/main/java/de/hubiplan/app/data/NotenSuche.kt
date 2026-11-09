package de.hubiplan.app.data

import java.text.Normalizer

/**
 * Suche über alle eigenen Mappen, auf dem Gerät (Konzept 4.5): ohne Groß-/Kleinschreibung,
 * Umlaute tolerant („ae“ findet „ä“), Wortanfänge („alte kam“ findet „Alte Kameraden“),
 * Nummern („27“ findet Marschbuch Nr. 27 zuerst).
 */
object NotenSuche {

    /** Fundstelle eines Stücks: in welcher Mappe, unter welcher Nummer. */
    data class Stelle(val mappe: Mappe, val item: FolderItem)

    /** Ein Treffer je Stück (Arrangement), mit allen Mappen, in denen es steht. */
    data class Treffer(val arrangement: Int, val item: FolderItem, val stellen: List<Stelle>, val rang: Int)

    fun normalize(text: String): String {
        val lower = text.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        // Übrige Akzente weg (é → e)
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }

    fun words(text: String): List<String> = normalize(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /**
     * Rang eines Stücks für die Anfrage, kleiner ist besser; null: kein Treffer.
     * 0 Nummer genau · 1 Nummer beginnt so · 2 Titel beginnt so · 3 alle Wörter als Wortanfang.
     */
    fun rang(query: String, item: FolderItem, mappe: Mappe? = null): Int? {
        val q = query.trim()
        if (q.isEmpty() || !item.isPiece) return null
        val number = normalize(item.number)
        val nq = normalize(q).replace(" ", "")
        if (number.isNotEmpty() && number == nq) return 0
        if (number.isNotEmpty() && nq.any { it.isDigit() } && number.startsWith(nq)) return 1
        val tokens = words(q)
        if (tokens.isEmpty()) return null
        val title = words(item.title)
        if (title.joinToString(" ").startsWith(tokens.joinToString(" "))) return 2
        val all = title + words(item.composer) + words(item.arranger) + words(item.arrangementName) +
            words(mappe?.name.orEmpty()) + words(item.number)
        return if (tokens.all { t -> all.any { it.startsWith(t) } }) 3 else null
    }

    /** Alle Treffer über [mappen], bestes zuerst, gleiches Stück nur einmal. */
    fun suche(query: String, mappen: List<Mappe>): List<Treffer> {
        val byArrangement = linkedMapOf<Int, MutableList<Pair<Stelle, Int>>>()
        for (mappe in mappen) {
            for (item in mappe.pieces) {
                val r = rang(query, item, mappe) ?: continue
                byArrangement.getOrPut(item.arrangement!!) { mutableListOf() } += Stelle(mappe, item) to r
            }
        }
        return byArrangement.map { (id, found) ->
            val best = found.minBy { it.second }
            Treffer(id, best.first.item, found.map { it.first }, best.second)
        }.sortedWith(compareBy({ it.rang }, { normalize(it.item.title) }))
    }

    /**
     * Schnellwahl per Nummer in einer Mappe: das Stück mit genau dieser Nummer, wenn keine
     * andere Nummer der Mappe mit ihr beginnt (dann ist die Eingabe eindeutig, Konzept 4.5).
     */
    fun eindeutigeNummer(eingabe: String, mappe: Mappe): FolderItem? {
        val n = normalize(eingabe)
        if (n.isEmpty()) return null
        val passend = mappe.pieces.filter { normalize(it.number).startsWith(n) }
        val genau = passend.firstOrNull { normalize(it.number) == n } ?: return null
        return genau.takeIf { passend.size == 1 }
    }

    /** Stück mit genau dieser Nummer (Bestätigen im Nummernfeld). */
    fun nummer(eingabe: String, mappe: Mappe): FolderItem? {
        val n = normalize(eingabe)
        return mappe.pieces.firstOrNull { n.isNotEmpty() && normalize(it.number) == n }
    }
}
