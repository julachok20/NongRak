package com.lullabyai.app.util

import android.content.Context
import android.content.Intent
import android.util.Base64
import com.linecorp.linesdk.LineApiResponseCode
import com.linecorp.linesdk.Scope
import com.linecorp.linesdk.auth.LineAuthenticationParams
import com.linecorp.linesdk.auth.LineLoginApi
import com.lullabyai.app.BuildConfig
import java.security.SecureRandom

/**
 * LINE Login, mirroring [GoogleAuthHelper]: hand back an id_token and let the repository turn it
 * into a Supabase session.
 *
 * The SDK is used rather than a browser OAuth flow because it does the app-to-app handoff — in
 * Thailand nearly every parent already has LINE installed, so they get a one-tap confirm instead
 * of typing a password into a Custom Tab.
 */
object LineAuthHelper {

    /** False when no Login channel is configured, so callers can hide the button. */
    val isConfigured: Boolean get() = BuildConfig.LINE_LOGIN_CHANNEL_ID.isNotBlank()

    /**
     * The nonce for the login attempt currently in flight.
     *
     * Held here between launching the intent and reading the result: the edge function replays it
     * to LINE, which refuses to validate an id_token minted for a different nonce. That is what
     * stops a token captured from an earlier sign-in being posted again later.
     */
    @Volatile
    private var pendingNonce: String? = null

    val nonce: String? get() = pendingNonce

    private fun newNonce(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Intent for the LINE account picker.
     *
     * OPENID_CONNECT is what makes LINE return an id_token at all; PROFILE supplies the display
     * name. Email is deliberately not requested — it needs a separate manual review in the LINE
     * console, and the server already copes without it.
     */
    fun signInIntent(context: Context): Intent {
        val n = newNonce()
        pendingNonce = n
        return LineLoginApi.getLoginIntent(
            context,
            BuildConfig.LINE_LOGIN_CHANNEL_ID,
            LineAuthenticationParams.Builder()
                .scopes(listOf(Scope.PROFILE, Scope.OPENID_CONNECT))
                .nonce(n)
                .build()
        )
    }

    /** The id_token from a finished login, or a failure naming what actually came back. */
    fun idTokenFromResult(data: Intent?): Result<String> {
        if (data == null) return Result.failure(IllegalStateException("line_cancelled"))
        val result = LineLoginApi.getLoginResultFromIntent(data)
        return when (result.responseCode) {
            LineApiResponseCode.SUCCESS -> {
                val token = result.lineIdToken?.rawString
                if (token.isNullOrBlank()) {
                    // Happens when OPENID_CONNECT was dropped from the scopes: login "succeeds"
                    // but there is no id_token, and a generic error would send someone hunting
                    // through the server logs for a client-side misconfiguration.
                    Result.failure(IllegalStateException("line_no_id_token"))
                } else Result.success(token)
            }
            LineApiResponseCode.CANCEL -> Result.failure(IllegalStateException("line_cancelled"))
            else -> Result.failure(
                IllegalStateException("line_error: ${result.errorData.message ?: result.responseCode}")
            )
        }
    }

    /** Clears the in-flight nonce once the result has been exchanged. */
    fun clearNonce() { pendingNonce = null }

    fun friendlyError(raw: String?, isThai: Boolean): String = when {
        raw == null -> if (isThai) "เข้าสู่ระบบด้วย LINE ไม่สำเร็จ" else "LINE sign-in failed"
        raw.contains("cancelled") ->
            if (isThai) "ยกเลิกการเข้าสู่ระบบ" else "Sign-in cancelled"
        raw.contains("not_configured") ->
            if (isThai) "ยังไม่ได้ตั้งค่า LINE Login" else "LINE Login is not configured"
        raw.contains("line_token_rejected") ->
            if (isThai) "LINE ปฏิเสธการยืนยันตัวตน กรุณาลองใหม่" else "LINE rejected the sign-in — please try again"
        // Anything else keeps the server's own words: the create_user / exchange failures name
        // the real cause, and replacing them with a generic message is how those bugs hide.
        else -> if (isThai) "เข้าสู่ระบบด้วย LINE ไม่สำเร็จ ($raw)" else "LINE sign-in failed ($raw)"
    }
}
