package com.lullabyai.app.util

/** Parent booking visit types — encoded in [com.lullabyai.app.data.model.Appointment.reason]. */
enum class AppointmentBookingType(val key: String) {
    NORMAL("normal"),
    HEALTH_CHECK("health_check"),
    FOLLOW_UP("follow_up"),
    VACCINE("vaccine"),
    EMERGENCY("emergency");

    fun labelThai(): String = when (this) {
        NORMAL -> "ตรวจทั่วไป"
        HEALTH_CHECK -> "ตรวจสุขภาพ"
        FOLLOW_UP -> "ติดตามผล"
        VACCINE -> "วัคซีน"
        EMERGENCY -> "ฉุกเฉิน"
    }

    fun labelEn(): String = when (this) {
        NORMAL -> "General visit"
        HEALTH_CHECK -> "Health check"
        FOLLOW_UP -> "Follow-up"
        VACCINE -> "Vaccination"
        EMERGENCY -> "Emergency"
    }

    fun label(isThai: Boolean): String = if (isThai) labelThai() else labelEn()
}

data class BookedVaccineMeta(
    val vaccineName: String? = null,
    val vaccineRecordId: String? = null,
    val isFromBook: Boolean = false
)

object AppointmentBookingMeta {
    private const val TYPE_PREFIX = "[type:"
    private const val VAC_ID_PREFIX = "[vac_id:"
    private const val VAC_NAME_PREFIX = "[vac_name:"
    private const val EMERGENCY_NOTE = "booking_meta:is_emergency=true"

    fun encodeReason(
        type: AppointmentBookingType,
        detail: String?,
        vaccineName: String? = null,
        vaccineRecordId: String? = null
    ): String {
        val tags = StringBuilder("$TYPE_PREFIX${type.key}]")
        val cleanVacId = vaccineRecordId?.trim().takeUnless { it.isNullOrBlank() }
        val cleanVacName = vaccineName?.trim().takeUnless { it.isNullOrBlank() }

        if (cleanVacId != null) {
            tags.append("$VAC_ID_PREFIX$cleanVacId]")
        }
        if (cleanVacName != null) {
            tags.append("$VAC_NAME_PREFIX$cleanVacName]")
        }

        val prefix = tags.toString()
        val defaultHeader = if (type == AppointmentBookingType.VACCINE && cleanVacName != null) {
            "วัคซีน: $cleanVacName"
        } else {
            type.labelThai()
        }

        val body = detail?.trim().orEmpty()
        return when {
            body.isBlank() -> "$prefix $defaultHeader"
            body.startsWith(prefix) -> body
            else -> "$prefix $defaultHeader${if (body.isNotBlank() && body != defaultHeader) ": $body" else ""}"
        }
    }

    fun parseVaccine(reason: String?, notes: String?): BookedVaccineMeta {
        val r = reason.orEmpty()
        val n = notes.orEmpty()
        val combined = "$r\n$n"

        val vacId = if (combined.contains(VAC_ID_PREFIX)) {
            combined.substringAfter(VAC_ID_PREFIX).substringBefore("]").trim()
        } else null

        val vacName = if (combined.contains(VAC_NAME_PREFIX)) {
            combined.substringAfter(VAC_NAME_PREFIX).substringBefore("]").trim()
        } else null

        if (!vacName.isNullOrBlank() || !vacId.isNullOrBlank()) {
            return BookedVaccineMeta(
                vaccineName = vacName,
                vaccineRecordId = vacId,
                isFromBook = !vacId.isNullOrBlank()
            )
        }

        val displayed = displayReason(reason, isThai = true)
        val extractedName = if (displayed.startsWith("วัคซีน:", ignoreCase = true) || displayed.startsWith("Vaccine:", ignoreCase = true)) {
            displayed.substringAfter(":").substringBefore(":").trim().substringBefore("(").trim()
        } else null

        return BookedVaccineMeta(
            vaccineName = extractedName?.takeIf { it.isNotBlank() },
            vaccineRecordId = null,
            isFromBook = false
        )
    }

    fun parseType(reason: String?, notes: String?): AppointmentBookingType {
        if (notes?.contains(EMERGENCY_NOTE) == true) return AppointmentBookingType.EMERGENCY
        val r = reason?.trim().orEmpty()
        if (r.startsWith(TYPE_PREFIX)) {
            val key = r.removePrefix(TYPE_PREFIX).substringBefore("]").trim()
            return AppointmentBookingType.entries.firstOrNull { it.key == key } ?: AppointmentBookingType.NORMAL
        }
        return when {
            r.contains("วัคซีน", ignoreCase = true) || r.contains("vaccine", ignoreCase = true) ->
                AppointmentBookingType.VACCINE
            r.contains("ติดตาม", ignoreCase = true) || r.contains("follow", ignoreCase = true) ->
                AppointmentBookingType.FOLLOW_UP
            r.contains("สุขภาพ", ignoreCase = true) || r.contains("health check", ignoreCase = true) ->
                AppointmentBookingType.HEALTH_CHECK
            r.contains("ฉุกเฉิน", ignoreCase = true) || r.contains("emergency", ignoreCase = true) ->
                AppointmentBookingType.EMERGENCY
            else -> AppointmentBookingType.NORMAL
        }
    }

    fun isEmergency(notes: String?): Boolean = notes?.contains(EMERGENCY_NOTE) == true

    fun emergencyNotes(existing: String?): String {
        val base = existing?.trim().orEmpty()
        return if (base.contains(EMERGENCY_NOTE)) base
        else if (base.isBlank()) EMERGENCY_NOTE
        else "$base\n$EMERGENCY_NOTE"
    }

    /** Human-readable reason without the bracket tags. */
    fun displayReason(reason: String?, isThai: Boolean): String {
        val type = parseType(reason, null)
        var raw = reason?.trim().orEmpty()
        while (raw.startsWith("[")) {
            val closeIdx = raw.indexOf("]")
            if (closeIdx != -1) {
                raw = raw.substring(closeIdx + 1).trim()
            } else {
                break
            }
        }
        if (raw.isNotBlank()) {
            return raw
        }
        return type.label(isThai)
    }
}
