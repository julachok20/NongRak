package com.lullabyai.app.util

/** Classifies parent/doctor booking reason text for visit wrap-up UI (vaccine vs general). */
enum class VisitReasonKind { VACCINE, GENERAL }

object VisitReasonTemplates {
    private val vaccineTokens = listOf(
        "vaccine", "vaccination", "immunization", "วัคซีน", "ฉีดวัคซีน",
        "bmr", "mmr", "bcg", "dtap", "ipv", "hep", "hib", "rv", "pcv", "flu"
    )

    fun classify(reason: String?): VisitReasonKind {
        val r = reason?.trim()?.lowercase().orEmpty()
        if (r.isEmpty()) return VisitReasonKind.GENERAL
        return if (vaccineTokens.any { r.contains(it) }) VisitReasonKind.VACCINE else VisitReasonKind.GENERAL
    }

    /** Stored on [com.lullabyai.app.data.model.DoctorVisit.doctorAdvice] for parent-facing summary cards. */
    fun buildStructuredAdvice(
        kind: VisitReasonKind,
        vaccineName: String?,
        lotNumber: String?,
        injectionSite: String?,
        reactionNotes: String?,
        generalAdvice: String?
    ): String? {
        if (kind != VisitReasonKind.VACCINE) return generalAdvice?.trim()?.takeIf { it.isNotBlank() }
        val lines = buildList {
            add("[visit_kind=vaccine]")
            vaccineName?.trim()?.takeIf { it.isNotBlank() }?.let { add("vaccine_name=$it") }
            lotNumber?.trim()?.takeIf { it.isNotBlank() }?.let { add("lot=$it") }
            injectionSite?.trim()?.takeIf { it.isNotBlank() }?.let { add("site=$it") }
            reactionNotes?.trim()?.takeIf { it.isNotBlank() }?.let { add("reaction=$it") }
            generalAdvice?.trim()?.takeIf { it.isNotBlank() }?.let { add("advice=$it") }
        }
        return lines.takeIf { it.size > 1 }?.joinToString("\n")
    }

    data class ParsedVaccineAdvice(
        val vaccineName: String?,
        val lot: String?,
        val site: String?,
        val reaction: String?,
        val advice: String?
    )

    fun parseVaccineAdvice(doctorAdvice: String?): ParsedVaccineAdvice? {
        val raw = doctorAdvice?.trim().orEmpty()
        if (!raw.startsWith("[visit_kind=vaccine]")) return null
        var name: String? = null
        var lot: String? = null
        var site: String? = null
        var reaction: String? = null
        var advice: String? = null
        raw.lineSequence().drop(1).forEach { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@forEach
            val key = line.substring(0, idx)
            val value = line.substring(idx + 1)
            when (key) {
                "vaccine_name" -> name = value
                "lot" -> lot = value
                "site" -> site = value
                "reaction" -> reaction = value
                "advice" -> advice = value
            }
        }
        return ParsedVaccineAdvice(name, lot, site, reaction, advice)
    }
}
