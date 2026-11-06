package de.hubiplan.app.ui

import androidx.compose.ui.unit.dp

/**
 * Einheitliche Abstände in der App, passend zu den Stufen der Weboberfläche
 * (--space-1 … --space-5). Neue Oberflächen nehmen diese Werte statt eigener Zahlen,
 * damit Listen, Formulare und Karten überall gleich wirken.
 */
object Abstand {
    /** Zwischen zwei Textzeilen eines Eintrags */
    val zeile = 2.dp
    val eng = 4.dp
    /** Zwischen Feldern und Knöpfen eines Formulars */
    val klein = 8.dp
    val mittel = 12.dp
    /** Innenrand von Seiten und Karten */
    val rand = 16.dp
    val gross = 24.dp
}
