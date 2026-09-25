package com.lullabyai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Monthly parent (not child) mental-health self-assessment — modeled on the DMH-endorsed EPDS
 *  (Edinburgh Postnatal Depression Scale) framework: 10 items, each 0-3, total 0-30. */
@Serializable
data class MentalHealthAssessment(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val answers: List<Int> = emptyList(),
    @SerialName("total_score") val totalScore: Int = 0,
    @SerialName("risk_tier") val riskTier: String = "low", // low | moderate | high
    @SerialName("flagged_self_harm") val flaggedSelfHarm: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MedicalDocument(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("file_path") val filePath: String = "",
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Set when this document was attached from RecordVisitScreen/VisitDetailsScreen — lets a
    // doctor-visit pull just its own attachments instead of the child's whole document vault.
    @SerialName("doctor_visit_id") val doctorVisitId: String? = null,
    /** Vault category: identity | certs | lab | other (Stitch Document Vault chips). */
    val category: String = "other",
    @SerialName("file_size_bytes") val fileSizeBytes: Long? = null,
    /** Verified badge (e.g. birth certificate / identity docs). */
    val verified: Boolean = false
)

@Serializable
data class MedicationSchedule(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("medicine_name") val medicineName: String = "",
    val dosage: Double? = null,
    @SerialName("dosage_unit") val dosageUnit: String = "mg",
    val frequency: String? = null,
    @SerialName("schedule_type") val scheduleType: String = "times", // date_range | times | until_gone
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("num_doses") val numDoses: Int? = null,
    @SerialName("doses_taken") val dosesTaken: Int = 0,
    val notes: String? = null,
    val status: String = "active", // active | completed | stopped
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MedicationDose(
    val id: String = "",
    @SerialName("schedule_id") val scheduleId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("scheduled_at") val scheduledAt: String = "",
    @SerialName("taken_at") val takenAt: String? = null,
    val status: String = "pending", // pending | taken | skipped
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SleepLog(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("sleep_type") val sleepType: String = "main", // main | nap
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String? = null,       // null while a session is in progress
    val mood: String? = null,
    val notes: String? = null,
    /** Parent-rated sleep quality 1–5 (nullable = not rated). Agent #7 Pro Sleep. */
    @SerialName("quality_stars") val qualityStars: Int? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SleepSchedule(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val label: String = "",
    @SerialName("sleep_type") val sleepType: String = "main",
    @SerialName("start_time") val startTime: String = "",   // "HH:mm"
    @SerialName("end_time") val endTime: String = "",        // "HH:mm"
    @SerialName("days_of_week") val daysOfWeek: String = "ALL", // "MON,TUE,..." or "ALL"
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Profile(
    val id: String = "",
    val username: String? = null,
    val relationship: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    // Separate, opt-in consent for using (de-identified) data to improve/train AI models —
    // deliberately distinct from the mandatory signup consent, since it's a different purpose
    // under PDPA and must be freely given, not bundled. Off by default.
    @SerialName("ai_training_consent") val aiTrainingConsent: Boolean = false,
    @SerialName("ai_training_consent_at") val aiTrainingConsentAt: String? = null,
    @SerialName("account_type") val accountType: String = "parent", // legacy; prefer [roles]
    /** One auth identity may include both `parent` and `doctor` (same email, separate app areas). */
    val roles: List<String> = emptyList(),
    // Collected at sign-up (#13), stored in normalized Thai national form (e.g. 0812345678) by
    // ThaiPhone.normalize. Nullable + defaulted for the same reason as VaccineRecord.ageGroup:
    // accounts created before this field existed have no phone, and a non-nullable field makes
    // kotlinx throw on the explicit null and blanks the WHOLE profile.
    @SerialName("phone") val phone: String? = null,
    /** Set by confirm_email_ownership() once the parent proves the mailbox (optional). Null = not verified. */
    @SerialName("email_verified_at") val emailVerifiedAt: String? = null,
    /** Home address for the Health Book guardian block. Optional; collected via Info Ask. */
    val address: String? = null,
    /** Two-factor authentication toggle: when enabled, signing into a new device requires verification from an active device. */
    @SerialName("two_factor_enabled") val twoFactorEnabled: Boolean = false,
    @SerialName("two_factor_enabled_at") val twoFactorEnabledAt: String? = null
)

@Serializable
data class DoctorProfile(
    val id: String = "", // = profiles.id (auth user id)
    @SerialName("clinic_name") val clinicName: String? = null,
    val specialty: String? = null,
    @SerialName("license_number") val licenseNumber: String? = null,
    @SerialName("clinic_address") val clinicAddress: String? = null,
    @SerialName("clinic_photo_url") val clinicPhotoUrl: String? = null,
    val bio: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class DoctorAvailability(
    val id: String = "",
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("day_of_week") val dayOfWeek: Int = 0, // 0=Sunday .. 6=Saturday
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("is_day_off") val isDayOff: Boolean = false
)

@Serializable
data class DoctorAppointmentSlot(
    val id: String = "",
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("slot_date") val slotDate: String = "",
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String = "",
    val capacity: Int = 1,
    @SerialName("booked_count") val bookedCount: Int = 0,
    val status: String = "open" // open | closed
)

@Serializable
data class Appointment(
    val id: String = "",
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("slot_id") val slotId: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    val status: String = "pending", // pending | confirmed | completed | cancelled | rescheduled | skipped
    val reason: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class VaccineVerificationRequest(
    val id: String = "",
    @SerialName("vaccine_record_id") val vaccineRecordId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("doctor_id") val doctorId: String? = null,
    val status: String = "pending", // pending | verified | rejected
    val notes: String? = null,
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null
)

@Serializable
data class Child(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val name: String = "",
    val birthdate: String = "",
    val gender: String = "unspecified",
    val allergies: String? = null,
    val notes: String? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("sleep_hours") val sleepHours: Double? = null,
    @SerialName("activity_level") val activityLevel: String = "moderate",
    @SerialName("blood_group") val bloodGroup: String? = null,
    /** Birth-book fields — NongRak Health Book page 1. Null until the parent fills them (Info Ask or later). */
    @SerialName("delivery_type") val deliveryType: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("gestational_weeks") val gestationalWeeks: Int? = null,
    @SerialName("birth_weight_g") val birthWeightG: Double? = null,
    @SerialName("birth_length_cm") val birthLengthCm: Double? = null,
    @SerialName("birth_head_cm") val birthHeadCm: Double? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    /**
     * Secret in the digital ID card's QR. It addresses the public emergency page, so it is a token
     * and not the child id: a lost card is answered by rotating this, and it exposes nothing else.
     */
    @SerialName("card_token") val cardToken: String? = null,
    /** Legal name for documents. [name] stays the nickname shown across the app. */
    @SerialName("full_name") val fullName: String? = null,
    // Explicit "confirmed no allergies" — distinct from simply having zero rows in the allergies
    // table, so the missing-info nudge can tell "not yet answered" apart from "answered: none".
    @SerialName("no_known_allergies") val noKnownAllergies: Boolean = false,
    // Hospital-record demographic fields (สัญชาติ/เชื้อชาติ/ศาสนา) — collected on the first onboarding
    // page and printed on the doctor-export PDF, all optional.
    val nationality: String? = null,
    val ethnicity: String? = null,
    val religion: String? = null,
    /** เวลาเกิด — printed beside the birthdate on the Health Book and the ID card. */
    @SerialName("birth_time") val birthTime: String? = null,
    /** เลขประจำตัวประชาชน / รหัสเด็ก. */
    @SerialName("national_id") val nationalId: String? = null,
    /** ข้อสังเกตและวิธีปลอบใจ — free text that travels with the favourites to the caretaker. */
    @SerialName("soothing_note") val soothingNote: String? = null,
    /**
     * แชร์ให้ผู้ดูแลเห็น. Off hides favourites and [soothingNote] from nanny/doctor/other; enforced
     * in the database, not here, so turning it off is not merely a hidden row in the UI.
     */
    @SerialName("favorites_shared") val favoritesShared: Boolean = true,
    @SerialName("previous_owner_email") val previousOwnerEmail: String? = null,
    @SerialName("previous_owner_id") val previousOwnerId: String? = null,
    @SerialName("transferred_from_role") val transferredFromRole: String? = null,
    @SerialName("transferred_at") val transferredAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * สิ่งที่น้องชอบ — one thing this child likes, or one trick that calms him.
 *
 * Parents write them, the AI adds what it notices in activity, and the daily question banner can
 * ask for one. [source] keeps those apart so a parent can see which are their own words.
 */
@Serializable
data class ChildFavorite(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val category: String = FavoriteCategory.TOY,
    val label: String = "",
    val source: String = FavoriteSource.PARENT,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** Must match the category check constraint on public.child_favorites. */
object FavoriteCategory {
    const val TOY = "toy"
    const val FOOD = "food"
    const val SOUND = "sound"
    const val ACTIVITY = "activity"
    const val SOOTHING = "soothing"
    const val INTERACTION = "interaction"

    /** The order the edit page and the handover page both list them in. */
    val ordered = listOf(TOY, FOOD, SOUND, ACTIVITY, SOOTHING, INTERACTION)

    fun labelTh(category: String): String = when (category) {
        TOY -> "ของเล่นและของชิ้นโปรด"
        FOOD -> "อาหารและรสชาติที่ชอบ"
        SOUND -> "เพลงและเสียงที่ช่วยผ่อนคลาย"
        ACTIVITY -> "กิจกรรมโปรด"
        SOOTHING -> "ทริคกล่อมนอน & ปลอบโยน"
        INTERACTION -> "คำพูด & ปฏิสัมพันธ์ที่คุ้นเคย"
        else -> "อื่นๆ"
    }

    fun labelEn(category: String): String = when (category) {
        TOY -> "Toys & comfort objects"
        FOOD -> "Favourite food & flavours"
        SOUND -> "Calming songs & sounds"
        ACTIVITY -> "Favourite activities"
        SOOTHING -> "Soothing hacks"
        INTERACTION -> "Familiar words & interaction"
        else -> "Other"
    }

    fun label(category: String, isThai: Boolean): String =
        if (isThai) labelTh(category) else labelEn(category)
}

/** Must match the source check constraint on public.child_favorites. */
object FavoriteSource {
    const val PARENT = "parent"
    const val AI = "ai"
    const val INFO_ASK = "info_ask"
}

/** A candidate match returned by the search_child_for_sync RPC (name + birthdate, no other data). */
@Serializable
data class ChildSyncMatch(
    @SerialName("child_id") val childId: String = "",
    @SerialName("child_name") val childName: String = ""
)

/** A child previously owned by the user before account deletion/transfer, eligible for ownership reclaim. */
@Serializable
data class ReclaimableChild(
    @SerialName("child_id") val childId: String = "",
    @SerialName("child_name") val childName: String = "",
    @SerialName("birth_date") val birthDate: String = "",
    @SerialName("current_owner_name") val currentOwnerName: String = "",
    @SerialName("transferred_at") val transferredAt: String? = null
)

/** A pending/resolved request to sync (co-parent) access to someone else's child. */
@Serializable
data class ChildSyncRequest(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("requester_user_id") val requesterUserId: String = "",
    @SerialName("owner_user_id") val ownerUserId: String = "",
    val method: String = "search",
    val status: String = "pending",
    @SerialName("requested_role") val requestedRole: String = "co_owner",
    @SerialName("requester_label") val requesterLabel: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** Member row from list_child_access_members — role + granular toggles. */
@Serializable
data class ChildAccessMember(
    @SerialName("user_id") val userId: String = "",
    val role: String = "co_owner",
    @SerialName("display_name") val displayName: String = "",
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("can_view_health") val canViewHealth: Boolean = true,
    @SerialName("can_log_symptoms") val canLogSymptoms: Boolean = false,
    @SerialName("can_edit_growth") val canEditGrowth: Boolean = false,
    @SerialName("can_view_appointments") val canViewAppointments: Boolean = true,
    @SerialName("can_book_doctor") val canBookDoctor: Boolean = false,
    @SerialName("can_cancel_appointments") val canCancelAppointments: Boolean = false,
    @SerialName("can_view_documents") val canViewDocuments: Boolean = true,
    @SerialName("can_upload_documents") val canUploadDocuments: Boolean = false,
    @SerialName("can_edit_profile") val canEditProfile: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    // capabilities is the canonical store server-side; the can_* fields above are generated columns
    // over it, kept because ~4 files already read them.
    val capabilities: List<String> = emptyList(),
    @SerialName("role_id") val roleId: String? = null,
    @SerialName("role_name_th") val roleNameTh: String? = null,
    @SerialName("role_name_en") val roleNameEn: String? = null,
    // "บุคคลที่ช่วยดูแลเด็ก" — deliberately not one of the can_* permissions. It answers
    // "does this person physically care for the child", which is what decides who appears in
    // the caregiver chat row and who lands on the daily-update screen instead of the history.
    @SerialName("is_caregiver") val isCaregiver: Boolean = false
)

/**
 * One recorded cost for a child.
 *
 * [amountSatang] is THB × 100 as an integer — money in a Double eventually rounds wrong, and a
 * budget that quietly drifts is worse than none. Format at the edges; never store the format.
 */
@Serializable
data class ChildExpense(
    val id: String = "",
    /** Which child this belongs to — needed once "ทั้งหมด" mixes several children into one list. */
    @SerialName("child_id") val childId: String? = null,
    @SerialName("amount_satang") val amountSatang: Long = 0,
    val category: String = "other",
    @SerialName("spent_on") val spentOn: String = "",
    /** What was bought — the design's row title ("Vaccine MMR"), distinct from the free-text note. */
    val name: String? = null,
    val note: String? = null,
    /**
     * The design's three switches. They are independent on purpose: something can be under
     * warranty and already used, and a tin of formula can have an expiry date and no warranty.
     * Each date is only meaningful while its own switch is on — the table enforces that.
     */
    @SerialName("has_warranty") val hasWarranty: Boolean = false,
    @SerialName("warranty_expires_on") val warrantyExpiresOn: String? = null,
    @SerialName("has_expiration") val hasExpiration: Boolean = false,
    @SerialName("expires_on") val expiresOn: String? = null,
    @SerialName("already_used") val alreadyUsed: Boolean = false,
    /**
     * When "used" was ticked. Stamped by the database, never by the client, because it starts the
     * 30-minute edit window after which the row is frozen — a clock the editor could set would be
     * no lock at all. Null whenever [alreadyUsed] is false.
     */
    @SerialName("used_at") val usedAt: String? = null,
    @SerialName("usage_note") val usageNote: String? = null,
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    val baht: Double get() = amountSatang / 100.0
}

/** One row of the spending summary — a category and what it cost over the window. */
@Serializable
data class ExpenseCategoryTotal(
    val category: String = "",
    @SerialName("total_satang") val totalSatang: Long = 0,
    val entries: Int = 0
)

/**
 * Budget beside spend for one slot of a month. A null [category] is the month's overall figure,
 * so the same row shape drives both the headline bar and the per-category breakdown.
 */
@Serializable
data class BudgetLine(
    val category: String? = null,
    @SerialName("budget_satang") val budgetSatang: Long = 0,
    @SerialName("spent_satang") val spentSatang: Long = 0
) {
    val hasBudget: Boolean get() = budgetSatang > 0
    /** 0f..1f, clamped: a bar drawn past its end reads as a rendering bug, not as overspending. */
    val progress: Float
        get() = if (budgetSatang <= 0) 0f
        else (spentSatang.toFloat() / budgetSatang).coerceIn(0f, 1f)
    val overBudget: Boolean get() = budgetSatang > 0 && spentSatang > budgetSatang
    val remainingSatang: Long get() = budgetSatang - spentSatang
}

/**
 * One entry on ตารางนัดหมายและวันสำคัญ — an appointment, a vaccine, a milestone or a family outing.
 *
 * All four share this shape because the calendar treats them as one stream: the same day cell, the
 * same timeline, the same search. [category] is only what colours the dot.
 */
@Serializable
data class ChildActivity(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val title: String = "",
    val category: String = "family",
    @SerialName("starts_at") val startsAt: String = "",
    @SerialName("ends_at") val endsAt: String? = null,
    /** Milestones sit on a date, not a time — "ตลอดวัน (16 พ.ย.)". */
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("location_name") val locationName: String? = null,
    /** Present when this came from a Places suggestion, so the venue stays identifiable. */
    @SerialName("place_id") val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val notes: String? = null,
    @SerialName("estimated_cost_satang") val estimatedCostSatang: Long? = null,
    /** Set once the outing is actually paid for, tying the plan to the real spend. */
    @SerialName("expense_id") val expenseId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    val hasPlace: Boolean get() = !placeId.isNullOrBlank() || !locationName.isNullOrBlank()
}

/**
 * The stored suggestion batch. [isFresh] is computed by Postgres against the refresh window, so a
 * device with a wrong clock cannot decide to regenerate on every visit — or never.
 *
 * [items] stays raw JSON here: `core:model` has no reason to know the shape of a recommendation,
 * which belongs to the layer that produces it.
 */
@Serializable
data class CachedRecommendations(
    val items: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("home_only") val homeOnly: Boolean = false,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("is_fresh") val isFresh: Boolean = false
)

/** One row of "สรุปภาพรวม 7 วันข้างหน้า". Categories with nothing scheduled still appear, as zero. */
@Serializable
data class ActivityCategoryCount(
    val category: String = "",
    val entries: Int = 0
)

/** One bar of the trend chart. [month] is the first day of that month, `yyyy-MM-dd`. */
@Serializable
data class ExpenseTrendPoint(
    val month: String = "",
    @SerialName("total_satang") val totalSatang: Long = 0
)

/**
 * One row of the Care Circle hub: a person you can talk to about this child, plus the last thing
 * said. Everyone with access appears even with no history, so the hub doubles as the contact picker.
 */
@Serializable
data class CareConversation(
    @SerialName("peer_id") val peerId: String = "",
    @SerialName("peer_name") val peerName: String = "",
    @SerialName("peer_avatar") val peerAvatar: String? = null,
    @SerialName("peer_role") val peerRole: String = "",
    @SerialName("last_body") val lastBody: String? = null,
    @SerialName("last_at") val lastAt: String? = null,
    @SerialName("last_from_me") val lastFromMe: Boolean = false,
    @SerialName("last_has_image") val lastHasImage: Boolean = false,
    val unread: Int = 0
) {
    val hasHistory: Boolean get() = !lastAt.isNullOrBlank()
}

/** One message in a parent↔caregiver conversation about a child. */
@Serializable
data class CareMessage(
    val id: String = "",
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
    /** Server-computed, so the bubble side never depends on the client knowing its own id. */
    val mine: Boolean = false,
    /**
     * When the sender withdrew this message. [body] stays populated on purpose — an unsend in a
     * childcare record is a withdrawal ON the record, not an erasure of it.
     */
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_to_body") val replyToBody: String? = null,
    @SerialName("reply_to_sender") val replyToSender: String? = null,
    /** Bucket path in the private `chat-images` bucket, signed at render time. */
    @SerialName("image_url") val imageUrl: String? = null,
    /** Set when the recipient's device fetched this message. */
    @SerialName("delivered_at") val deliveredAt: String? = null,
    /** Set when the recipient actually opened the conversation. */
    @SerialName("read_at") val readAt: String? = null
) {
    val isUnsent: Boolean get() = !deletedAt.isNullOrBlank()

    /** WhatsApp's three states. Only meaningful for messages I sent. */
    val status: DeliveryStatus
        get() = when {
            !readAt.isNullOrBlank() -> DeliveryStatus.READ
            !deliveredAt.isNullOrBlank() -> DeliveryStatus.DELIVERED
            else -> DeliveryStatus.SENT
        }
}

/** One tick = sent, two grey = delivered, two blue = read. */
enum class DeliveryStatus { SENT, DELIVERED, READ }

/** One tap on the caregiver's quick-record grid. [abnormal] non-null is what a parent must see. */
@Serializable
data class CareLog(
    val id: String = "",
    val kind: String = "",
    val note: String? = null,
    val mood: String? = null,
    val abnormal: String? = null,
    @SerialName("logged_at") val loggedAt: String = "",
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    /** care-media object paths — private; shown through short-lived signed URLs only. */
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("voice_path") val voicePath: String? = null,
    @SerialName("voice_seconds") val voiceSeconds: Int? = null
)

/**
 * One change to a record, from record_edits. [oldRow] and [newRow] are the whole row as it was and
 * as it became, so any field's before/after can be read without knowing the table's shape.
 */
@Serializable
data class RecordEdit(
    val id: Long = 0,
    @SerialName("child_id") val childId: String = "",
    @SerialName("table_name") val tableName: String = "",
    @SerialName("record_id") val recordId: String = "",
    val action: String = "update",
    @SerialName("old_row") val oldRow: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("new_row") val newRow: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

/** "คุณแม่อ่านแล้ว": one person having seen one record. */
@Serializable
data class RecordRead(
    @SerialName("record_id") val recordId: String = "",
    @SerialName("reader_id") val readerId: String = "",
    @SerialName("read_at") val readAt: String = ""
)

/**
 * A person who could be invited to a child. Returned only to owners/co-owners, only for queries
 * of 3+ characters, and only on a name prefix or a full email — an address can be confirmed
 * but never discovered.
 */
@Serializable
data class InviteCandidate(
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

/**
 * One row of the child's activity history — a record of any kind, with who wrote it.
 *
 * [detail] arrives pre-composed from the server in units that read the same in both languages
 * (kg, cm, ml, min); the human-facing title is derived from [kind] on the client so it stays
 * bilingual. [category] is what the filter chips match on: growth / medical / nutrition / daily.
 */
@Serializable
data class ChildActivityEntry(
    @SerialName("source_id") val sourceId: String = "",
    val kind: String = "",
    val category: String = "",
    @SerialName("occurred_at") val occurredAt: String = "",
    val detail: String? = null,
    val note: String? = null,
    @SerialName("author_id") val authorId: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_avatar") val authorAvatar: String? = null
)

/**
 * A named bundle of capabilities. `childId == null` marks the built-in presets shared by every
 * child; custom roles belong to one child and only its owner may edit them.
 */
@Serializable
data class ChildRole(
    val id: String = "",
    val key: String = "",
    @SerialName("name_th") val nameTh: String = "",
    @SerialName("name_en") val nameEn: String = "",
    val capabilities: List<String> = emptyList(),
    @SerialName("is_system") val isSystem: Boolean = false,
    @SerialName("is_customized") val isCustomized: Boolean = false,
    @SerialName("base_role_key") val baseRoleKey: String? = null,
    @SerialName("member_count") val memberCount: Int = 0
)

/** Activity row for the Access Activity feed. */
@Serializable
data class ChildAccessActivity(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("actor_user_id") val actorUserId: String? = null,
    @SerialName("target_user_id") val targetUserId: String? = null,
    @SerialName("event_type") val eventType: String = "",
    val role: String? = null,
    val detail: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** Result of generate_child_access_invite (code or QR, with TTL). */
@Serializable
data class ChildAccessInvite(
    val code: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    val role: String = "co_owner",
    @SerialName("invite_type") val inviteType: String = "code",
    val payload: String = ""
)

@Serializable
data class MedicalHistory(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("has_taken_medication") val hasTakenMedication: Boolean = false,
    @SerialName("medication_details") val medicationDetails: String? = null,
    @SerialName("underlying_conditions") val underlyingConditions: String? = null,
    @SerialName("vaccines_given") val vaccinesGiven: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class VaccineRecord(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("vaccine_name") val vaccineName: String = "",
    @SerialName("date_given") val dateGiven: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    // Nullable: legacy DB rows may have NULL status; a non-nullable field would break list decoding.
    // status: scheduled | upcoming | appointment | overdue | completed | skipped
    val status: String? = "scheduled",
    val notes: String? = null,                 // also holds the skip reason when status == "skipped"
    @SerialName("place") val place: String? = null,   // hospital / clinic where the dose was/will be given
    // Nullable for the same reason: older seed generations left NULL age_group/age_order on some
    // rows. A non-nullable field makes kotlinx throw on the explicit null and blanks the WHOLE list.
    @SerialName("age_group") val ageGroup: String? = "แรกเกิด",
    @SerialName("age_order") val ageOrder: Int? = 0,
    // #10 ใบรับรองวัคซีน — storage path of the uploaded certificate; null = not uploaded yet.
    @SerialName("certificate_url") val certificateUrl: String? = null,
    // #10 provenance: "moph_book" = read off a photographed สมุดวัคซีนกระทรวงสาธารณสุข,
    // null/"manual" = typed by the parent. Drives the badge in the book and in the PDF.
    val source: String? = null,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MilkLog(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("milk_type") val milkType: String = "formula", // breast | formula | uht | water
    @SerialName("volume_ml") val volumeMl: Int = 0,
    /** Minutes at the breast for a direct feed; volumeMl is 0 then. Null for bottle/formula. */
    @SerialName("duration_min") val durationMin: Int? = null,
    val side: String? = null, // left | right | both (breast only)
    /** direct | pumped for breast milk; boiled | mineral | warm for water. */
    val subtype: String? = null,
    @SerialName("left_min") val leftMin: Int? = null,
    @SerialName("right_min") val rightMin: Int? = null,
    val temperature: String? = null,             // warm | room | cold
    /** finished | left_some | spit_up for milk; easy | little | refused for water. */
    val reaction: String? = null,
    val method: String? = null,                  // cup | straw | spoon | bottle (water)
    @SerialName("logged_at") val loggedAt: String? = null,
    val notes: String? = null
) {
    /** Boiled water shares this table (same RLS, edit history, AI tools) but is not a feed: every
     *  milk total, "last feed" and energy sum must skip it. */
    val isWater: Boolean get() = milkType == WATER

    companion object { const val WATER = "water" }
}

@Serializable
data class WasteRecord(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("waste_type") val wasteType: String = "stool", // urine | stool
    val consistency: String? = null, // stool only: soft_normal | hard_constipated | loose_diarrhea | mucus_blood
    val color: String? = null,       // yellow | brown | green | red_orange | grey_white
    val amount: String = "medium",   // low | medium | high
    @SerialName("recorded_at") val recordedAt: String? = null,
    val notes: String? = null
)

@Serializable
data class AppNotification(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val category: String = "normal",  // schedule | warning | normal
    val title: String = "",
    val body: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class GrowthRecord(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("record_type") val recordType: String = "manual",
    val notes: String? = null
)

@Serializable
data class ChronicCondition(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val name: String = "",
    val notes: String? = null,
    /** Parent-authored prevention tip for this named condition (Chronic Registry #14). */
    val prevention: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Allergy(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val name: String = "",
    val type: String = "food",          // food | environment | medication
    val severity: String = "mild",      // mild | moderate | severe
    val symptoms: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class HealthRecord(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("event_type") val eventType: String = "illness",
    val title: String = "",
    val description: String? = null,
    val severity: String = "mild",
    val temperature: Double? = null,
    @SerialName("additional_symptoms") val additionalSymptoms: List<String>? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    // recorded_at is date-only in practice (AddHealthRecordScreen only lets the user pick a date,
    // not a time), so two records logged the same day are indistinguishable by it alone — created_at
    // (a real insert-time timestamp, DB default now()) breaks the tie so the list order matches the
    // order they were actually logged in, not an arbitrary one.
    @SerialName("created_at") val createdAt: String? = null,
    // Set on content edits (not status toggles) — drives the "edited" badge (Batch Q #8).
    @SerialName("updated_at") val updatedAt: String? = null,
    // F3 — last time symptoms were recorded/updated on this episode (day-log or detail edit).
    // Edit-lock clock starts here (falls back to updated_at / created_at).
    @SerialName("symptoms_updated_at") val symptomsUpdatedAt: String? = null,
    // monitoring | recovered | unused — unused stays tickable after the 10-min edit lock.
    // Auto-resolved (7-day idle) is DERIVED in HealthRecordRules, never written here.
    val status: String = "monitoring"
)

// Multiple entries per day are allowed (no unique constraint on health_record_id + log_date) —
// each Save creates a new timeline row unless the caller explicitly passes an existingId to edit
// one in place. symptoms is a name->value map; value is a per-symptom pain level "0".."10" (string,
// so custom symptoms need no schema change). temperature is always stored canonically in Celsius;
// temperatureUnit just remembers which unit the parent was viewing/entering in.
@Serializable
data class SymptomLog(
    val id: String = "",
    @SerialName("health_record_id") val healthRecordId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("log_date") val logDate: String = "",
    val symptoms: Map<String, String> = emptyMap(),
    val temperature: Double? = null,        // canonical Celsius
    @SerialName("temperature_unit") val temperatureUnit: String = "C",  // C | F (display unit only)
    val feeling: String? = null,           // cold | normal | hot | warm | chilly
    @SerialName("pain_level") val painLevel: Int? = null,  // 0–10, derived = max of per-symptom levels
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("day_status") val dayStatus: String = "active",   // started | active | recovered
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** "บันทึกการหาหมอ" — either a future appointment (visitType="scheduled") or a completed visit
 *  log (visitType="completed"). status distinguishes list-badge state: scheduled | completed |
 *  follow_up (a completed visit with a next_appointment_date, i.e. needs a follow-up visit). */
@Serializable
data class DoctorVisit(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("visit_type") val visitType: String = "scheduled",
    @SerialName("visit_datetime") val visitDatetime: String = "",
    @SerialName("facility_name") val facilityName: String? = null,
    @SerialName("doctor_name") val doctorName: String? = null,
    @SerialName("doctor_specialty") val doctorSpecialty: String? = null,
    val reason: String? = null,
    val symptoms: List<String>? = null,
    val diagnosis: String? = null,
    @SerialName("treatment_plan") val treatmentPlan: String? = null,
    @SerialName("doctor_advice") val doctorAdvice: String? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("temperature_c") val temperatureC: Double? = null,
    @SerialName("blood_pressure") val bloodPressure: String? = null,
    @SerialName("next_appointment_date") val nextAppointmentDate: String? = null,
    @SerialName("preparation_notes") val preparationNotes: String? = null,
    val reminders: List<String>? = null,
    val status: String = "scheduled", // scheduled | completed | follow_up
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("doctor_id") val doctorId: String? = null,
    @SerialName("appointment_id") val appointmentId: String? = null
)

@Serializable
data class DevelopmentMilestone(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val category: String = "",
    @SerialName("milestone_name") val milestoneName: String = "",
    @SerialName("is_achieved") val isAchieved: Boolean = false,
    @SerialName("achieved_at") val achievedAt: String? = null,
    @SerialName("age_months_expected") val ageMonthsExpected: Int? = null,
    @SerialName("skip_reason") val skipReason: String? = null
)

@Serializable
data class ChatSession(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val title: String = "แชทใหม่",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ChatMessage(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("session_id") val sessionId: String? = null,
    val role: String = "user",
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    /** Set on medical answers, so the disclaimer banner survives reopening the chat. */
    val medical: Boolean = false
)

data class UiChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val imageUri: String? = null,   // local content:// URI of an attached image (display only, this session)
    val timestamp: Long = System.currentTimeMillis(),
    val agentAction: AgentAction? = null,   // non-null when the AI proposed a data-changing action
    val actionStatus: String? = null,       // null = awaiting confirmation, "confirmed", "cancelled", "failed"
    /** A medical answer: the chat shows the "not a diagnosis" banner above it. */
    val medical: Boolean = false,
    /** Record commands the ai-chat command center proposed with this answer; each runs only after Confirm. */
    val commands: List<AiCommand> = emptyList(),
    /** ticket -> "running" | "done" | "cancelled" | "failed:<named reason>". Missing = waiting for the tap. */
    val commandStatus: Map<String, String> = emptyMap()
)

/**
 * The AI asked a clarifying question via chat (e.g. it can't judge growth without the child's
 * current weight). No skip cooldown by design — skipping just dismisses this instance; if the AI
 * still lacks the info on a later turn, it asks again on its own.
 */
@Serializable
data class NeedInfoRequest(
    val question: String,
    val fieldLabel: String = ""
)

/**
 * A data-changing action the AI proposed via chat (log/edit/delete). Always requires an explicit
 * user tap to execute — see ChatViewModel.confirmAgentAction — nothing in [type]/[params] runs on
 * its own just because the model emitted it.
 */
@Serializable
data class AgentAction(
    val type: String,
    val confirmText: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * One record command from the ai-chat command center. [ticket] is signed by the function, single-use
 * and short-lived; the app only hands it back on Confirm (SparkChatClient.execute) and cannot alter it.
 * [fields] is display text (local times, baht), not what gets written.
 */
@Serializable
data class AiCommand(
    val ticket: String,
    val command: String,
    val label: String,
    val summary: String,
    val child: String = "",
    val fields: Map<String, String> = emptyMap(),
    /** "add" | "edit" | "delete" | "restore" | "send" — decides the card's wording and button colour. */
    val action: String = "add",
    /** Edits only: the same keys as [fields], as they are now, so the card can show old → new. */
    val before: Map<String, String> = emptyMap()
)

@Serializable
data class AiTask(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("task_text") val taskText: String = "",
    val category: String = "general",
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    val status: String = "active",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AiAnalysis(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("analysis_type") val analysisType: String = "",
    val content: String = "",
    @SerialName("updated_at") val updatedAt: String? = null
)

// ---------------- Nutrition / Calorie / Exercise feature ----------------

@Serializable
data class FoodLog(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("log_date") val logDate: String = "",
    @SerialName("meal_type") val mealType: String = "breakfast", // breakfast|lunch|dinner|snack|other
    @SerialName("item_name") val itemName: String = "",
    val calories: Int? = null,
    /** care-media object path of the photo the parent took; private, shown via a signed URL. */
    @SerialName("photo_path") val photoPath: String? = null,
    /** How much of the serving the child actually ate, 0-100. Null = the parent was not asked. */
    @SerialName("eaten_pct") val eatenPct: Int? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ExerciseLog(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("log_date") val logDate: String = "",
    @SerialName("activity_name") val activityName: String = "",
    @SerialName("duration_min") val durationMin: Int? = null,
    @SerialName("calories_burned") val caloriesBurned: Int? = null,
    val completed: Boolean = true,
    val mood: String? = null,   // tired | happy | energized — how the child felt after
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** Wearable / IoT metric row synced into parent analysis + device-warning notifications. */
@Serializable
data class DeviceReading(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("user_id") val userId: String = "",
    val source: String = "health_connect",
    val metric: String = "",
    val value: Double = 0.0,
    val unit: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("is_warning") val isWarning: Boolean = false,
    @SerialName("warning_reason") val warningReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** Which health-app / wearable / IoT platform a parent has linked for a child. */
@Serializable
data class WearableConnection(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("child_id") val childId: String? = null,
    val platform: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val status: String = "connected",
    @SerialName("last_synced_at") val lastSyncedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** AI-generated content (not persisted) — recipe & exercise instructions, suggestion lists. */
data class RecipeContent(
    val title: String,
    val timeMinutes: String,
    val ageRange: String,
    val servings: String,
    val nutritionNote: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val caloriesKcal: Int? = null,
    val proteinG: Double? = null,
    val carbG: Double? = null,
    val fatG: Double? = null,
    val calciumMg: Int? = null,
    val ironMg: Double? = null,
    val benefits: List<String> = emptyList(),
    val videoUrl: String? = null,
    val videoTitle: String? = null
)

data class ExerciseContent(
    val title: String,
    val prepTime: String,
    val duration: String,
    val ageRange: String,
    val difficulty: String,
    val benefit: String,
    val equipment: List<String>,
    val steps: List<String>
)

/** A lightweight suggestion item shown in lists (recommended foods / activities). */
data class SuggestionItem(
    val title: String,
    val subtitle: String,
    val tags: List<String> = emptyList()
)

// ---------------- Oral Health Pro System Models ----------------

@Serializable
data class ToothRecord(
    @SerialName("child_id") val childId: String = "",
    @SerialName("tooth_id") val toothId: String = "",
    val status: String = "healthy", // healthy | emerging | needs_care | missing
    val notes: String? = null,
    @SerialName("erupted_at") val eruptedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class OralHealthEvent(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("user_id") val userId: String = "",
    val category: String = "first_tooth", // first_tooth | dentist_visit | treatment | other
    @SerialName("event_name") val eventName: String = "",
    @SerialName("event_date") val eventDate: String = "",
    val notes: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class DailyOralRoutine(
    @SerialName("child_id") val childId: String = "",
    @SerialName("routine_date") val routineDate: String = "",
    val morning: Boolean = false,
    val lunch: Boolean = false,
    val bedtime: Boolean = false,
    val flossing: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null
)

// ---------------- Smart Refrigerator & Ingredient Scanner ----------------

@Serializable
data class FridgeIngredient(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("user_id") val userId: String = "",
    /** The household this belongs to (the child's owner). Set server-side; the whole family shares it. */
    @SerialName("owner_user_id") val ownerUserId: String = "",
    @SerialName("ingredient_name") val ingredientName: String = "",
    val category: String = "general", // vegetable | fruit | meat | dairy | grain | general
    val quantity: String? = null,
    @SerialName("bought_on") val boughtOn: String? = null,
    @SerialName("expires_on") val expiresOn: String? = null,
    val storage: String? = null,                                  // fridge | freezer | pantry
    @SerialName("storage_note") val storageNote: String? = null,  // "แช่เย็น 2-4°C"
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * One new food being watched for a reaction, for ONE child (กฎการทดสอบ 3-4 วัน). Kept per child
 * because a sibling's result says nothing about this one.
 */
@Serializable
data class FoodTrial(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("food_name") val foodName: String = "",
    @SerialName("started_on") val startedOn: String = "",
    val result: String = "testing",   // testing | safe | allergic
    val reaction: String? = null,
    @SerialName("decided_on") val decidedOn: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)


