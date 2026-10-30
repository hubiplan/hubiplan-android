package de.hubiplan.app.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Buchungsentwürfe der Kasse (Schritt B4), solange sie nicht gesendet sind: die einzige
 * Ausnahme von „keine Verwaltungsdaten auf dem Gerät“. Jede Datei ist mit einem Schlüssel aus
 * dem Android-Keystore verschlüsselt (nur nutzbar, solange das Telefon entsperrt ist), liegt im
 * App-Speicher ohne Sicherung und wird nach dem Senden gelöscht. Je Konto ein eigener Ordner.
 */
class DraftStore(context: Context, accountKey: String) {
    private val dir = File(context.filesDir, "entwuerfe/" + accountKey.filter { it.isLetterOrDigit() }.take(60)).apply { mkdirs() }

    fun list(): List<BookingDraft> = dir.listFiles().orEmpty()
        .filter { it.name.endsWith(".bin") }
        .sortedBy { it.lastModified() }
        .mapNotNull { file -> runCatching { decodeDraft(String(Crypto.open(file.readBytes()))) }.getOrNull() }

    fun save(draft: BookingDraft) {
        val file = File(dir, safe(draft.key) + ".bin")
        val tmp = File(dir, safe(draft.key) + ".tmp")
        tmp.writeBytes(Crypto.seal(encodeDraft(draft).toByteArray()))
        tmp.renameTo(file)
    }

    fun delete(key: String) {
        File(dir, safe(key) + ".bin").delete()
    }

    private fun safe(key: String) = key.filter { it.isLetterOrDigit() || it == '-' }.take(64)

    private object Crypto {
        private const val ALIAS = "hubiplan-entwuerfe"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12

        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            // Nur bei entsperrtem Telefon lesbar (ab Android 9)
            if (Build.VERSION.SDK_INT >= 28) builder.setUnlockedDeviceRequired(true)
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(builder.build())
            return generator.generateKey()
        }

        fun seal(plain: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            return cipher.iv + cipher.doFinal(plain)
        }

        fun open(data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_BYTES))
            return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
        }
    }
}

fun encodeDraft(draft: BookingDraft): String = json.encodeToString(BookingDraft.serializer(), draft)

fun decodeDraft(text: String): BookingDraft = json.decodeFromString(BookingDraft.serializer(), text)
