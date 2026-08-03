package com.lullabyai.app.data.doctor

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * One doctor account per device.
 *
 * Stores the bound doctor user id in EncryptedSharedPreferences. A second doctor signup/signin
 * on the same device is rejected until the bound account is deleted (which clears the binding).
 * "Just log out" keeps the binding so only the same doctor can return on this device.
 */
object DoctorDeviceBinding {

    private const val PREFS_NAME = "nongrak_doctor_device_binding"
    private const val KEY_BOUND_DOCTOR_ID = "bound_doctor_user_id"

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

    fun getBoundDoctorId(context: Context): String? =
        prefs(context).getString(KEY_BOUND_DOCTOR_ID, null)?.takeIf { it.isNotBlank() }

    /** True when no doctor is bound yet, or [doctorId] is the already-bound account. */
    fun canUseDoctorAccount(context: Context, doctorId: String): Boolean {
        val bound = getBoundDoctorId(context) ?: return true
        return bound == doctorId
    }

    /** True when this device already has a different doctor bound (blocks new signup). */
    fun isBoundToAnotherDoctor(context: Context): Boolean =
        getBoundDoctorId(context) != null

    /**
     * Bind [doctorId] to this device. Fails if another doctor is already bound.
     * Idempotent when the same id is already stored.
     */
    fun bindDoctor(context: Context, doctorId: String): Result<Unit> = runCatching {
        val bound = getBoundDoctorId(context)
        when {
            bound == null -> prefs(context).edit().putString(KEY_BOUND_DOCTOR_ID, doctorId).apply()
            bound == doctorId -> Unit
            else -> error("DEVICE_BOUND_OTHER_DOCTOR")
        }
    }

    /** Clears the device binding — call only on permanent account deletion. */
    fun clearBinding(context: Context) {
        prefs(context).edit().remove(KEY_BOUND_DOCTOR_ID).apply()
    }

    fun deviceBlockedMessage(isThai: Boolean): String =
        if (isThai)
            "อุปกรณ์นี้ผูกกับบัญชีแพทย์อื่นแล้ว ไม่สามารถสมัครหรือเข้าสู่ระบบด้วยบัญชีแพทย์ใหม่บนอุปกรณ์เครื่องนี้ได้ กรุณาลบบัญชีเดิมจากเมนูตั้งค่า (ออกจากระบบ → ลบบัญชีทันที) ก่อน"
        else
            "This device is already linked to another doctor account. You cannot create or sign in with a second doctor account on this device. Delete the existing account from Settings (Sign out → Delete account now) first."
}
