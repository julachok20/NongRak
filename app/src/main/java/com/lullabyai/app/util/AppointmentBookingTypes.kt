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

object AppointmentBookingMeta {
    private const val TYPE_PREFIX = "[type:"
    private const val EMERGENCY_NOTE = "booking_meta:is_emergency=true"

    fun encodeReason(type: AppointmentBookingType, detail: String?): String {
        val body = detail?.trim().orEmpty()
        val prefix = "$TYPE_PREFIX${type.key}]"
        return when {
            body.isBlank() -> type.labelThai()
            body.startsWith(prefix) -> body
            else -> "$prefix ${type.labelThai()}${if (body.isNotBlank()) ": $body" else ""}"
        }
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

    /** Human-readable reason without the type prefix. */
    fun displayReason(reason: String?, isThai: Boolean): String {
        val type = parseType(reason, null)
        val raw = reason?.trim().orEmpty()
        if (raw.startsWith(TYPE_PREFIX)) {
            val after = raw.substringAfter("]").trim()
            if (after.isNotBlank()) return after.removePrefix(type.labelThai()).trim(' ', ':')
        }
        return raw.ifBlank { type.label(isThai) }
    }
}
