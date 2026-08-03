package com.lullabyai.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.lullabyai.app.BuildConfig

/**
 * Classic Google Sign-In (play-services-auth). More reliable on real devices/emulators than
 * Credential Manager alone, which often returns CancellationException before showing the picker.
 */
object GoogleAuthHelper {

    fun signInIntent(context: Context): Intent {
        val client = GoogleSignIn.getClient(context, options())
        // Clear stale one-tap session so the account chooser appears.
        runCatching { client.signOut() }
        return client.signInIntent
    }

    fun idTokenFromResult(data: Intent?): Result<String> = runCatching {
        if (data == null) error("ยกเลิกการเข้าสู่ระบบ")
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            throw IllegalStateException(friendlyApiStatus(e.statusCode))
        }
        account.idToken?.takeIf { it.isNotBlank() } ?: error("missing_id_token")
    }

    private fun friendlyApiStatus(status: Int): String = when (status) {
        12501 -> "ยกเลิกการเข้าสู่ระบบ"
        10 -> "developer_error"
        7 -> "network_error"
        else -> "google_sign_in_failed_$status"
    }

    fun options(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID.trim())
            .requestEmail()
            .build()

    fun friendlyError(raw: String?, isThai: Boolean): String {
        val msg = raw?.lowercase().orEmpty()
        return when {
            msg.isBlank() -> if (isThai) "เข้าสู่ระบบด้วย Google ไม่สำเร็จ" else "Google sign-in failed"
            "ยกเลิก" in msg || "cancel" in msg ->
                if (isThai) "ยกเลิกการเข้าสู่ระบบ" else "Sign-in cancelled"
            "nonce" in msg || "id_token" in msg || "invalid" in msg && "token" in msg ||
                "developer_error" in msg ->
                if (isThai) "การยืนยัน Google ไม่สำเร็จ — ตรวจสอบ SHA-1 ของแอปใน Google Cloud Console"
                else "Google token rejected — check app SHA-1 in Google Cloud Console"
            "network_error" in msg ->
                if (isThai) "เชื่อมต่ออินเทอร์เน็ตไม่ได้ กรุณาลองใหม่" else "Network error — try again"
            "provider" in msg && "disabled" in msg ->
                if (isThai) "เปิดใช้ Google ใน Supabase Dashboard → Authentication → Providers"
                else "Enable Google provider in Supabase Dashboard → Authentication → Providers"
            "email_exists_use_password" in msg ->
                if (isThai) "อีเมลนี้มีบัญชีอยู่แล้ว — เข้าสู่ระบบด้วยอีเมลและรหัสผ่านครั้งแรก จากนั้น Google จะเชื่อมกับบัญชีเดิม"
                else "This email already has an account — sign in with email and password once, then Google will link to the same account"
            else -> raw ?: if (isThai) "เข้าสู่ระบบด้วย Google ไม่สำเร็จ" else "Google sign-in failed"
        }
    }
}
