package com.lullabyai.app.data.repository

import com.lullabyai.app.BuildConfig
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder

/** A parent's linked LINE account, as stored in `line_links`. */
@Serializable
data class LineLink(
    @SerialName("line_user_id") val lineUserId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("notify_enabled") val notifyEnabled: Boolean = true,
    @SerialName("linked_at") val linkedAt: String? = null
)

/** A live two-factor code, as returned by `issue_link_code`. */
@Serializable
data class LineLinkCode(
    @SerialName("code") val code: String,
    @SerialName("expires_at") val expiresAt: String? = null
)

/**
 * Someone has entered this parent's code on another device and is waiting to be let in.
 *
 * Nothing is linked at this point. The row exists so the phone can ask the parent, which is the
 * second factor: knowing the code is not enough without the handset.
 */
@Serializable
data class LineLinkRequest(
    @SerialName("id") val id: String,
    @SerialName("claimed_display_name") val displayName: String? = null,
    @SerialName("claimed_at") val claimedAt: String? = null
)

/**
 * Linking a NongRak account to the NongRak LINE OA, and pushing notifications to it.
 *
 * Pairing runs the "code" way round rather than through LINE Login: the app already knows who
 * the user is, so it asks the server for a short code, the parent sends that code to the OA,
 * and the webhook — the only party that has actually heard from LINE — writes the link. That
 * needs no LINE Login channel and no custom Supabase auth provider.
 */
object LineRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000 }
        }
    }

    /** False when no OA id is configured, so the UI can hide the entry point rather than 404. */
    val isConfigured: Boolean get() = BuildConfig.LINE_OA_ID.isNotBlank()

    /** The current link, or null when this account has never paired. */
    suspend fun status(): Result<LineLink?> = runCatching {
        supabase.from("line_links").select().decodeSingleOrNull<LineLink>()
    }

    /**
     * A fresh 8-character pairing code, valid for ten minutes.
     *
     * Asking again invalidates any previous code — see `line_issue_pair_code`. The RPC returns a
     * bare JSON string, so the surrounding quotes come off here.
     */
    suspend fun issuePairCode(): Result<String> = runCatching {
        supabase.postgrest.rpc("line_issue_pair_code").data.trim().trim('"')
    }

    /**
     * A fresh 8-digit two-factor code for linking from a device that is not this phone.
     *
     * Issuing cancels any previous live code, so only one is ever guessable at a time.
     */
    suspend fun issueLinkCode(): Result<LineLinkCode> = runCatching {
        // The RPC returns a one-row table, so PostgREST sends an array.
        supabase.postgrest.rpc("issue_link_code").decodeList<LineLinkCode>().first()
    }

    /**
     * The request waiting for this parent's approval, or null.
     *
     * Filtered on claimed_at rather than just "newest": a code nobody has entered yet is not a
     * request, and prompting for it would train the parent to approve without reading.
     */
    suspend fun pendingLinkRequest(): Result<LineLinkRequest?> = runCatching {
        supabase.from("line_link_codes").select {
            filter {
                exact("confirmed_at", null)
                exact("cancelled_at", null)
                filterNot("claimed_at", io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS, null)
                gt("expires_at", java.time.Instant.now().toString())
            }
            order("claimed_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<LineLinkRequest>()
    }

    /** Approves the request: only now does the LINE account actually link. */
    suspend fun confirmLinkCode(id: String): Result<Boolean> = runCatching {
        supabase.postgrest.rpc("confirm_link_code", buildJsonObject { put("p_id", id) })
            .data.trim().equals("true", ignoreCase = true)
    }

    /** Rejects the request, or withdraws a code the parent no longer wants live. */
    suspend fun cancelLinkCode(id: String): Result<Boolean> = runCatching {
        supabase.postgrest.rpc("cancel_link_code", buildJsonObject { put("p_id", id) })
            .data.trim().equals("true", ignoreCase = true)
    }

    /**
     * Deep link that opens the NongRak OA chat with [code] already typed, so the parent only has
     * to press send. Falling back to typing it by hand is why the alphabet excludes O/0 and I/1.
     */
    fun pairDeepLink(code: String): String {
        val oa = URLEncoder.encode(BuildConfig.LINE_OA_ID, "UTF-8")   // "@nongrak" -> "%40nongrak"
        return "https://line.me/R/oaMessage/$oa/?${URLEncoder.encode(code, "UTF-8")}"
    }

    /**
     * Trades a LINE id_token for a Supabase session, via the `line-auth` edge function.
     *
     * Returns access + refresh tokens. The exchange has to happen server-side because verifying
     * the id_token needs the Login channel secret and minting a session needs the service role —
     * neither of which may ever ship inside the APK.
     */
    suspend fun exchangeIdToken(
        idToken: String,
        nonce: String?,
        accountType: String
    ): Result<Pair<String, String>> = runCatching {
        val raw = http.post("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/line-auth") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("idToken", idToken)
                if (!nonce.isNullOrBlank()) put("nonce", nonce)
                put("accountType", accountType)
            }.toString())
        }.bodyAsText()

        val root = json.parseToJsonElement(raw).jsonObject
        // Surface the server's own wording. "sign-in failed" would hide the two failures that
        // actually happen in practice: a rejected LINE token, and a project that refuses the
        // synthetic .invalid email domain.
        root["error"]?.jsonPrimitive?.contentOrNull?.let { error(it) }
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?: error("line_auth_no_access_token")
        val refresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull
            ?: error("line_auth_no_refresh_token")
        access to refresh
    }

    /** Turns LINE notifications on or off without unlinking, for a parent who wants quiet. */
    suspend fun setNotifyEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id ?: error("not signed in")
        supabase.from("line_links").update(buildJsonObject { put("notify_enabled", enabled) }) {
            filter { eq("user_id", uid) }
        }
        Unit
    }

    /** Forgets the link entirely. The parent must pair again to get notifications back. */
    /**
     * Disconnects LINE, and kills any link code still in flight.
     *
     * One RPC rather than a delete here plus a cleanup there: an 8-digit code issued minutes ago
     * stays live for its full ten minutes, and a request already claimed on another device could
     * still be approved afterwards, silently re-linking the account the parent just disconnected.
     *
     * Returns true when a link actually existed, so the screen can tell the parent what happened
     * instead of reporting success whatever the outcome.
     */
    suspend fun unlink(): Result<Boolean> = runCatching {
        supabase.postgrest.rpc("unlink_line").data.trim().equals("true", ignoreCase = true)
    }

    /**
     * Pushes one line of text to the caller's own LINE.
     *
     * Returns false — never throws — when the user has not linked, has switched notifications
     * off, or has blocked the OA. A notification failing to reach LINE must never stop the
     * in-app notification from being written, which is still the source of truth.
     */
    suspend fun push(text: String): Boolean {
        if (!isConfigured) return false
        val token = runCatching { supabase.auth.currentAccessTokenOrNull() }.getOrNull()
        if (token.isNullOrBlank()) return false
        return runCatching {
            val raw = http.post("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/line-push") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("text", text) }.toString())
            }.bodyAsText()
            json.parseToJsonElement(raw).jsonObject["sent"]?.jsonPrimitive?.booleanOrNull ?: false
        }.getOrDefault(false)
    }
}
