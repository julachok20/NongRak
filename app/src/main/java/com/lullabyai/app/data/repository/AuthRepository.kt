package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.MentalHealthAssessment
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.model.ProfileRole
import com.lullabyai.app.data.model.effectiveRoles
import com.lullabyai.app.data.model.hasDoctorRole
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thai phone numbers, normalized to national form (`0812345678`).
 *
 * Accepts anything a parent might actually type — spaces, dashes, dots, parentheses — and the
 * `+66` / `0066` / `66` country prefixes. Returns `null` when the input cannot be a Thai number,
 * so callers show a clear message instead of silently storing junk. Never log the input or result.
 */
object ThaiPhone {
    fun normalize(raw: String): String? {
        var d = raw.filter { it.isDigit() }
        if (d.startsWith("00")) d = d.drop(2)       // 00 = international access prefix
        if (d.startsWith("66")) d = "0" + d.drop(2) // country code -> national trunk '0'
        // Mobile is 10 digits (06x/08x/09x), landline 9 (02x-07x). Second digit is never 0.
        return d.takeIf { it.length in 9..10 && it[0] == '0' && it[1] != '0' }
    }
}

class AuthRepository {

    val currentUser get() = supabase.auth.currentUserOrNull()

    /** [phone] is expected pre-normalized by the caller, but is re-normalized here — this is the
     *  trust boundary, and an un-validated number must never reach the database. */
    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        captchaToken: String? = null,
        accountType: String = "parent", // "parent" | "doctor"
        phone: String? = null
    ): Result<Unit> = runCatching {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.captchaToken = captchaToken
            data = buildJsonObject {
                put("full_name", JsonPrimitive(fullName))
            }
        }
        // Auto sign-in after sign up to guarantee a session exists
        // (needed when Supabase email confirmation is enabled). Not passing captchaToken here —
        // the token is single-use and already spent on signUpWith above.
        if (supabase.auth.currentUserOrNull() == null) {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }
        ensureProfile(fullName = fullName, accountType = accountType, phone = phone?.let { ThaiPhone.normalize(it) })
    }

    /**
     * Doctor registration when the email may already belong to a parent account (same person,
     * separate app area). Supabase allows one auth user per email — we add the doctor role instead
     * of creating a second auth user.
     */
    suspend fun signUpDoctor(
        email: String,
        password: String,
        fullName: String,
        captchaToken: String? = null,
        /** The doctor's contact number. The sign-up form has always collected it, but it stopped at
         *  the screen — `profiles.phone` exists and the parent flow writes it, so not forwarding it
         *  here silently discarded what the doctor typed. */
        phone: String? = null
    ): Result<Unit> = runCatching {
        val created = runCatching {
            signUp(email, password, fullName, captchaToken, accountType = ProfileRole.DOCTOR, phone = phone)
        }
        if (created.isSuccess) return@runCatching
        val err = created.exceptionOrNull() ?: error("Doctor sign-up failed")
        if (!isEmailAlreadyRegistered(err)) throw err
        signIn(email, password, captchaToken).getOrThrow()
        grantDoctorRole().getOrThrow()
    }

    suspend fun signIn(email: String, password: String, captchaToken: String? = null): Result<Unit> = runCatching {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
            this.captchaToken = captchaToken
        }
        ensureProfile(fullName = null, accountType = null)
    }

    suspend fun grantDoctorRole(): Result<Unit> = runCatching {
        val user = supabase.auth.currentUserOrNull() ?: error("Not signed in")
        val profile = getProfile().getOrThrow()
        val merged = (profile.effectiveRoles() + ProfileRole.DOCTOR).distinct()
        val patch = buildJsonObject {
            put("roles", JsonArray(merged.map { JsonPrimitive(it) }))
            if (!merged.contains(ProfileRole.PARENT)) {
                put("account_type", JsonPrimitive(ProfileRole.DOCTOR))
            }
        }
        supabase.from("profiles").update(patch) { filter { eq("id", user.id) } }
    }

    private fun isEmailAlreadyRegistered(err: Throwable): Boolean {
        val msg = err.message?.lowercase() ?: return false
        return msg.contains("already registered") ||
            msg.contains("already been registered") ||
            msg.contains("user already registered") ||
            msg.contains("email address is already registered")
    }

    /** Creates or patches profile metadata. [accountType] null = sign-in only (do not change roles). */
    private suspend fun ensureProfile(fullName: String?, accountType: String?, phone: String? = null) {
        val user = supabase.auth.currentUserOrNull() ?: return
        runCatching {
            val existing = supabase.from("profiles").select {
                filter { eq("id", user.id) }
                limit(1)
            }.decodeList<Profile>()
            if (existing.isEmpty()) {
                val role = when (accountType?.lowercase()) {
                    ProfileRole.DOCTOR -> ProfileRole.DOCTOR
                    else -> ProfileRole.PARENT
                }
                val legacyType = if (role == ProfileRole.DOCTOR) ProfileRole.DOCTOR else ProfileRole.PARENT
                supabase.from("profiles").insert(
                    buildJsonObject {
                        put("id", JsonPrimitive(user.id))
                        if (!fullName.isNullOrBlank()) put("full_name", JsonPrimitive(fullName))
                        put("account_type", JsonPrimitive(legacyType))
                        put("roles", JsonArray(listOf(JsonPrimitive(role))))
                        if (phone != null) put("phone", JsonPrimitive(phone))
                        put("consent_given_at", JsonPrimitive(java.time.Instant.now().toString()))
                    }
                )
            } else {
                val patch = buildJsonObject {
                    if (phone != null) put("phone", JsonPrimitive(phone))
                }
                if (patch.isNotEmpty()) {
                    supabase.from("profiles").update(patch) { filter { eq("id", user.id) } }
                }
                if (accountType?.lowercase() == ProfileRole.DOCTOR && !existing.first().hasDoctorRole()) {
                    grantDoctorRole()
                }
            }
        }
    }

    /** Completes Google auth after [GoogleAuthHelper] returns an ID token (classic Sign-In SDK). */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        accountType: String = "parent"
    ): Result<Unit> = runCatching {
        require(idToken.isNotBlank()) { "missing_id_token" }
        runCatching {
            supabase.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }
        }.onFailure { err ->
            // Same email may already exist (email/password signup). Supabase links identities when
            // automatic linking is enabled; if sign-in still fails, surface a clear message.
            if (isEmailAlreadyRegistered(err)) {
                error(
                    if (accountType.lowercase() == ProfileRole.DOCTOR)
                        "email_exists_use_password_then_google"
                    else
                        "email_exists_use_password_then_google"
                )
            } else throw err
        }.getOrThrow()
        ensureProfile(fullName = null, accountType = accountType)
    }

    /**
     * Completes LINE auth after [LineAuthHelper] returns an id_token.
     *
     * Unlike Google there is no Supabase provider to hand this to — signInWith(IDToken) only
     * accepts Google/Apple/Azure/Facebook/Kakao — so the session is minted by the `line-auth`
     * edge function and imported here.
     */
    suspend fun signInWithLineIdToken(
        idToken: String,
        accountType: String = "parent"
    ): Result<Unit> = runCatching {
        require(idToken.isNotBlank()) { "missing_id_token" }
        val (access, refresh) = LineRepository.exchangeIdToken(
            idToken = idToken,
            nonce = com.lullabyai.app.util.LineAuthHelper.nonce,
            accountType = accountType
        ).getOrThrow()
        com.lullabyai.app.util.LineAuthHelper.clearNonce()
        supabase.auth.importAuthToken(access, refresh)
        ensureProfile(fullName = null, accountType = accountType)
    }

    /**
     * Providers linked to the signed-in account, e.g. {"email", "google"}.
     *
     * Read from the Supabase identity list rather than remembered locally, so the Settings rows
     * show what the server actually believes — a locally cached "linked" flag is exactly how a
     * revoked Google grant keeps showing as connected.
     */
    fun linkedProviders(): Set<String> = runCatching {
        supabase.auth.currentUserOrNull()?.identities.orEmpty()
            .mapNotNull { it.provider.lowercase().takeIf(String::isNotBlank) }
            .toSet()
    }.getOrDefault(emptySet())

    enum class AuthPortal { PARENT, DOCTOR }

    fun portalRejectionMessage(portal: AuthPortal, isThai: Boolean): String = when (portal) {
        AuthPortal.PARENT -> if (isThai) {
            "อีเมลนี้เป็นบัญชีแพทย์เท่านั้น — เข้าสู่ระบบผู้ปกครองใช้บัญชีผู้ปกครอง หรือสมัครผู้ปกครองด้วยอีเมลนี้ (บัญชีแยกในส่วนแพทย์/ผู้ปกครอง)"
        } else {
            "This email is doctor-only. Use parent sign-in with a parent account, or register as parent first (same email can hold both roles separately)."
        }
        AuthPortal.DOCTOR -> if (isThai) {
            "บัญชีนี้ยังไม่มีสิทธิ์แพทย์ — สมัครสมาชิกแพทย์ด้วยอีเมลเดียวกันได้ (แยกจากผู้ปกครอง)"
        } else {
            "This account does not have doctor access yet — register as a doctor with the same email (separate from parent)."
        }
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        val context = com.lullabyai.app.NongRakApp.instance
        val session = supabase.auth.currentSessionOrNull()
        val userId = supabase.auth.currentUserOrNull()?.id
        if (session != null && userId != null &&
            com.lullabyai.app.data.settings.BiometricLogin.enrolledUserId(context) == userId
        ) {
            // Biometric sign-in is on for this account: keep its latest refresh token (encrypted,
            // released only after face / fingerprint) and clear the session on this phone without
            // revoking it, so "เข้าสู่ระบบด้วยลายนิ้วมือ" can resume it.
            val displayName = getProfile().getOrNull()?.let { p ->
                p.fullName?.trim()?.takeIf { it.isNotEmpty() } ?: p.username?.trim()?.takeIf { it.isNotEmpty() }
            }
            com.lullabyai.app.data.settings.BiometricLogin.saveRefreshToken(context, session.refreshToken, displayName)
            supabase.auth.clearSession()
        } else {
            // LOCAL scope clears the on-device session immediately without a server round-trip, so logout
            // is instant even on a slow/flaky connection (GLOBAL waits on the network to revoke tokens).
            supabase.auth.signOut(SignOutScope.LOCAL)
        }
    }

    /** Settings › Active Sessions: this account's signed-in devices (RPC list_my_sessions, own rows only). */
    suspend fun listSessions(): Result<List<AuthSessionRow>> = runCatching {
        supabase.postgrest.rpc("list_my_sessions").decodeList<AuthSessionRow>()
    }

    /** Signs one other device out. The server refuses the phone in hand (cannot_revoke_current_session). */
    suspend fun revokeSession(sessionId: String): Result<Boolean> = runCatching {
        supabase.postgrest.rpc(
            "revoke_my_session",
            buildJsonObject { put("p_session_id", JsonPrimitive(sessionId)) }
        ).decodeAs<Boolean>()
    }

    /** Signs every device except this one out; returns how many. */
    suspend fun revokeOtherSessions(): Result<Int> = runCatching {
        supabase.postgrest.rpc("revoke_my_other_sessions").decodeAs<Int>()
    }

    /**
     * Resumes the enrolled account from the key saved at logout. Call only after AppLock.authenticate
     * succeeded. The key is removed as it is read (refresh tokens rotate), so a failure here means
     * signing in with a password once more.
     */
    suspend fun signInWithBiometricKey(): Result<Unit> = runCatching {
        val context = com.lullabyai.app.NongRakApp.instance
        val token = com.lullabyai.app.data.settings.BiometricLogin.takeRefreshToken(context)
            ?: error("biometric_login_no_saved_key")
        val session = supabase.auth.refreshSession(token)
        supabase.auth.importSession(session)
        ensureProfile(fullName = null, accountType = null)
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
        // The account is gone: its saved biometric sign-in must go too.
        runCatching { com.lullabyai.app.data.settings.BiometricLogin.clear(com.lullabyai.app.NongRakApp.instance) }
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

    suspend fun sendPasswordResetOtp(email: String, captchaToken: String? = null): Result<Unit> = runCatching {
        supabase.auth.resetPasswordForEmail(email.trim(), captchaToken = captchaToken)
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

    suspend fun updateProfile(
        username: String?,
        fullName: String?,
        avatarUrl: String?,
        relationship: String? = null,
        phone: String? = null,
        address: String? = null
    ): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val updates = buildJsonObject {
            username?.let { put("username", JsonPrimitive(it)) }
            fullName?.let { put("full_name", JsonPrimitive(it)) }
            avatarUrl?.let { put("avatar_url", JsonPrimitive(it)) }
            relationship?.let { put("relationship", JsonPrimitive(it)) }
            phone?.let { put("phone", JsonPrimitive(it)) }
            address?.let { put("address", JsonPrimitive(it)) }
        }
        supabase.from("profiles").update(updates) {
            filter { eq("id", userId) }
        }
    }

    /**
     * Settings › Personal Info: saves ONE profile column (`username`, `full_name` or `phone`) and reads
     * it back. Null clears it. A username someone else holds fails as `username_taken`
     * (profiles_username_key), so the dialog can say so instead of showing a raw Postgres error.
     */
    suspend fun updateProfileField(column: String, value: String?): Result<Profile> = runCatching {
        require(column in setOf("username", "full_name", "phone")) { "field_not_editable: $column" }
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        try {
            supabase.from("profiles").update(buildJsonObject { put(column, value?.let { JsonPrimitive(it) } ?: JsonNull) }) {
                filter { eq("id", userId) }
            }
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("profiles_username_key")) error("username_taken")
            throw e
        }
        val saved = getProfile().getOrThrow()
        val now = when (column) { "username" -> saved.username; "full_name" -> saved.fullName; else -> saved.phone }
        check(now == value) { "profile_field_not_saved: $column wanted '$value', got '$now'" }
        saved
    }

    /** Asks Supabase to move sign-in to [newEmail]. Nothing changes until the link sent to the new address is opened. */
    suspend fun requestEmailChange(newEmail: String): Result<Unit> = runCatching {
        supabase.auth.updateUser { email = newEmail }
    }

    /** Separate opt-in consent for using (de-identified) data to improve/train AI models — a
     *  distinct PDPA purpose from the mandatory signup consent, so it gets its own timestamp
     *  the same way [signUp]'s consent_given_at does. Off by default; can be withdrawn any time. */
    /** Submits the monthly parent mental-health self-assessment (see MentalHealthCatalog for the
     *  question set + scoring). Scoring is computed client-side from the already-known answers. */
    suspend fun submitMentalHealthAssessment(answers: List<Int>): Result<MentalHealthAssessment> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val totalScore = answers.sum()
        val tier = com.lullabyai.app.data.MentalHealthCatalog.riskTier(totalScore)
        val flaggedSelfHarm = answers.getOrNull(com.lullabyai.app.data.MentalHealthCatalog.SELF_HARM_INDEX)?.let { it > 0 } ?: false
        supabase.from("parent_mental_health_assessments").insert(buildJsonObject {
            put("user_id", JsonPrimitive(userId))
            put("answers", JsonArray(answers.map { JsonPrimitive(it) }))
            put("total_score", JsonPrimitive(totalScore))
            put("risk_tier", JsonPrimitive(tier))
            put("flagged_self_harm", JsonPrimitive(flaggedSelfHarm))
        }) { select() }.decodeSingle<MentalHealthAssessment>()
    }

    /** Newest-first history, for the result screen's trend chart and the dashboard "done this month" check. */
    suspend fun getMentalHealthHistory(): Result<List<MentalHealthAssessment>> = runCatching {
        supabase.from("parent_mental_health_assessments").select {
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList<MentalHealthAssessment>()
    }

    suspend fun setAiTrainingConsent(value: Boolean): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        supabase.from("profiles").update(buildJsonObject {
            put("ai_training_consent", JsonPrimitive(value))
            put("ai_training_consent_at", if (value) JsonPrimitive(java.time.Instant.now().toString()) else JsonNull)
        }) {
            filter { eq("id", userId) }
        }
    }

    /**
     * Toggles 2FA on the user's profile in Supabase.
     */
    suspend fun setTwoFactorEnabled(value: Boolean): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        supabase.from("profiles").update(buildJsonObject {
            put("two_factor_enabled", JsonPrimitive(value))
            put("two_factor_enabled_at", if (value) JsonPrimitive(java.time.Instant.now().toString()) else JsonNull)
        }) {
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
     * PDPA right to data portability: every table the app keeps about this user, read through RLS so
     * only their own rows come back. A table that fails to load is kept with its error, so the export
     * says "could not load" instead of silently showing it as empty.
     */
    suspend fun collectUserData(): Result<UserDataExport> = runCatching {
        supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        UserDataExport(
            UserDataExport.TABLES.map { (table, title) ->
                runCatching { supabase.from(table).select().decodeList<JsonObject>() }.fold(
                    onSuccess = { UserDataExport.Section(table, title, it, null) },
                    onFailure = { UserDataExport.Section(table, title, emptyList(), it.message ?: it::class.simpleName) }
                )
            }
        )
    }

    suspend fun hasCompletedOnboarding(): Boolean {
        return try {
            // No user_id filter — RLS already scopes to owned + synced (member) children; adding one
            // here would hide synced children and wrongly report "not onboarded" for that account.
            val list = supabase.from("children").select {
                limit(1)
            }.decodeList<Map<String, String>>()
            list.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

/** One row of `list_my_sessions()` — a device signed into this account. */
@kotlinx.serialization.Serializable
data class AuthSessionRow(
    val id: String,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    @kotlinx.serialization.SerialName("last_active_at") val lastActiveAt: String? = null,
    @kotlinx.serialization.SerialName("user_agent") val userAgent: String? = null,
    val ip: String? = null,
    @kotlinx.serialization.SerialName("is_current") val isCurrent: Boolean = false
)

/** Result of [AuthRepository.collectUserData]: a readable Thai report (for the PDF) or raw JSON. */
data class UserDataExport(val sections: List<Section>) {
    data class Section(val table: String, val title: String, val rows: List<JsonObject>, val error: String?)

    /** Human-readable Thai report: English columns become Thai labels, internal ids are left out. */
    fun toReport(exportedOn: String): String {
        fun value(e: JsonElement): String = when (e) {
            is JsonNull -> ""
            is JsonPrimitive -> e.content
            else -> e.toString()
        }
        val sb = StringBuilder()
        sb.append("ข้อมูลส่วนบุคคลของฉัน — NongRak AI\n")
        sb.append("ส่งออกเมื่อ: $exportedOn\n")
        sb.append("เอกสารนี้รวบรวมข้อมูลทั้งหมดที่แอปจัดเก็บเกี่ยวกับคุณและบุตรหลาน ตามสิทธิ์ PDPA\n\n")
        for (section in sections) {
            sb.append("======== ${section.title} ========\n")
            when {
                section.error != null -> sb.append("  (โหลดข้อมูลส่วนนี้ไม่สำเร็จ: ${section.error})\n\n")
                section.rows.isEmpty() -> sb.append("  (ไม่มีข้อมูล)\n\n")
                else -> section.rows.forEachIndexed { i, row ->
                    sb.append("  รายการที่ ${i + 1}\n")
                    row.entries.filter { it.key !in SKIP }.forEach { (k, v) ->
                        val text = value(v)
                        if (text.isNotBlank() && text != "null") sb.append("    • ${LABELS[k] ?: k.replace("_", " ")}: $text\n")
                    }
                    sb.append("\n")
                }
            }
        }
        return sb.toString()
    }

    /** Machine-readable copy: every column as stored, one key per table, plus any load errors. */
    fun toJson(exportedOn: String): String = kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("exported_at", JsonPrimitive(exportedOn))
            for (section in sections) {
                put(section.table, kotlinx.serialization.json.JsonArray(section.rows))
            }
            val errors = sections.filter { it.error != null }
            if (errors.isNotEmpty()) {
                put("errors", buildJsonObject { errors.forEach { put(it.table, JsonPrimitive(it.error)) } })
            }
        }
    )

    companion object {
        val TABLES = listOf(
            "profiles" to "โปรไฟล์ผู้ใช้",
            "children" to "ข้อมูลบุตรหลาน",
            "medical_history" to "ประวัติการรักษา",
            "health_records" to "บันทึกสุขภาพ",
            "vaccine_records" to "สมุดวัคซีน",
            "allergies" to "ประวัติการแพ้",
            "growth_records" to "การเจริญเติบโต",
            "development_milestones" to "พัฒนาการ",
            "ai_tasks" to "กิจกรรมที่ AI แนะนำ",
            "chat_messages" to "ประวัติการสนทนากับ AI"
        )
        private val SKIP = setOf("id", "user_id", "child_id", "session_id", "avatar_url", "updated_at")
        private val LABELS = mapOf(
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
    }
}
