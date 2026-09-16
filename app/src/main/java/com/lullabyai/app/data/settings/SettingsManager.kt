package com.lullabyai.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "nongrak_settings")

enum class AppLanguage { TH, EN }

data class AppSettings(
    val darkMode: Boolean = false,
    val language: AppLanguage = AppLanguage.TH,
    val pushNotifications: Boolean = true,
    val dailyReport: Boolean = true,
    val healthAlert: Boolean = true,
    val aiTips: Boolean = false,
    /** true = the AI logs what it understood without asking; false = it asks first. */
    val autoRecord: Boolean = false,
    /** true = signing in from a new device requires approval from an active device (2FA). */
    val twoFactorEnabled: Boolean = false,
    /** true = the app asks for face / fingerprint / screen lock before opening (AppLockGate). */
    val appLock: Boolean = false
)

class SettingsManager(private val context: Context) {

    private val DARK_MODE = booleanPreferencesKey("dark_mode")
    private val LANGUAGE = stringPreferencesKey("language")
    private val PUSH_NOTIFICATIONS = booleanPreferencesKey("push_notifications")
    private val DAILY_REPORT = booleanPreferencesKey("daily_report")
    private val HEALTH_ALERT = booleanPreferencesKey("health_alert")
    private val AI_TIPS = booleanPreferencesKey("ai_tips")
    private val TWO_FACTOR_ENABLED = booleanPreferencesKey("two_factor_enabled")
    private val SELECTED_CHILD = stringPreferencesKey("selected_child_id")
    private val HAS_SEEN_TOUR = booleanPreferencesKey("has_seen_first_run_tour")
    private val HAS_SEEN_DOCTOR_TOUR = booleanPreferencesKey("has_seen_doctor_first_run_tour")
    private val SLEEP_REC_RESPONSE_AT = stringPreferencesKey("sleep_rec_response_at")
    private val DEVICE_ID = stringPreferencesKey("trusted_device_id")
    private val COMMUNITY_TERMS_ACCEPTED = booleanPreferencesKey("community_terms_accepted")

    /** When the parent last answered (yes or no+reason) the recommended-bedtime prompt — gates
     *  how often SleepScheduleScreen/SleepRecommendationWorker re-ask (cooldown, not one-and-done). */
    val sleepRecRespondedAt: Flow<String?> = context.dataStore.data.map { it[SLEEP_REC_RESPONSE_AT] }

    suspend fun setSleepRecResponded() {
        context.dataStore.edit { it[SLEEP_REC_RESPONSE_AT] = java.time.Instant.now().toString() }
    }

    /**
     * A random id for this install, persisted once and reused forever after.
     *
     * Not the Android ID or any hardware identifier: those can change on reinstall or be shared
     * across profiles on some OEM skins. A self-issued UUID is what device-trust approval keys
     * off, so it needs to be stable for exactly this install and nothing else.
     */
    suspend fun getOrCreateDeviceId(): String {
        val existing = context.dataStore.data.map { it[DEVICE_ID] }.first()
        if (existing != null) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID] = fresh }
        return fresh
    }

    /** Whether the parent has already seen (or skipped) the first-run feature tour. */
    val hasSeenTour: Flow<Boolean> = context.dataStore.data.map { it[HAS_SEEN_TOUR] ?: false }

    suspend fun setHasSeenTour(value: Boolean) {
        context.dataStore.edit { it[HAS_SEEN_TOUR] = value }
    }

    /** Whether the doctor has already seen (or skipped) the first sign-up feature tour. */
    val hasSeenDoctorTour: Flow<Boolean> = context.dataStore.data.map { it[HAS_SEEN_DOCTOR_TOUR] ?: false }

    suspend fun setHasSeenDoctorTour(value: Boolean) {
        context.dataStore.edit { it[HAS_SEEN_DOCTOR_TOUR] = value }
    }

    private val HAS_SEEN_WIDGET_PROMO = booleanPreferencesKey("has_seen_widget_promo")

    /** Whether the parent has already seen (or dismissed) the home-screen-widgets promo popup. */
    val hasSeenWidgetPromo: Flow<Boolean> = context.dataStore.data.map { it[HAS_SEEN_WIDGET_PROMO] ?: false }

    suspend fun setHasSeenWidgetPromo(value: Boolean) {
        context.dataStore.edit { it[HAS_SEEN_WIDGET_PROMO] = value }
    }

    private val AI_DAILY_Q_DATE = stringPreferencesKey("ai_daily_question_date")
    private val AI_DAILY_Q_COUNT = intPreferencesKey("ai_daily_question_count")

    /** How many AI "what do you want to know more" popups have been shown today — resets on a new
     *  day. Caller (Dashboard) enforces the 3/day cap against this. */
    suspend fun aiDailyQuestionCountToday(): Int {
        val prefs = context.dataStore.data.first()
        val today = java.time.LocalDate.now().toString()
        return if (prefs[AI_DAILY_Q_DATE] == today) (prefs[AI_DAILY_Q_COUNT] ?: 0) else 0
    }

    /** Records one more AI daily-question shown (counts toward the cap whether answered or skipped). */
    suspend fun recordAiDailyQuestionShown() {
        val today = java.time.LocalDate.now().toString()
        context.dataStore.edit { prefs ->
            val current = if (prefs[AI_DAILY_Q_DATE] == today) (prefs[AI_DAILY_Q_COUNT] ?: 0) else 0
            prefs[AI_DAILY_Q_DATE] = today
            prefs[AI_DAILY_Q_COUNT] = current + 1
        }
    }

    // --- Child sync request snooze ---------------------------------------------------------
    //
    // Answering an access request used to hide it only in memory, so any refresh that re-read the
    // table put the card straight back: a request can legitimately still be `pending` after the
    // owner answers (the RPC refused, or it is now waiting on a ranked co-owner). Persisting the
    // snooze here is what makes "gone" survive a refresh or an app restart.
    //
    // A snooze, not a permanent dismissal: if nobody has resolved the request two hours later it
    // surfaces again, because a pending request for access to a child's records must not be
    // silently forgotten.
    private val SYNC_REQUEST_SNOOZE = stringPreferencesKey("sync_request_snooze")

    /** How long an answered request stays hidden before it is worth raising again. */
    private val SYNC_SNOOZE = java.time.Duration.ofHours(2)

    /** Request ids still inside their snooze window. Expired entries are ignored. */
    suspend fun snoozedSyncRequestIds(): Set<String> {
        val now = System.currentTimeMillis()
        return parseSnoozes(context.dataStore.data.first()[SYNC_REQUEST_SNOOZE])
            .filterValues { it > now }
            .keys
    }

    /** Hides [requestId] for the next two hours. Expired entries are pruned on the way through. */
    suspend fun snoozeSyncRequest(requestId: String) {
        val now = System.currentTimeMillis()
        val until = now + SYNC_SNOOZE.toMillis()
        context.dataStore.edit { prefs ->
            val kept = parseSnoozes(prefs[SYNC_REQUEST_SNOOZE]).filterValues { it > now }
            prefs[SYNC_REQUEST_SNOOZE] = (kept + (requestId to until))
                .entries.joinToString(",") { "${it.key}|${it.value}" }
        }
    }

    /** Tolerant of malformed entries: a corrupt pair is dropped, never allowed to throw. */
    private fun parseSnoozes(raw: String?): Map<String, Long> =
        (raw ?: "").split(',')
            .mapNotNull { entry ->
                val id = entry.substringBefore('|', "").trim()
                val until = entry.substringAfter('|', "").trim().toLongOrNull()
                if (id.isBlank() || until == null) null else id to until
            }
            .toMap()

    private val INFO_ASK_SHOWN_DATE = stringPreferencesKey("info_ask_shown_date")
    private val INFO_ASK_SHOWN_IDS = stringPreferencesKey("info_ask_shown_ids")
    private val INFO_ASK_ANSWERED_IDS = stringPreferencesKey("info_ask_answered_ids")

    /** Field ids already shown today for [childId] — so the banner does not repeat the same gap. */
    suspend fun infoAskShownFieldIdsToday(childId: String): Set<String> {
        val prefs = context.dataStore.data.first()
        val today = java.time.LocalDate.now().toString()
        if (prefs[INFO_ASK_SHOWN_DATE] != today) return emptySet()
        return fieldIdsForChild(prefs[INFO_ASK_SHOWN_IDS], childId)
    }

    suspend fun recordInfoAskFieldShown(childId: String, fieldId: String) =
        recordInfoAskFieldsShown(childId, listOf(fieldId))

    suspend fun recordInfoAskFieldsShown(childId: String, fieldIds: Collection<String>) {
        if (fieldIds.isEmpty()) return
        val today = java.time.LocalDate.now().toString()
        context.dataStore.edit { prefs ->
            val reset = prefs[INFO_ASK_SHOWN_DATE] != today
            val current = if (reset) emptyList() else (prefs[INFO_ASK_SHOWN_IDS] ?: "").split(',').filter { it.isNotBlank() }
            prefs[INFO_ASK_SHOWN_DATE] = today
            prefs[INFO_ASK_SHOWN_IDS] = (current + fieldIds.map { "$childId|$it" }).distinct().joinToString(",")
        }
    }

    /** One-shot fields already answered for this child (e.g. activity_level). */
    suspend fun infoAskAnsweredFieldIds(childId: String): Set<String> =
        fieldIdsForChild(context.dataStore.data.first()[INFO_ASK_ANSWERED_IDS], childId)

    suspend fun recordInfoAskFieldAnswered(childId: String, fieldId: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[INFO_ASK_ANSWERED_IDS] ?: "").split(',').filter { it.isNotBlank() }
            prefs[INFO_ASK_ANSWERED_IDS] = (current + "$childId|$fieldId").distinct().joinToString(",")
        }
    }

    private fun fieldIdsForChild(raw: String?, childId: String): Set<String> =
        (raw ?: "").split(',')
            .map { it.trim() }
            .filter { it.startsWith("$childId|") }
            .map { it.substringAfter('|') }
            .toSet()

    private val ADD_CHILD_PROMPT_AT = stringPreferencesKey("add_child_prompt_shown_at")

    /** Whether the "add a child" nudge popup is allowed to show again — gated to once per 4 hours
     *  (childless accounts only) instead of firing on every screen visit. */
    suspend fun shouldShowAddChildPrompt(): Boolean {
        val last = context.dataStore.data.first()[ADD_CHILD_PROMPT_AT]
            ?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() } ?: return true
        return java.time.Duration.between(last, java.time.Instant.now()).toHours() >= 4
    }

    suspend fun recordAddChildPromptShown() {
        context.dataStore.edit { it[ADD_CHILD_PROMPT_AT] = java.time.Instant.now().toString() }
    }

    private val RECENT_SERVICES = stringPreferencesKey("recent_services")

    /** The child the user is currently viewing (multi-child support). Null = not set yet. */
    val selectedChildId: Flow<String?> = context.dataStore.data.map { it[SELECTED_CHILD] }

    suspend fun setSelectedChildId(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(SELECTED_CHILD) else prefs[SELECTED_CHILD] = value
        }
    }

    private val AUTO_RECORD = booleanPreferencesKey("auto_record")

    suspend fun setAutoRecord(value: Boolean) {
        context.dataStore.edit { it[AUTO_RECORD] = value }
    }

    private val APP_LOCK = booleanPreferencesKey("app_lock")

    suspend fun setAppLock(value: Boolean) {
        context.dataStore.edit { it[APP_LOCK] = value }
    }

    private val SELECTED_MASCOT = stringPreferencesKey("selected_mascot")

    /** The AI companion the parent picked at the end of onboarding. Null = never chose. */
    val selectedMascot: Flow<String?> = context.dataStore.data.map { it[SELECTED_MASCOT] }

    suspend fun setSelectedMascot(value: String) {
        context.dataStore.edit { it[SELECTED_MASCOT] = value }
    }

    private fun unlockAnnouncedKey(mascot: String) = intPreferencesKey("unlock_announced_$mascot")
    private val UNLOCK_ANNOUNCED_BEST = intPreferencesKey("unlock_announced_best")

    /** Last bond level whose unlocks were shown for this companion. Null = never seeded. */
    suspend fun announcedUnlockLevel(mascot: String): Int? =
        context.dataStore.data.first()[unlockAnnouncedKey(mascot)]

    suspend fun setAnnouncedUnlockLevel(mascot: String, level: Int) {
        context.dataStore.edit { it[unlockAnnouncedKey(mascot)] = level }
    }

    /** Best bond whose companion-unlock toasts were shown. Null = never seeded. */
    suspend fun announcedBestLevel(): Int? =
        context.dataStore.data.first()[UNLOCK_ANNOUNCED_BEST]

    suspend fun setAnnouncedBestLevel(level: Int) {
        context.dataStore.edit { it[UNLOCK_ANNOUNCED_BEST] = level }
    }

    private val SERVICE_USAGE = stringPreferencesKey("service_usage")
    private val REMINDED_KEYS = stringPreferencesKey("vaccine_reminded_keys")
    private val MEDICINE_REMINDED_KEYS = stringPreferencesKey("medicine_reminded_keys")
    private val DEVICE_WARNING_KEYS = stringPreferencesKey("device_warning_keys")
    private val DOCTOR_VACCINE_INBOX_DISMISSED = stringPreferencesKey("doctor_vaccine_inbox_dismissed")
    private val DOCTOR_SCHEDULE_NUDGE_DISMISSED = booleanPreferencesKey("doctor_schedule_nudge_dismissed")
    private val LAST_AUTH_PORTAL = stringPreferencesKey("last_auth_portal")
    private val portalPrefs by lazy {
        context.getSharedPreferences("nongrak_portal", Context.MODE_PRIVATE)
    }

    companion object {
        const val PORTAL_PARENT = "parent"
        const val PORTAL_DOCTOR = "doctor"
        private const val PREFS_LAST_PORTAL = "last_auth_portal"
    }

    /** Synchronous read for cold-start routing before DataStore hydrates. */
    fun getLastAuthPortalSync(): String =
        portalPrefs.getString(PREFS_LAST_PORTAL, PORTAL_PARENT) ?: PORTAL_PARENT

    /** Last portal the user signed into — keeps doctor vs parent on cold start when both roles exist. */
    suspend fun getLastAuthPortal(): String {
        val fromStore = context.dataStore.data.first()[LAST_AUTH_PORTAL]
        if (fromStore != null) return fromStore
        return getLastAuthPortalSync()
    }

    suspend fun setLastAuthPortal(portal: String) {
        portalPrefs.edit().putString(PREFS_LAST_PORTAL, portal).apply()
        context.dataStore.edit { it[LAST_AUTH_PORTAL] = portal }
    }

    suspend fun isDoctorScheduleNudgeDismissed(): Boolean =
        context.dataStore.data.first()[DOCTOR_SCHEDULE_NUDGE_DISMISSED] == true

    suspend fun setDoctorScheduleNudgeDismissed(dismissed: Boolean = true) {
        context.dataStore.edit { it[DOCTOR_SCHEDULE_NUDGE_DISMISSED] = dismissed }
    }

    /** Parent dismissed "doctor sent vaccine verification" popups (request id list). */
    suspend fun getDismissedDoctorVaccineInboxIds(): Set<String> =
        context.dataStore.data.first()[DOCTOR_VACCINE_INBOX_DISMISSED]?.split("|||")?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()

    suspend fun dismissDoctorVaccineInboxId(id: String) {
        val next = getDismissedDoctorVaccineInboxIds() + id
        context.dataStore.edit { it[DOCTOR_VACCINE_INBOX_DISMISSED] = next.joinToString("|||") }
    }

    /** Dedupe markers (e.g. "vaccineId:3") so a vaccine reminder fires once per offset, not repeatedly. */
    suspend fun getRemindedKeys(): Set<String> =
        context.dataStore.data.first()[REMINDED_KEYS]?.split("|||")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    suspend fun setRemindedKeys(keys: Set<String>) {
        context.dataStore.edit { it[REMINDED_KEYS] = keys.joinToString("|||") }
    }

    /** Dedupe markers ("medicine:doseId") so a medicine dose reminder fires once, not repeatedly.
     *  Kept separate from [REMINDED_KEYS] so each worker's own pruning logic can't wipe the other's keys. */
    suspend fun getMedicineRemindedKeys(): Set<String> =
        context.dataStore.data.first()[MEDICINE_REMINDED_KEYS]?.split("|||")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    suspend fun setMedicineRemindedKeys(keys: Set<String>) {
        context.dataStore.edit { it[MEDICINE_REMINDED_KEYS] = keys.joinToString("|||") }
    }

    /** Dedupe markers for wearable/IoT warning notifications ("childId:metric:recordedAt"). */
    suspend fun getDeviceWarningKeys(): Set<String> =
        context.dataStore.data.first()[DEVICE_WARNING_KEYS]?.split("|||")?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()

    suspend fun setDeviceWarningKeys(keys: Set<String>) {
        context.dataStore.edit { it[DEVICE_WARNING_KEYS] = keys.joinToString("|||") }
    }

    /** Recently-opened service keys, newest first (comma-separated). For the dashboard "บริการล่าสุด". */
    val recentServices: Flow<List<String>> = context.dataStore.data.map {
        it[RECENT_SERVICES]?.split(",")?.filter { s -> s.isNotBlank() } ?: emptyList()
    }

    /** How many times each service was opened (key:count,...). For ordering "บริการหลัก" by activity. */
    val serviceUsage: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        (prefs[SERVICE_USAGE] ?: "").split(",").mapNotNull { entry ->
            val p = entry.split(":"); if (p.size == 2) p[0] to (p[1].toIntOrNull() ?: 0) else null
        }.toMap()
    }

    /** Records a service open: bumps recency AND the usage count (both feed the dashboard). */
    suspend fun pushRecentService(key: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[RECENT_SERVICES]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[RECENT_SERVICES] = (listOf(key) + cur.filter { it != key }).take(4).joinToString(",")

            val counts = (prefs[SERVICE_USAGE] ?: "").split(",").mapNotNull { e ->
                val p = e.split(":"); if (p.size == 2) p[0] to (p[1].toIntOrNull() ?: 0) else null
            }.toMap().toMutableMap()
            counts[key] = (counts[key] ?: 0) + 1
            prefs[SERVICE_USAGE] = counts.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            darkMode = prefs[DARK_MODE] ?: false,
            language = if (prefs[LANGUAGE] == "EN") AppLanguage.EN else AppLanguage.TH,
            pushNotifications = prefs[PUSH_NOTIFICATIONS] ?: true,
            dailyReport = prefs[DAILY_REPORT] ?: true,
            healthAlert = prefs[HEALTH_ALERT] ?: true,
            aiTips = prefs[AI_TIPS] ?: false,
            // Defaults to off: the AI asking before it writes is the safe starting point, and
            // the parent opts in to unattended logging once they trust it.
            autoRecord = prefs[AUTO_RECORD] ?: false,
            twoFactorEnabled = prefs[TWO_FACTOR_ENABLED] ?: false,
            appLock = prefs[APP_LOCK] ?: false
        )
    }

    suspend fun setTwoFactorEnabled(value: Boolean) {
        context.dataStore.edit { it[TWO_FACTOR_ENABLED] = value }
    }

    suspend fun setDarkMode(value: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = value }
    }

    suspend fun setLanguage(value: AppLanguage) {
        context.dataStore.edit { it[LANGUAGE] = value.name }
    }

    suspend fun setPushNotifications(value: Boolean) {
        context.dataStore.edit { it[PUSH_NOTIFICATIONS] = value }
    }

    suspend fun setDailyReport(value: Boolean) {
        context.dataStore.edit { it[DAILY_REPORT] = value }
    }

    suspend fun setHealthAlert(value: Boolean) {
        context.dataStore.edit { it[HEALTH_ALERT] = value }
    }

    suspend fun setAiTips(value: Boolean) {
        context.dataStore.edit { it[AI_TIPS] = value }
    }

    private val GALLERY_AUTO_CHECK = booleanPreferencesKey("gallery_auto_check")
    private val GALLERY_AUTO_MOOD = booleanPreferencesKey("gallery_auto_mood")
    private val GALLERY_DETECT_FIRST_STEPS = booleanPreferencesKey("gallery_detect_first_steps")
    private val GALLERY_SMART_HIGHLIGHTS = booleanPreferencesKey("gallery_smart_highlights")
    private val CHILD_RECOGNITION_PRIMARY = stringPreferencesKey("child_recognition_primary")
    private val CHILD_RECOGNITION_REFS = stringPreferencesKey("child_recognition_refs")

    val galleryAutoCheck: Flow<Boolean> = context.dataStore.data.map { it[GALLERY_AUTO_CHECK] ?: true }
    suspend fun setGalleryAutoCheck(enabled: Boolean) { context.dataStore.edit { it[GALLERY_AUTO_CHECK] = enabled } }

    val galleryAutoMood: Flow<Boolean> = context.dataStore.data.map { it[GALLERY_AUTO_MOOD] ?: true }
    suspend fun setGalleryAutoMood(enabled: Boolean) { context.dataStore.edit { it[GALLERY_AUTO_MOOD] = enabled } }

    val galleryDetectFirstSteps: Flow<Boolean> = context.dataStore.data.map { it[GALLERY_DETECT_FIRST_STEPS] ?: true }
    suspend fun setGalleryDetectFirstSteps(enabled: Boolean) { context.dataStore.edit { it[GALLERY_DETECT_FIRST_STEPS] = enabled } }

    val gallerySmartHighlights: Flow<Boolean> = context.dataStore.data.map { it[GALLERY_SMART_HIGHLIGHTS] ?: false }
    suspend fun setGallerySmartHighlights(enabled: Boolean) { context.dataStore.edit { it[GALLERY_SMART_HIGHLIGHTS] = enabled } }

    val childRecognitionPrimary: Flow<String?> = context.dataStore.data.map { it[CHILD_RECOGNITION_PRIMARY] }
    suspend fun setChildRecognitionPrimary(uri: String?) {
        context.dataStore.edit { if (uri != null) it[CHILD_RECOGNITION_PRIMARY] = uri else it.remove(CHILD_RECOGNITION_PRIMARY) }
    }

    val childRecognitionRefs: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[CHILD_RECOGNITION_REFS]
        if (raw.isNullOrBlank()) emptyList()
        else raw.split(";;;").filter { it.isNotBlank() }
    }
    suspend fun setChildRecognitionRefs(refs: List<String>) {
        context.dataStore.edit { it[CHILD_RECOGNITION_REFS] = refs.joinToString(";;;") }
    }

    private val SYNC_CAMERA_ROLL = booleanPreferencesKey("sync_camera_roll")
    private val SYNC_CLOUD_DRIVE = booleanPreferencesKey("sync_cloud_drive")
    private val SYNC_CUSTOM_SOURCES = stringPreferencesKey("sync_custom_sources")

    val syncCameraRoll: Flow<Boolean> = context.dataStore.data.map { it[SYNC_CAMERA_ROLL] ?: true }
    suspend fun setSyncCameraRoll(enabled: Boolean) { context.dataStore.edit { it[SYNC_CAMERA_ROLL] = enabled } }

    val syncCloudDrive: Flow<Boolean> = context.dataStore.data.map { it[SYNC_CLOUD_DRIVE] ?: true }
    suspend fun setSyncCloudDrive(enabled: Boolean) { context.dataStore.edit { it[SYNC_CLOUD_DRIVE] = enabled } }

    val syncCustomSources: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[SYNC_CUSTOM_SOURCES]
        if (raw.isNullOrBlank()) emptyList()
        else raw.split(";;;").filter { it.isNotBlank() }
    }
    suspend fun addCustomSource(sourceName: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[SYNC_CUSTOM_SOURCES]?.split(";;;")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[SYNC_CUSTOM_SOURCES] = (cur + sourceName).distinct().joinToString(";;;")
        }
    }
    suspend fun removeCustomSource(sourceName: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[SYNC_CUSTOM_SOURCES]?.split(";;;")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[SYNC_CUSTOM_SOURCES] = cur.filter { it != sourceName }.joinToString(";;;")
        }
    }

    val hasAcceptedCommunityTerms: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[COMMUNITY_TERMS_ACCEPTED] ?: false
    }

    suspend fun setCommunityTermsAccepted(accepted: Boolean = true) {
        context.dataStore.edit { it[COMMUNITY_TERMS_ACCEPTED] = accepted }
    }
}


