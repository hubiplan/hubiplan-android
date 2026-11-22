package de.hubiplan.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.tr
import de.hubiplan.app.trn

/** Teile des Reiters „Neues“ (Gestaltung „Frisch mobil“): alles, was der Verein mitteilt oder fragt. */
internal enum class NeuesTeil(private val labelText: () -> String) {
    MITTEILUNGEN({ tr("Mitteilungen") }),
    PINNWAND({ tr("Pinnwand") }),
    UMFRAGEN({ tr("Umfragen") });

    val label: String get() = labelText()
}

/** Teile, deren Modul an ist; Mitteilungen gibt es immer. */
internal fun neuesTeile(access: PortalAccess): List<NeuesTeil> = NeuesTeil.entries.filter {
    when (it) {
        NeuesTeil.MITTEILUNGEN -> true
        NeuesTeil.PINNWAND -> access.hasPinboard
        NeuesTeil.UMFRAGEN -> access.hasSurveys
    }
}

/** Bereiche, die unter „Neues“ statt unter „Ich“ stehen; null: der Bereich steht unter „Ich“. */
internal fun neuesTeil(bereich: IchBereich): NeuesTeil? = when (bereich) {
    IchBereich.PINNWAND -> NeuesTeil.PINNWAND
    IchBereich.UMFRAGEN -> NeuesTeil.UMFRAGEN
    else -> null
}

/** Gewählter Teil: der gewünschte, wenn es ihn für diesen Zugang gibt, sonst Mitteilungen. */
internal fun neuesWahl(teile: List<NeuesTeil>, wahl: String?): NeuesTeil =
    teile.firstOrNull { it.name == wahl } ?: NeuesTeil.MITTEILUNGEN

/** Beschriftung eines Teils: mit Zahl, wenn dort etwas wartet („Pinnwand (2)“). */
internal fun neuesBeschriftung(teil: NeuesTeil, anzahl: Int): String =
    if (anzahl > 0) "${teil.label} (${zaehlerText(anzahl)})" else teil.label

/**
 * Reiter „Neues“: Mitteilungen, Pinnwand und Umfragen an einem Ort. Gibt es nur Mitteilungen,
 * steht keine Auswahl darüber. [request]: Teil, den „Start“ öffnen will (Name eines [NeuesTeil]).
 */
@Composable
internal fun NeuesPane(access: PortalAccess, vm: AppViewModel, request: String? = null, onRequestHandled: () -> Unit = {}) {
    val teile = neuesTeile(access)
    var wahl by rememberSaveable(access.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(request) {
        if (request != null) {
            wahl = request
            onRequestHandled()
        }
    }
    // Eine Benachrichtigung öffnet eine Mitteilung: dorthin wechseln
    val openRequest by vm.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) { if (openRequest != null) wahl = null }
    val teil = neuesWahl(teile, wahl)
    val ungelesen = vm.unreadCirculars.collectAsStateWithLifecycle().value.size
    // Zahlen nur nebenbei; nach jedem Wechsel neu, damit Erledigtes verschwindet
    var pinnwandNeu by remember(access.id) { mutableStateOf(access.pinboardNew) }
    var umfragenOffen by remember(access.id) { mutableStateOf(access.surveysOpen) }
    LaunchedEffect(access.id, teil) {
        val api = vm.api() ?: return@LaunchedEffect
        if (NeuesTeil.PINNWAND in teile) {
            // Geöffnet gilt die Pinnwand als gesehen; sonst nur nachsehen
            if (teil == NeuesTeil.PINNWAND) {
                pinnwandNeu = 0
            } else {
                quietly(vm) { api.memberPinboard(access.id, seen = false) }?.let { pinnwandNeu = it.newCount }
            }
        }
        if (NeuesTeil.UMFRAGEN in teile) {
            quietly(vm) { api.memberSurveys(access.id) }?.let { list -> umfragenOffen = list.surveys.count { it.waiting } }
        }
    }
    val kopf: @Composable () -> Unit = {
        if (teile.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Abstand.rand),
                horizontalArrangement = Arrangement.spacedBy(Abstand.klein),
            ) {
                teile.forEach { t ->
                    val anzahl = when (t) {
                        NeuesTeil.MITTEILUNGEN -> ungelesen
                        NeuesTeil.PINNWAND -> pinnwandNeu
                        NeuesTeil.UMFRAGEN -> umfragenOffen
                    }
                    val ansage = if (anzahl > 0) "${t.label}, ${trn("1 offen", "{n} offen", anzahl)}" else t.label
                    FilterChip(
                        selected = teil == t,
                        onClick = { wahl = t.name },
                        label = { Text(neuesBeschriftung(t, anzahl)) },
                        modifier = Modifier.semantics { contentDescription = ansage },
                    )
                }
            }
        }
    }
    when (teil) {
        // Die Auswahl steht über der Liste, nicht über einer geöffneten Mitteilung
        NeuesTeil.MITTEILUNGEN -> CircularsPane(access, vm, kopf = kopf)
        // Ohne Liste und Detail: Lesebreite wie die übrigen Reiter
        NeuesTeil.PINNWAND -> Lesebreite {
            Column(Modifier.fillMaxSize()) {
                kopf()
                Box(Modifier.weight(1f)) { PinnwandSection(access, vm) }
            }
        }
        NeuesTeil.UMFRAGEN -> Lesebreite {
            Column(Modifier.fillMaxSize()) {
                kopf()
                Box(Modifier.weight(1f)) { SurveysSection(access, vm) }
            }
        }
    }
}
