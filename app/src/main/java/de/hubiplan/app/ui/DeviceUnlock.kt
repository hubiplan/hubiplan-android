package de.hubiplan.app.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import de.hubiplan.app.tr

/** Fingerabdruck, Gesicht oder Geräte-PIN für die Verwaltung (Schritt A8). */
object DeviceUnlock {
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Hat das Telefon eine Bildschirmsperre, mit der sich entsperren lässt? */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Systemeinstellung zum Einrichten einer Bildschirmsperre öffnen (UX-Audit 08.11. A-17):
     * ab Android 11 direkt die Einrichtung (sie fragt zuerst nach PIN oder Muster), sonst die
     * Sperre bzw. die Sicherheitseinstellungen. false, wenn nichts davon geöffnet werden konnte.
     */
    fun openSetup(context: Context): Boolean = setupIntents().any { intent ->
        runCatching { context.startActivity(intent) }.isSuccess
    }

    internal fun setupIntents(): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= 30) {
            add(Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG or DEVICE_CREDENTIAL))
        }
        add(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }

    fun ask(context: Context, title: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val activity = context.findActivity() ?: return onError(tr("Entsperren ist hier nicht möglich."))
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(tr("Mit Fingerabdruck, Gesicht oder Geräte-PIN"))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
