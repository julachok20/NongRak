package com.lullabyai.app.util

import android.graphics.Bitmap
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Everything printed on the two sides of the child's digital ID card. */
data class ChildIdCardData(
    val cardNumber: String,
    val fullName: String,
    val birthdate: String,
    val age: String,
    val gender: String,
    val bloodGroup: String,
    val nationality: String,
    val guardian: String,
    val photo: String?,
    val conditions: String,
    val drugAllergies: String,
    val foodAllergies: String,
    val emergencyContact: String,
    val emergencyRelation: String?,
    val hospital: String,
    val address: String,
    val qr: Bitmap?,
)

/**
 * Builds the card the app shows, from the same rows the website reads, so a card seen in the app
 * and one opened from LINE cannot disagree.
 *
 * Missing details read "ไม่ระบุ", never blank: on a card held out to a nurse, an empty allergy line
 * reads as "no allergies", which is not what missing data means.
 */
object ChildIdCardBuilder {

    private const val UNKNOWN_TH = "ไม่ระบุ"
    private const val NONE_TH = "ไม่มี"
    private const val CARD_PAGE = "https://nongrak-web.vercel.app/card/"
    private val DRUG_TYPES = setOf("drug", "medicine", "medication", "ยา")
    private val FOOD_TYPES = setOf("food", "อาหาร")
    private val TH_MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")

    suspend fun build(child: Child, isThai: Boolean): ChildIdCardData {
        val profile = runCatching { com.lullabyai.app.data.repository.AuthRepository().getProfile().getOrNull() }.getOrNull()

        val allergyRows = runCatching {
            supabase.from("allergies").select { filter { eq("child_id", child.id) } }.decodeList<JsonObject>()
        }.getOrDefault(emptyList())
        fun namesOf(types: Set<String>, inSet: Boolean) = allergyRows.mapNotNull { row ->
            val type = row["type"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            val name = row["name"]?.jsonPrimitive?.contentOrNull?.trim()
            if (name.isNullOrBlank()) null else if ((type in types) == inSet) name else null
        }
        val drug = namesOf(DRUG_TYPES, inSet = true)
        // An allergy typed during onboarding has no type; the food line is the safer home for it.
        val other = namesOf(DRUG_TYPES + FOOD_TYPES, inSet = false) +
            child.allergies.orEmpty().split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

        val conditions = runCatching {
            supabase.from("chronic_conditions").select { filter { eq("child_id", child.id) } }.decodeList<JsonObject>()
                .mapNotNull { it["name"]?.jsonPrimitive?.contentOrNull?.trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

        val hospital = runCatching {
            supabase.from("doctor_visits").select {
                filter { eq("child_id", child.id) }
                order("visit_datetime", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(5)
            }.decodeList<JsonObject>()
                .firstNotNullOfOrNull { it["facility_name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { n -> n.isNotEmpty() } }
        }.getOrNull()

        val known = { list: List<String> ->
            if (list.isNotEmpty()) list.distinct().joinToString(", ")
            else if (child.noKnownAllergies) NONE_TH else UNKNOWN_TH
        }

        val qr = child.cardToken?.let { token ->
            runCatching { AccessQrHelper.encodeBitmap(CARD_PAGE + token, 320) }.getOrNull()
        }

        return ChildIdCardData(
            cardNumber = cardNumber(child.id),
            // The card is a document, so it prints the legal name. Falls back to the nickname
            // rather than "ไม่ระบุ": a card with no name at all is useless to whoever holds it.
            fullName = child.fullName?.trim().orEmpty().ifBlank { child.name }.ifBlank { UNKNOWN_TH },
            birthdate = thaiDate(child.birthdate),
            age = ageLabel(child.birthdate, isThai),
            gender = when (child.gender) {
                "male" -> if (isThai) "ชาย" else "Male"
                "female" -> if (isThai) "หญิง" else "Female"
                else -> UNKNOWN_TH
            },
            bloodGroup = child.bloodGroup?.trim().orEmpty().ifEmpty { UNKNOWN_TH },
            nationality = child.nationality?.trim().orEmpty().ifEmpty { "ไทย" },
            guardian = profile?.fullName?.trim().orEmpty().ifEmpty { UNKNOWN_TH },
            photo = runCatching { StorageHelper.signedAvatarUrl(child.avatarUrl) }.getOrNull(),
            conditions = conditions.joinToString(", ").ifEmpty { NONE_TH },
            drugAllergies = known(drug),
            foodAllergies = known(namesOf(FOOD_TYPES, inSet = true) + other),
            emergencyContact = profile?.phone?.trim().orEmpty().ifEmpty { UNKNOWN_TH },
            emergencyRelation = profile?.relationship?.trim()?.takeIf { it.isNotEmpty() },
            hospital = hospital.orEmpty().ifEmpty { UNKNOWN_TH },
            address = profile?.address?.trim().orEmpty().ifEmpty { UNKNOWN_TH },
            qr = qr,
        )
    }

    /** Derived from the child's id, so the number never needs storing and never drifts. */
    fun cardNumber(childId: String): String {
        val year = java.time.LocalDate.now().year
        val tail = childId.replace("-", "").takeLast(6).uppercase()
        return "NRK-$year-$tail"
    }

    /** Thai Buddhist-era date, as printed on Thai documents: "15 ม.ค. 2565". */
    fun thaiDate(iso: String): String = runCatching {
        val d = java.time.LocalDate.parse(iso)
        "${d.dayOfMonth} ${TH_MONTHS[d.monthValue - 1]} ${d.year + 543}"
    }.getOrDefault(UNKNOWN_TH)

    fun ageLabel(iso: String, isThai: Boolean): String = runCatching {
        val born = java.time.LocalDate.parse(iso)
        val period = java.time.Period.between(born, java.time.LocalDate.now())
        when {
            period.years > 0 -> if (isThai) "${period.years} ปี ${period.months} เดือน" else "${period.years}y ${period.months}m"
            else -> if (isThai) "${period.months} เดือน" else "${period.months}m"
        }
    }.getOrDefault(UNKNOWN_TH)
}
