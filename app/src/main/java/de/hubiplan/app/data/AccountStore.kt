package de.hubiplan.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Angemeldetes Konto auf diesem Gerät. Später mehrere (mehrere Instanzen, Schritt A7). */
data class Account(
    val server: String,
    val token: String,
    val deviceId: Int,
    val email: String,
    val display: String,
    val selectedAccess: Int?,
)

private val Context.dataStore by preferencesDataStore(name = "konto")

private object Keys {
    val server = stringPreferencesKey("server")
    val token = stringPreferencesKey("token")
    val deviceId = intPreferencesKey("device_id")
    val email = stringPreferencesKey("email")
    val display = stringPreferencesKey("display")
    val selectedAccess = intPreferencesKey("selected_access")
    val theme = stringPreferencesKey("theme")
    val calendarOn = booleanPreferencesKey("kalender_an")
    val reminderMinutes = intPreferencesKey("erinnerung_minuten")
    val rsvpHints = booleanPreferencesKey("hinweis_rueckmeldung")
    val notified = stringSetPreferencesKey("hinweise_gezeigt")
    val lastSync = longPreferencesKey("letzter_abgleich")
}

/** Einstellungen der App auf diesem Gerät. */
data class AppSettings(
    val calendarOn: Boolean = false,
    // Persönliche Erinnerung im Gerätekalender; null = keine (Standard 1 Tag vorher)
    val reminderMinutes: Int? = DEFAULT_REMINDER_MINUTES,
    val rsvpHints: Boolean = true,
    val lastSync: Long? = null,
)

const val DEFAULT_REMINDER_MINUTES = 24 * 60

/**
 * Speichert das Konto. Der Geräteschlüssel liegt nur verschlüsselt vor; der Schlüssel
 * dazu steckt im Android-Keystore und lässt sich nicht aus dem Gerät herauslesen.
 */
class AccountStore(private val context: Context) {

    val account: Flow<Account?> = context.dataStore.data.map { it.toAccount() }

    /** Zuletzt geladene Vereinsfarben (für den Start ohne Netz). */
    val cachedTheme: Flow<Theme?> = context.dataStore.data.map { prefs ->
        prefs[Keys.theme]?.let { runCatching { json.decodeFromString(Theme.serializer(), it) }.getOrNull() }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            calendarOn = p[Keys.calendarOn] ?: false,
            reminderMinutes = (p[Keys.reminderMinutes] ?: DEFAULT_REMINDER_MINUTES).takeIf { it >= 0 },
            rsvpHints = p[Keys.rsvpHints] ?: true,
            lastSync = p[Keys.lastSync],
        )
    }

    suspend fun setCalendarOn(on: Boolean) = context.dataStore.edit { it[Keys.calendarOn] = on }

    suspend fun setReminderMinutes(minutes: Int?) = context.dataStore.edit { it[Keys.reminderMinutes] = minutes ?: -1 }

    suspend fun setRsvpHints(on: Boolean) = context.dataStore.edit { it[Keys.rsvpHints] = on }

    suspend fun markSynced(at: Long) = context.dataStore.edit { it[Keys.lastSync] = at }

    /** Hinweise „Rückmeldung fehlt“ nur einmal je Termin. */
    suspend fun notifiedKeys(): Set<String> = context.dataStore.data.map { it[Keys.notified] ?: emptySet() }.first()

    suspend fun addNotified(keys: Set<String>) = context.dataStore.edit {
        // Nur die letzten 200 behalten
        it[Keys.notified] = ((it[Keys.notified] ?: emptySet()) + keys).toList().takeLast(200).toSet()
    }

    private fun Preferences.toAccount(): Account? {
        val server = this[Keys.server] ?: return null
        val token = this[Keys.token]?.let { decrypt(it) } ?: return null
        return Account(
            server = server,
            token = token,
            deviceId = this[Keys.deviceId] ?: return null,
            email = this[Keys.email] ?: "",
            display = this[Keys.display] ?: "",
            selectedAccess = this[Keys.selectedAccess],
        )
    }

    suspend fun save(server: String, paired: Paired) {
        context.dataStore.edit {
            it[Keys.server] = server
            it[Keys.token] = encrypt(paired.token)
            it[Keys.deviceId] = paired.device.id
            it[Keys.email] = paired.user.email
            it[Keys.display] = paired.user.display
            it.remove(Keys.selectedAccess)
        }
    }

    suspend fun selectAccess(id: Int, theme: Theme?) {
        context.dataStore.edit {
            it[Keys.selectedAccess] = id
            if (theme != null) it[Keys.theme] = json.encodeToString(Theme.serializer(), theme)
        }
    }

    /** Abmelden: alles auf dem Gerät löschen. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_BYTES))
        String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES))
    }.getOrNull()

    private companion object {
        const val ALIAS = "hubiplan-geraeteschluessel"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
