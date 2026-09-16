package com.lullabyai.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lullabyai.app.BuildConfig

/**
 * Where the self-hosted model lives, and the token to call it — typed on the device, never shipped.
 *
 * A key compiled into `BuildConfig` travels inside every APK and can be read straight out of it, so
 * a private model server reachable over WireGuard is configured here instead: the value exists only
 * on the phone that typed it, encrypted at rest through the Android Keystore.
 *
 * [AiEndpointConfig] is the in-memory copy the network clients read, because they are objects with
 * no Context. It is filled at startup and whenever the settings screen saves.
 */
object AiEndpointSettings {

    private const val PREFS_NAME = "nongrak_ai_endpoint"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_TOKEN = "token"
    private const val KEY_MODEL = "model"

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

    /** Reads the stored values into [AiEndpointConfig]. Safe to call on every app start. */
    fun init(context: Context) {
        val p = runCatching { prefs(context) }.getOrNull() ?: return
        AiEndpointConfig.endpoint = p.getString(KEY_ENDPOINT, "").orEmpty()
        AiEndpointConfig.token = p.getString(KEY_TOKEN, "").orEmpty()
        AiEndpointConfig.model = p.getString(KEY_MODEL, "").orEmpty()
    }

    fun save(context: Context, endpoint: String, token: String, model: String) {
        prefs(context).edit()
            .putString(KEY_ENDPOINT, endpoint.trim())
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_MODEL, model.trim())
            .apply()
        AiEndpointConfig.endpoint = endpoint.trim()
        AiEndpointConfig.token = token.trim()
        AiEndpointConfig.model = model.trim()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        AiEndpointConfig.endpoint = ""
        AiEndpointConfig.token = ""
        AiEndpointConfig.model = ""
    }
}

/**
 * The values the network clients actually use. A device setting wins over the build-time value, so
 * a release build with nothing compiled in works the moment an endpoint is typed in.
 */
object AiEndpointConfig {
    @Volatile var endpoint: String = ""
    @Volatile var token: String = ""
    @Volatile var model: String = ""

    val effectiveEndpoint: String get() = endpoint.ifBlank { BuildConfig.MEDGEMMA_ENDPOINT }
    val effectiveToken: String get() = token.ifBlank { BuildConfig.MEDGEMMA_TOKEN }
    val effectiveModel: String get() = model.ifBlank { BuildConfig.MEDGEMMA_MODEL }

    /** True when the endpoint came from this device rather than from the build. */
    val isDeviceConfigured: Boolean get() = endpoint.isNotBlank()

    /**
     * A WireGuard peer address is a private one. Traffic inside the tunnel is already encrypted, so
     * plain HTTP there is acceptable — but only inside it, which is why cleartext stays blocked for
     * everything else and is permitted in debug builds alone.
     */
    fun isPrivateHost(url: String): Boolean {
        val host = runCatching { java.net.URI(url.trim()).host }.getOrNull().orEmpty()
        if (host.isBlank()) return false
        return host == "localhost" ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.startsWith("127.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)
    }
}
