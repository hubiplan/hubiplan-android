package de.hubiplan.app.data

import android.content.Context
import java.io.File

/** Nur Dateischlüssel im Saved State; Fotos und Zeichenstriche bleiben verschlüsselt,
 * kontogetrennt und ohne Backup im privaten App-Speicher. Kein automatisch sendbarer Entwurf.
 * Keine zeitgesteuerte Löschung: entfernen nur nach ausdrücklicher Entscheidung/Übernahme. */
class FormAttachments(context: Context, accountKey: String) {
    private val dir = LocalAccountData.dir(context, FOLDER, accountKey).apply { mkdirs() }
    private val alias = LocalAccountData.alias(accountKey)
    private fun file(key: String): File {
        require(key.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        return File(dir, key + ".bin")
    }
    fun write(key: String, bytes: ByteArray) {
        val target = file(key)
        val temp = File(dir, key + ".tmp")
        temp.writeBytes(DraftCrypto.seal(alias, bytes))
        LocalAccountData.replace(temp, target)
    }
    fun read(key: String): ByteArray = DraftCrypto.open(alias, file(key).readBytes())
    fun delete(key: String) { file(key).delete() }
    companion object { const val FOLDER = "formularanhaenge" }
}
