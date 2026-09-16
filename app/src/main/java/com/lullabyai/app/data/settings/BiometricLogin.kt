package com.lullabyai.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lullabyai.app.util.AppLock

/**
 * "เข้าสู่ระบบด้วยลายนิ้วมือ / ใบหน้า" — one account per phone.
 *
 * The app already stays signed in, so this only matters after logging out. When the enrolled
 * account logs out, AuthRepository.signOut stores that session's latest refresh token here
 * (EncryptedSharedPreferences, same scheme as DoctorDeviceBinding) and clears the session locally.
 * The login screen hands it back only after the phone's own face / fingerprint / screen lock.
 *
 * The key is single-use: Supabase rotates refresh tokens, so it is removed when taken and a fresh
 * one is saved at the next logout. A stale key fails once, is gone, and the parent uses a password.
 * ponytail: the key is protected by app-sandbox encryption plus the prompt, not bound to the
 * biometric in the Keystore — that would exclude Samsung face unlock (a "weak" biometric).
 */
object BiometricLogin {
    private const val PREFS_NAME = "nongrak_biometric_login"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_DECLINED = "declined_user_ids"
    private const val KEY_NAME = "display_name"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun enrolledUserId(context: Context): String? = prefs(context).getString(KEY_USER_ID, null)
    fun enrolledEmail(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)

    /** Name shown in "ต้องการเข้าสู่ระบบบัญชีนี้หรือไม่?"; saved at logout, so it is the latest one. */
    fun displayName(context: Context): String? = prefs(context).getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }

    /** A saved sign-in exists: the enrolled account logged out on this phone. */
    fun hasSavedSignIn(context: Context): Boolean = !prefs(context).getString(KEY_REFRESH, null).isNullOrBlank()

    /** One account per phone: enrolling replaces whoever was enrolled before, and their saved key. */
    fun enroll(context: Context, userId: String, email: String?) {
        prefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .remove(KEY_REFRESH)
            .apply()
    }

    fun saveRefreshToken(context: Context, token: String, displayName: String?) {
        prefs(context).edit().putString(KEY_REFRESH, token).putString(KEY_NAME, displayName).apply()
    }

    /** Reads and removes the key — it can only be used once. */
    fun takeRefreshToken(context: Context): String? {
        val p = prefs(context)
        val token = p.getString(KEY_REFRESH, null)
        p.edit().remove(KEY_REFRESH).apply()
        return token?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_USER_ID).remove(KEY_EMAIL).remove(KEY_REFRESH).apply()
    }

    fun decline(context: Context, userId: String) {
        val p = prefs(context)
        val declined = p.getStringSet(KEY_DECLINED, emptySet()).orEmpty() + userId
        p.edit().putStringSet(KEY_DECLINED, declined).apply()
    }

    /** Ask after sign-in only if the phone can check a biometric, this account isn't set up, and they haven't said no. */
    fun shouldOffer(context: Context, userId: String): Boolean =
        AppLock.isSupported() && AppLock.isDeviceSecure(context) &&
            enrolledUserId(context) != userId &&
            userId !in prefs(context).getStringSet(KEY_DECLINED, emptySet()).orEmpty()
}
