package de.hubiplan.app

import de.hubiplan.app.data.Instance
import de.hubiplan.app.data.minAndroidVersion
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mindestversion je Plattform (Gesamtaudit 15.11., APP-11). Gleiche Fälle wie in hubiplan-ios
 * (MindestversionTests).
 */
class MindestversionTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun androidWertJePlattform() {
        val instance = json.decodeFromString<Instance>(
            """{"product":"HUBIplan","api_version":1,"min_app_version":3,"min_app_versions":{"android":7,"ios":2}}""",
        )
        assertEquals(7, instance.minAndroidVersion)
    }

    @Test
    fun aeltereInstanzOhneListe() {
        val instance = json.decodeFromString<Instance>(
            """{"product":"HUBIplan","api_version":1,"min_app_version":3}""",
        )
        assertEquals(3, instance.minAndroidVersion)
    }
}
