package com.lullabyai.app.data.remote

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.lullabyai.app.NongRakApp
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HeaderValueParam
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * Doctor patient **detailed** clinical records in Google Drive (app-data folder), not Supabase.
 *
 * Auth: Google Play Services [GoogleAuthUtil] with `drive.appdata` scope. AccountManager alone
 * often returns null on modern Android even when Google accounts exist on the device.
 */
object DoctorPatientDriveHelper {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 45_000; socketTimeoutMillis = 45_000 }
    }

    private const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
    private const val FILE_PREFIX = "nr_patient_"
    private const val PREFS_NAME = "doctor_drive_prefs"
    private const val KEY_ACCOUNT = "account_email"

    /** Google account chosen for Drive — persisted across sessions. */
    @Volatile
    private var preferredAccountName: String? = null

    private fun prefs() =
        NongRakApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedAccount(): String? {
        preferredAccountName?.let { saved ->
            if (googleAccounts().any { it.equals(saved, ignoreCase = true) }) return saved
        }
        val stored = prefs().getString(KEY_ACCOUNT, null) ?: return null
        return stored.takeIf { googleAccounts().any { it.equals(stored, ignoreCase = true) } }
            ?.also { preferredAccountName = it }
    }

    private fun saveAccount(email: String) {
        preferredAccountName = email
        prefs().edit().putString(KEY_ACCOUNT, email).apply()
    }

    fun clearSavedAccount() {
        preferredAccountName = null
        prefs().edit().remove(KEY_ACCOUNT).apply()
    }

    enum class DriveFailure {
        NO_PLAY_SERVICES,
        NO_GOOGLE_ACCOUNTS,
        EMAIL_MISMATCH,
        AUTH_FAILED
    }

    sealed class DriveConnectResult {
        data object Success : DriveConnectResult()
        data class NeedsAccountChoice(val accounts: List<String>) : DriveConnectResult()
        data class NeedsConsent(val intent: Intent, val accountEmail: String) : DriveConnectResult()
        data class Failed(val reason: DriveFailure, val loginEmail: String?, val deviceAccounts: List<String>) : DriveConnectResult()
    }

    @Serializable
    data class PatientDetailRecord(
        val id: String,
        val doctorId: String,
        val childId: String,
        val appointmentId: String? = null,
        val childName: String? = null,
        val symptoms: String = "",
        val diagnosis: String = "",
        val treatmentPlan: String = "",
        val weightKg: Double? = null,
        val heightCm: Double? = null,
        val temperatureC: Double? = null,
        val nextAppointmentDate: String? = null,
        val recordedAt: String = Instant.now().toString()
    )

    data class DriveFileMeta(
        val id: String,
        val name: String,
        val modifiedTime: String? = null,
        val childId: String? = null
    )

    suspend fun isDriveAvailable(): Boolean = obtainToken(allowConsentPrompt = false) != null

    /** User-initiated connect — pick account when several exist; may return consent [Intent]. */
    suspend fun requestConnect(
        accountEmail: String? = null,
        forceAccountPicker: Boolean = false
    ): DriveConnectResult = withContext(Dispatchers.IO) {
        val ctx = NongRakApp.instance
        if (!isPlayServicesAvailable(ctx)) {
            return@withContext failed(DriveFailure.NO_PLAY_SERVICES)
        }
        val accounts = googleAccounts()
        if (accounts.isEmpty()) {
            return@withContext failed(DriveFailure.NO_GOOGLE_ACCOUNTS)
        }

        when {
            !accountEmail.isNullOrBlank() -> connectAccount(accountEmail, accounts)
            forceAccountPicker -> DriveConnectResult.NeedsAccountChoice(accounts)
            getSavedAccount() != null -> connectAccount(getSavedAccount()!!, accounts)
            accounts.size > 1 -> DriveConnectResult.NeedsAccountChoice(accounts)
            else -> connectAccount(accounts.first(), accounts)
        }
    }

    private fun connectAccount(accountEmail: String, deviceAccounts: List<String>): DriveConnectResult {
        val ctx = NongRakApp.instance
        if (deviceAccounts.none { it.equals(accountEmail, ignoreCase = true) }) {
            return DriveConnectResult.Failed(
                DriveFailure.AUTH_FAILED,
                supabase.auth.currentUserOrNull()?.email,
                deviceAccounts
            )
        }
        return try {
            val token = GoogleAuthUtil.getToken(ctx, accountEmail, DRIVE_SCOPE)
            if (!token.isNullOrBlank()) {
                saveAccount(accountEmail)
                DriveConnectResult.Success
            } else {
                failed(DriveFailure.AUTH_FAILED)
            }
        } catch (e: UserRecoverableAuthException) {
            val consentIntent = e.intent
            if (consentIntent != null) {
                DriveConnectResult.NeedsConsent(consentIntent, accountEmail)
            } else {
                failed(DriveFailure.AUTH_FAILED)
            }
        } catch (_: GoogleAuthException) {
            failed(DriveFailure.AUTH_FAILED)
        } catch (_: Exception) {
            failed(DriveFailure.AUTH_FAILED)
        }
    }

    /** @deprecated internal — use [requestConnect] from UI; kept for silent probes. */
    suspend fun connectDrive(): Boolean = requestConnect() is DriveConnectResult.Success

    suspend fun uploadPatientRecord(record: PatientDetailRecord): Result<String> = runCatching {
        val token = obtainToken(allowConsentPrompt = false) ?: error(NO_TOKEN)
        val fileName = "$FILE_PREFIX${record.childId}_${record.id}.json"
        val content = json.encodeToString(record)
        val existing = findFileId(token, fileName)
        if (existing != null) {
            updateFile(token, existing, content)
            existing
        } else {
            createFile(token, fileName, content) ?: error("Drive create failed")
        }
    }

    suspend fun listPatientRecords(childId: String? = null): Result<List<DriveFileMeta>> = runCatching {
        val token = obtainToken(allowConsentPrompt = false) ?: error(NO_TOKEN)
        val nameFilter = if (childId != null) {
            "name contains '${FILE_PREFIX}${childId}_' and trashed=false"
        } else {
            "name contains '$FILE_PREFIX' and trashed=false"
        }
        val raw = client.get(DRIVE_FILES) {
            header("Authorization", "Bearer $token")
            parameter("q", nameFilter)
            parameter("spaces", "appDataFolder")
            parameter("fields", "files(id,name,modifiedTime)")
            parameter("pageSize", 100)
            parameter("orderBy", "modifiedTime desc")
        }.bodyAsText()
        json.parseToJsonElement(raw).jsonObject["files"]?.jsonArray.orEmpty().mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val parsedChild = name.removePrefix(FILE_PREFIX).substringBefore('_').takeIf { it.isNotBlank() }
            DriveFileMeta(
                id = id,
                name = name,
                modifiedTime = obj["modifiedTime"]?.jsonPrimitive?.contentOrNull,
                childId = parsedChild
            )
        }
    }

    suspend fun downloadPatientRecord(fileId: String): Result<PatientDetailRecord> = runCatching {
        val token = obtainToken(allowConsentPrompt = false) ?: error(NO_TOKEN)
        val content = downloadFile(token, fileId) ?: error("Empty Drive download")
        json.decodeFromString<PatientDetailRecord>(content)
    }

    const val NO_TOKEN = "DRIVE_NO_TOKEN"

    /** Plain on-device guidance — never references internal docs. */
    fun userMessage(
        isThai: Boolean,
        failure: DriveFailure? = null,
        loginEmail: String? = null,
        deviceAccounts: List<String> = emptyList()
    ): String {
        val login = loginEmail?.takeIf { it.isNotBlank() }
        val accounts = deviceAccounts.filter { it.isNotBlank() }
        return when (failure) {
            DriveFailure.NO_PLAY_SERVICES -> if (isThai) {
                "ต้องมี Google Play services — อัปเดตจาก Play Store แล้วลองใหม่"
            } else {
                "Google Play services is required — update it from the Play Store, then try again."
            }
            DriveFailure.NO_GOOGLE_ACCOUNTS -> if (isThai) {
                "ไม่พบบัญชี Google บนเครื่อง — ไปที่ Settings → Passwords & accounts → Add account → Google"
            } else {
                "No Google account found on this device — open Settings → Passwords & accounts → Add account → Google."
            }
            DriveFailure.EMAIL_MISMATCH -> if (isThai) {
                buildString {
                    append("บัญชี Google บนเครื่องยังไม่ตรงกับที่เข้าสู่ระบบ")
                    login?.let { append(" ($it)") }
                    append(" — เพิ่มบัญชี Google อีเมลเดียวกันใน Settings → Passwords & accounts")
                    if (accounts.isNotEmpty()) {
                        append(" (ตอนนี้มี: ${accounts.joinToString()})")
                    }
                }
            } else {
                buildString {
                    append("The Google account on this device does not match your sign-in email")
                    login?.let { append(" ($it)") }
                    append(" — add the same Google account under Settings → Passwords & accounts")
                    if (accounts.isNotEmpty()) append(" (on device: ${accounts.joinToString()})")
                }
            }
            DriveFailure.AUTH_FAILED, null -> if (isThai) {
                "กด「เชื่อมต่อ Google Drive」แล้วอนุญาตเมื่อระบบถาม — ต้องมีบัญชี Google บนเครื่องและอนุญาตให้แอปใช้ Drive"
            } else {
                "Tap Connect Google Drive and allow access when prompted — you need a Google account on this device and must grant Drive permission."
            }
        }
    }

    /** @deprecated use [userMessage] */
    fun noTokenMessage(isThai: Boolean): String = userMessage(isThai)

    suspend fun googleAccountsOnDevice(): List<String> = withContext(Dispatchers.IO) {
        googleAccounts()
    }

    suspend fun loginEmail(): String? = supabase.auth.currentUserOrNull()?.email

    private fun failed(reason: DriveFailure): DriveConnectResult.Failed {
        val login = supabase.auth.currentUserOrNull()?.email
        val accounts = runCatching { AccountManager.get(NongRakApp.instance).getAccountsByType("com.google").map { it.name } }
            .getOrDefault(emptyList())
        return DriveConnectResult.Failed(reason, login, accounts)
    }

    private fun googleAccounts(): List<String> =
        AccountManager.get(NongRakApp.instance).getAccountsByType("com.google").map { it.name }

    private fun isPlayServicesAvailable(ctx: android.content.Context): Boolean {
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx)
        return code == ConnectionResult.SUCCESS
    }

    /** Call after user completes OAuth consent for [accountEmail]. */
    suspend fun completeConnectAfterConsent(accountEmail: String): DriveConnectResult =
        withContext(Dispatchers.IO) {
            connectAccount(accountEmail, googleAccounts())
        }

    private suspend fun obtainToken(allowConsentPrompt: Boolean): String? = withContext(Dispatchers.IO) {
        if (!isPlayServicesAvailable(NongRakApp.instance)) return@withContext null
        val account = getSavedAccount() ?: return@withContext null
        val ctx = NongRakApp.instance
        try {
            val token = GoogleAuthUtil.getToken(ctx, account, DRIVE_SCOPE)
            if (!token.isNullOrBlank()) return@withContext token
        } catch (e: UserRecoverableAuthException) {
            if (allowConsentPrompt) throw e
        } catch (_: Exception) {
            // no token
        }
        null
    }

    private suspend fun findFileId(token: String, fileName: String): String? = runCatching {
        val raw = client.get(DRIVE_FILES) {
            header("Authorization", "Bearer $token")
            parameter("q", "name='$fileName' and trashed=false")
            parameter("spaces", "appDataFolder")
            parameter("fields", "files(id)")
        }.bodyAsText()
        json.parseToJsonElement(raw).jsonObject["files"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private suspend fun downloadFile(token: String, id: String): String? = runCatching {
        client.get("$DRIVE_FILES/$id") {
            header("Authorization", "Bearer $token")
            parameter("alt", "media")
        }.bodyAsText()
    }.getOrNull()

    private suspend fun createFile(token: String, fileName: String, content: String): String? = runCatching {
        val boundary = "nongrak_${System.currentTimeMillis()}"
        val meta = """{"name":"$fileName","parents":["appDataFolder"]}"""
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: application/json\r\n\r\n$content\r\n--$boundary--"
        val raw = client.post("$UPLOAD_URL?uploadType=multipart") {
            header("Authorization", "Bearer $token")
            contentType(
                ContentType(
                    "multipart", "related",
                    listOf(HeaderValueParam("boundary", boundary))
                )
            )
            setBody(body)
        }.bodyAsText()
        json.parseToJsonElement(raw).jsonObject["id"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private suspend fun updateFile(token: String, id: String, content: String) {
        client.patch("$UPLOAD_URL/$id?uploadType=media") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(content)
        }
    }
}
