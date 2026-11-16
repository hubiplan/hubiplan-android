package de.hubiplan.app

import de.hubiplan.app.data.AwardItem
import de.hubiplan.app.data.json
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Eigener Titel des Trägers einer Ehrung (hubiplan #518); alle Daten erfunden. */
class EhrungTitelTest {
    @Test
    fun titelInKlammernSonstNurEhrung() {
        val list = json.decodeFromString(
            ListSerializer(AwardItem.serializer()),
            """[{"id":1,"title":3,"title_name":"Ehrenmitglied","valid_from":"2020-01-11","display_title":"Ehrenhauptmann"},
                {"id":2,"title":3,"title_name":"Ehrenmitglied","valid_from":"2021-01-11"}]""",
        )
        assertEquals("Ehrenmitglied (Ehrenhauptmann)", list[0].label)
        assertEquals("Ehrenmitglied", list[1].label)
    }
}
