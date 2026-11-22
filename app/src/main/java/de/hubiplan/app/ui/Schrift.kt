package de.hubiplan.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import de.hubiplan.app.R

/*
 * Schrift „Inter“ wie in der Weboberfläche (Gestaltung „Frisch mobil“, assets/design/werte.json).
 * Eine variable Schriftdatei (res/font/inter.ttf, SIL OFL 1.1, auf lateinische Zeichen gekürzt);
 * die Strichstärke kommt über die Achse „wght“. Die Größen bleiben die von Material 3 und
 * wachsen mit der Schriftgröße des Systems.
 */

/** Strichstärken, die die App benutzt. */
val InterSchrift = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

/** Alle Textstufen von Material 3 in der Schrift Inter. */
val HubiTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = InterSchrift),
        displayMedium = t.displayMedium.copy(fontFamily = InterSchrift),
        displaySmall = t.displaySmall.copy(fontFamily = InterSchrift),
        headlineLarge = t.headlineLarge.copy(fontFamily = InterSchrift),
        headlineMedium = t.headlineMedium.copy(fontFamily = InterSchrift),
        headlineSmall = t.headlineSmall.copy(fontFamily = InterSchrift),
        titleLarge = t.titleLarge.copy(fontFamily = InterSchrift),
        titleMedium = t.titleMedium.copy(fontFamily = InterSchrift),
        titleSmall = t.titleSmall.copy(fontFamily = InterSchrift),
        bodyLarge = t.bodyLarge.copy(fontFamily = InterSchrift),
        bodyMedium = t.bodyMedium.copy(fontFamily = InterSchrift),
        bodySmall = t.bodySmall.copy(fontFamily = InterSchrift),
        labelLarge = t.labelLarge.copy(fontFamily = InterSchrift),
        labelMedium = t.labelMedium.copy(fontFamily = InterSchrift),
        labelSmall = t.labelSmall.copy(fontFamily = InterSchrift),
    )
}
