package de.hubiplan.app.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import de.hubiplan.app.BuildConfig
import de.hubiplan.app.tr

/**
 * Passkey über den Credential Manager abrufen (Anmelden und Verwaltung freischalten).
 *
 * Das Telefon bietet nur Passkeys der Adresse an, die in den Optionen steht (`rpId`), und nur,
 * wenn diese Adresse die App unter `/.well-known/assetlinks.json` bestätigt. Die App
 * unterschreibt mit der Herkunft `android:apk-key-hash:…`; HUBIplan prüft sie (docs/app.md).
 */
object Passkeys {
    /** Die Person hat die Auswahl abgebrochen: kein Fehler, nur nichts tun. */
    class Cancelled : Exception()

    /**
     * Passkey wählen lassen. [context] muss die Activity sein. Liefert die Antwort als
     * WebAuthn-JSON; Fehler kommen als [ApiException] mit verständlichem Text.
     */
    suspend fun get(context: Context, optionsJson: String): String {
        val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(optionsJson)))
        val credential = try {
            CredentialManager.create(context).getCredential(context, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw Cancelled()
        } catch (e: NoCredentialException) {
            throw ApiException(0, noPasskeyText())
        } catch (e: GetCredentialProviderConfigurationException) {
            throw ApiException(0, unsupportedText())
        } catch (e: GetCredentialUnsupportedException) {
            throw ApiException(0, unsupportedText())
        } catch (e: GetCredentialException) {
            throw ApiException(0, tr("Der Passkey ließ sich nicht abrufen. Bitte erneut versuchen."))
        }
        if (credential !is PublicKeyCredential) {
            throw ApiException(0, tr("Der Passkey ließ sich nicht abrufen. Bitte erneut versuchen."))
        }
        return credential.authenticationResponseJson
    }

    internal fun noPasskeyText(): String =
        tr("Auf diesem Telefon ist kein Passkey für diese Adresse gespeichert. Richte ihn zuerst im Browser unter „Mein Konto › Anmeldung & Sicherheit“ ein.")

    /** Ohne Google Play gibt es Passkeys für Apps erst ab Android 14. */
    internal fun unsupportedText(foss: Boolean = BuildConfig.FLAVOR == "foss"): String =
        if (foss) {
            tr("Passkeys gehen in dieser App-Version erst ab Android 14. Bitte mit QR-Code oder Passwort verbinden.")
        } else {
            tr("Dieses Telefon unterstützt keine Passkeys. Bitte mit QR-Code oder Passwort verbinden.")
        }
}
