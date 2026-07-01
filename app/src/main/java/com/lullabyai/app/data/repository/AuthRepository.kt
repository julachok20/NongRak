package com.lullabyai.app.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lullabyai.app.BuildConfig
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.UUID

class AuthRepository {

    val currentUser get() = supabase.auth.currentUserOrNull()

    suspend fun signUp(email: String, password: String, fullName: String): Result<Unit> = runCatching {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("full_name", JsonPrimitive(fullName))
            }
        }
        // Auto sign-in after sign up to guarantee a session exists
        // (needed when Supabase email confirmation is enabled)
        if (supabase.auth.currentUserOrNull() == null) {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }
        ensureProfile(fullName)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        ensureProfile(null)
    }

    /** Safety net: guarantee a profiles row exists so children/chat foreign keys never fail. */
    private suspend fun ensureProfile(fullName: String?) {
        val user = supabase.auth.currentUserOrNull() ?: return
        runCatching {
            // Insert profile if missing (trigger usually creates it; this is a safety net)
            val existing = supabase.from("profiles").select {
                filter { eq("id", user.id) }
                limit(1)
            }.decodeList<Profile>()
            if (existing.isEmpty()) {
                supabase.from("profiles").insert(
                    buildJsonObject {
                        put("id", JsonPrimitive(user.id))
                        if (!fullName.isNullOrBlank()) put("full_name", JsonPrimitive(fullName))
                        // PDPA: first profile creation == the moment consent was given at signup
                        put("consent_given_at", JsonPrimitive(java.time.Instant.now().toString()))
                    }
                )
            }
        }
    }

    suspend fun signInWithGoogle(context: Context): Result<Unit> = runCatching {
        val credentialManager = CredentialManager.create(context)

        // Supabase expects the raw nonce; Google expects its SHA-256 hash
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = try {
            credentialManager.getCredential(context, request)
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            error("ไม่พบบัญชี Google ในเครื่อง กรุณาเพิ่มบัญชี Google ในการตั้งค่าของอุปกรณ์ก่อน")
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            error("ยกเลิกการเข้าสู่ระบบ")
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

        supabase.auth.signInWith(IDToken) {
            idToken = googleCredential.idToken
            provider = Google
            nonce = rawNonce
        }
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        // LOCAL scope clears the on-device session immediately without a server round-trip, so logout
        // is instant even on a slow/flaky connection (GLOBAL waits on the network to revoke tokens).
        supabase.auth.signOut(SignOutScope.LOCAL)
    }

    /** Cancel an unfinished signup: delete the user's data and sign out. */
    /**
     * Cancel an unfinished SIGNUP (user bailed during onboarding before creating a child).
     * Only signs out. We must NOT mass-delete children here: a child is created only at the END of
     * onboarding, so at cancel time there is nothing to clean — and deleting "all children for the
     * user" wiped a returning user's existing child once (real data loss). The add-child flow also
     * no longer routes its back button here.
     */
    suspend fun cancelSignup(): Result<Unit> = runCatching {
        supabase.auth.signOut(SignOutScope.LOCAL)
    }

    /**
     * Permanently delete the user's account (PDPA right to erasure). Calls the SECURITY DEFINER
     * `delete_account` RPC, which (server-side) removes the user's storage files, their profile —
     * cascading to all children/health/chat/notification data — and the auth account itself. The
     * local session is then invalid, so we clear it on-device.
     */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id
        // Storage objects can't be removed via SQL, so delete the user's files through the Storage API
        // first (while the session is still valid). Best-effort — never block account deletion on it.
        if (uid != null) runCatching { deleteUserStorage(uid) }
        supabase.postgrest.rpc("delete_account")
        runCatching { supabase.auth.signOut(SignOutScope.LOCAL) }
        Unit
    }

    /** Best-effort removal of the user's private files (avatars + medical docs) via the Storage API. */
    private suspend fun deleteUserStorage(uid: String) {
        // avatars bucket is flat: uid/<file>
        runCatching {
            val paths = supabase.storage.from("avatars").list(uid)
                .map { it.name }.filter { it.isNotBlank() }.map { "$uid/$it" }
            if (paths.isNotEmpty()) supabase.storage.from("avatars").delete(paths)
        }
        // medical-docs is nested: uid/<childId>/<file>
        runCatching {
            val paths = mutableListOf<String>()
            supabase.storage.from("medical-docs").list(uid).forEach { folder ->
                if (folder.name.isNotBlank()) runCatching {
                    supabase.storage.from("medical-docs").list("$uid/${folder.name}").forEach { f ->
                        if (f.name.isNotBlank()) paths.add("$uid/${folder.name}/${f.name}")
                    }
                }
            }
            if (paths.isNotEmpty()) supabase.storage.from("medical-docs").delete(paths)
        }
    }

    suspend fun changePassword(newPassword: String): Result<Unit> = runCatching {
        supabase.auth.updateUser { password = newPassword }
    }

    // ---------------- FORGOT PASSWORD (OTP flow) ----------------

    /** Step 1: email a 6-digit recovery code (requires the Supabase "Reset Password" email
     *  template to include {{ .Token }}). */
    /** Returns true if an account exists for this email (checks auth.users via SECURITY DEFINER RPC). */
    suspend fun isEmailRegistered(email: String): Boolean = runCatching {
        val raw = supabase.postgrest.rpc(
            "email_registered",
            buildJsonObject { put("check_email", JsonPrimitive(email.trim().lowercase())) }
        ).data
        // Supabase REST wraps scalar results as "[true]" or "[false]" — check for the word "true"
        raw.contains("true", ignoreCase = true)
    }.getOrDefault(true) // default true = don't block on network error

    suspend fun sendPasswordResetOtp(email: String): Result<Unit> = runCatching {
        supabase.auth.resetPasswordForEmail(email.trim())
    }

    /** Step 2: verify the 6-digit code — on success this creates an authenticated recovery session. */
    suspend fun verifyRecoveryOtp(email: String, token: String): Result<Unit> = runCatching {
        supabase.auth.verifyEmailOtp(
            type = OtpType.Email.RECOVERY,
            email = email.trim(),
            token = token.trim()
        )
    }

    /** Step 3: with the recovery session active, set a random temporary password + flag the account
     *  as "must change password", return the temp password to display, then sign out. */
    suspend fun setTemporaryPassword(): Result<String> = runCatching {
        val temp = generateTempPassword()
        supabase.auth.updateUser {
            password = temp
            data = buildJsonObject { put("must_change_password", JsonPrimitive(true)) }
        }
        runCatching { supabase.auth.signOut() }
        temp
    }

    /** True when the signed-in user logged in with a temporary password and must change it. */
    fun mustChangePassword(): Boolean {
        val meta = supabase.auth.currentUserOrNull()?.userMetadata ?: return false
        return meta["must_change_password"]?.jsonPrimitive?.booleanOrNull ?: false
    }

    /** Complete a forced change without re-asking the current password (user just logged in with the
     *  temp password). Sets the new password and clears the must-change flag. */
    suspend fun completeForcedChange(newPassword: String): Result<Unit> = runCatching {
        supabase.auth.updateUser {
            password = newPassword
            data = buildJsonObject { put("must_change_password", JsonPrimitive(false)) }
        }
    }

    /** Forced change: verify the current (temp) password by re-auth, set the new one, clear the flag. */
    suspend fun changePasswordForced(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val email = supabase.auth.currentUserOrNull()?.email ?: error("ไม่พบบัญชีผู้ใช้")
        // Verify the current password (throws if wrong)
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = currentPassword
        }
        // Set the new password and clear the must-change flag
        supabase.auth.updateUser {
            password = newPassword
            data = buildJsonObject { put("must_change_password", JsonPrimitive(false)) }
        }
    }

    private fun generateTempPassword(): String {
        // Exclude ambiguous characters (0/O, 1/I/L) for easy reading
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val code = (1..6).map { chars.random() }.joinToString("")
        return "LULLABY-$code"
    }

    suspend fun getProfile(): Result<Profile> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        supabase.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeSingle<Profile>()
    }

    suspend fun updateProfile(username: String?, fullName: String?, avatarUrl: String?, relationship: String? = null): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val updates = buildMap<String, Any?> {
            username?.let { put("username", it) }
            fullName?.let { put("full_name", it) }
            avatarUrl?.let { put("avatar_url", it) }
            relationship?.let { put("relationship", it) }
        }
        supabase.from("profiles").update(updates) {
            filter { eq("id", userId) }
        }
    }

    /**
     * Uploads a profile photo to the PRIVATE 'avatars' bucket, stores the bucket PATH on the
     * profile (never a public URL), and returns a short-lived signed URL for immediate display.
     */
    suspend fun uploadAvatar(bytes: ByteArray): Result<String> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val path = "$userId/avatar.jpg"
        supabase.storage.from(StorageHelper.AVATAR_BUCKET).upload(path, bytes) { upsert = true }
        // Store the stable PATH (not a public URL) — photos are private, served via signed URLs.
        supabase.from("profiles").update(buildJsonObject { put("avatar_url", JsonPrimitive(path)) }) {
            filter { eq("id", userId) }
        }
        StorageHelper.signedAvatarUrl(path) ?: error("Could not sign avatar URL")
    }

    /**
     * PDPA right to data portability: gathers ALL of the user's data into a HUMAN-READABLE
     * Thai report (not raw JSON, so parents can actually read it). RLS guarantees only the
     * current user's rows are returned.
     */
    suspend fun exportUserData(): Result<String> = runCatching {
        supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        suspend fun tbl(name: String): List<JsonObject> = runCatching {
            supabase.from(name).select().decodeList<JsonObject>()
        }.getOrDefault(emptyList())

        // English DB columns → friendly Thai labels
        val labels = mapOf(
            "full_name" to "ชื่อ-นามสกุล", "username" to "ชื่อผู้ใช้", "email" to "อีเมล",
            "consent_given_at" to "ให้ความยินยอมเมื่อ", "created_at" to "สร้างเมื่อ",
            "name" to "ชื่อ", "birthdate" to "วันเกิด", "gender" to "เพศ",
            "weight_kg" to "น้ำหนัก (กก.)", "height_cm" to "ส่วนสูง (ซม.)",
            "sleep_hours" to "ชั่วโมงการนอน", "activity_level" to "ระดับกิจกรรม",
            "allergies" to "การแพ้", "notes" to "บันทึก", "allergy_name" to "สิ่งที่แพ้", "severity" to "ความรุนแรง",
            "vaccine_name" to "ชื่อวัคซีน", "status" to "สถานะ", "scheduled_date" to "กำหนดฉีด",
            "given_date" to "วันที่ฉีด", "dose" to "เข็มที่",
            "milestone_name" to "ทักษะ", "category" to "หมวด", "is_achieved" to "ทำได้แล้ว", "skip_reason" to "เหตุผลที่ข้าม",
            "record_type" to "ประเภท", "title" to "หัวข้อ", "description" to "รายละเอียด", "date" to "วันที่",
            "role" to "ผู้พูด", "content" to "ข้อความ", "text" to "ข้อความ",
            "task_text" to "กิจกรรม", "is_completed" to "ทำเสร็จแล้ว", "completed_at" to "เสร็จเมื่อ",
            "metric_type" to "การวัด", "value" to "ค่า", "unit" to "หน่วย", "type" to "ประเภท"
        )
        val skip = setOf("id", "user_id", "child_id", "session_id", "avatar_url", "updated_at")
        fun valStr(e: JsonElement): String = when (e) {
            is JsonNull -> ""
            is JsonPrimitive -> e.content
            else -> e.toString()
        }
        fun render(rows: List<JsonObject>): String {
            if (rows.isEmpty()) return "  (ไม่มีข้อมูล)\n\n"
            val sb = StringBuilder()
            rows.forEachIndexed { i, row ->
                sb.append("  รายการที่ ${i + 1}\n")
                row.entries.filter { it.key !in skip }.forEach { (k, v) ->
                    val value = valStr(v)
                    if (value.isNotBlank() && value != "null") {
                        sb.append("    • ${labels[k] ?: k.replace("_", " ")}: $value\n")
                    }
                }
                sb.append("\n")
            }
            return sb.toString()
        }

        val sb = StringBuilder()
        sb.append("ข้อมูลส่วนบุคคลของฉัน — NongRak AI\n")
        sb.append("ส่งออกเมื่อ: ${java.time.LocalDate.now()}\n")
        sb.append("เอกสารนี้รวบรวมข้อมูลทั้งหมดที่แอปจัดเก็บเกี่ยวกับคุณและบุตรหลาน ตามสิทธิ์ PDPA\n\n")
        sb.append("======== โปรไฟล์ผู้ใช้ ========\n"); sb.append(render(tbl("profiles")))
        sb.append("======== ข้อมูลบุตรหลาน ========\n"); sb.append(render(tbl("children")))
        sb.append("======== ประวัติการรักษา ========\n"); sb.append(render(tbl("medical_history")))
        sb.append("======== บันทึกสุขภาพ ========\n"); sb.append(render(tbl("health_records")))
        sb.append("======== สมุดวัคซีน ========\n"); sb.append(render(tbl("vaccine_records")))
        sb.append("======== ประวัติการแพ้ ========\n"); sb.append(render(tbl("allergies")))
        sb.append("======== การเจริญเติบโต ========\n"); sb.append(render(tbl("growth_records")))
        sb.append("======== พัฒนาการ ========\n"); sb.append(render(tbl("development_milestones")))
        sb.append("======== กิจกรรมที่ AI แนะนำ ========\n"); sb.append(render(tbl("ai_tasks")))
        sb.append("======== ประวัติการสนทนากับ AI ========\n"); sb.append(render(tbl("chat_messages")))
        sb.toString()
    }

    suspend fun hasCompletedOnboarding(): Boolean {
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return false
            val list = supabase.from("children").select {
                filter { eq("user_id", userId) }
                limit(1)
            }.decodeList<Map<String, String>>()
            list.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
