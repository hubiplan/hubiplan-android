package de.hubiplan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import de.hubiplan.app.data.CircularItem
import de.hubiplan.app.ui.*

/** Nur Debug: erfundene Daten mit den unveränderten produktiven Komponenten. */
class AccessibilityTestActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { HubiplanTheme(null) {
            var choice by remember { mutableStateOf<String?>(null) }
            var selected by remember { mutableIntStateOf(1) }
            var saved by remember { mutableStateOf(false) }
            Column {
                ChoicePicker("Account", listOf("cash" to "Cash"), choice, { it }) { choice = it }
                CircularRow(CircularItem(1, "Board notice"), selected == 1) { selected = 1 }
                CircularRow(CircularItem(2, "Event notice"), selected == 2) { selected = 2 }
                Button(onClick = { saved = true }) { Text("Save") }
                if (saved) StatusNotice(tr("Gespeichert."))
                Button(onClick = { saved = false }) { Text("Reset") }
            }
        } }
    }
}
