package com.lullabyai.app.util

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "ล็อกแอปด้วยใบหน้า / ลายนิ้วมือ" — the app asks for the phone's own face, fingerprint or screen-lock
 * PIN before showing anything.
 *
 * Uses the platform BiometricPrompt (API 28+) rather than adding androidx.biometric: the app's only
 * need is one prompt. On API 26–27 the setting is simply not offered.
 *
 * Face unlock on most Samsung phones is a "weak" biometric, so weak biometrics plus the device
 * credential are allowed — strong-only would silently exclude face unlock.
 */
object AppLock {
    /** Background time after which coming back asks again. Short trips (photo picker) don't. */
    const val RELOCK_AFTER_MS = 60_000L

    /** Unlocked for this process. Snapshot state, so the gate recomposes when it changes. */
    var unlocked by mutableStateOf(false)

    /** When the app last went to the background; null while in the foreground. */
    var backgroundedAt: Long? = null

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** A lock only makes sense on a phone that has a screen lock to check against. */
    fun isDeviceSecure(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    fun shouldRelock(backgroundedAt: Long?, now: Long): Boolean =
        backgroundedAt != null && now - backgroundedAt >= RELOCK_AFTER_MS

    /**
     * Shows the system prompt. [onResult] gets (true, null) on success, or (false, message) with the
     * system's own reason — null message when the person just dismissed it.
     */
    fun authenticate(context: Context, title: String, subtitle: String, onResult: (Boolean, String?) -> Unit) {
        if (!isSupported()) {
            onResult(false, "biometric_prompt_needs_android_9")
            return
        }
        // A prompt that cannot be shown must come back as a message, never take the app down: the
        // first build crashed on tap because USE_BIOMETRIC was missing from the manifest.
        try {
            showPrompt(context, title, subtitle, onResult)
        } catch (e: Exception) {
            onResult(false, "${e::class.simpleName}: ${e.message ?: "biometric_prompt_failed"}")
        }
    }

    private fun showPrompt(context: Context, title: String, subtitle: String, onResult: (Boolean, String?) -> Unit) {
        val builder = android.hardware.biometrics.BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            context.mainExecutor,
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                    onResult(true, null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    val cancelled = errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    onResult(false, if (cancelled) null else "${errString ?: "error"} ($errorCode)")
                }
            }
        )
    }
}
