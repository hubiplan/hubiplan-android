package de.hubiplan.app.ui

import de.hubiplan.app.data.AdminUnit

// Rechte der Verwaltung in der App. Seit November 2026 hat HUBIplan je Bereich die Stufen
// „lesen“ (<bereich>.view) und „bearbeiten“ (<bereich>.edit), auch in Untereinheiten
// (hubiplan docs/berechtigungen.md). Solche Server melden „stufen“; ältere Server schicken
// nur die früheren Codes (events.manage, photos.manage, dept.*). Die App versteht beide.

const val EVENTS_VIEW = "events.view"
const val EVENTS_EDIT = "events.edit"
const val PHOTOS_EDIT = "photos.edit"
private const val DEPT_MEMBERS = "dept.members."

/** Bereich „Mitglieder“ zeigen: mindestens lesen. */
fun showsMembers(unit: AdminUnit): Boolean {
    val p = unit.permissions
    if (!unit.isDepartment) return p.any { it.startsWith("members.") }
    return if (unit.stufen) p.any { it.startsWith("members.") } else p.any { it.startsWith(DEPT_MEMBERS) }
}

/** Bereich „Termine“ zeigen: mit Stufen nur bei „lesen“, früher sah jede Rolle die Termine. */
fun showsEvents(unit: AdminUnit): Boolean =
    !unit.stufen || EVENTS_VIEW in unit.permissions || EVENTS_EDIT in unit.permissions

/** Fotos ändern: „bearbeiten“ im Bereich Fotos (früher photos.manage). */
fun canEditPhotos(permissions: List<String>): Boolean =
    PHOTOS_EDIT in permissions || PHOTOS_MANAGE in permissions

/** Kontaktdaten im Zug ändern: Mitglieder „bearbeiten“ (früher dept.members.edit). */
fun canEditDeptMembers(permissions: List<String>): Boolean =
    MEMBERS_EDIT in permissions || DEPT_MEMBERS_EDIT in permissions
