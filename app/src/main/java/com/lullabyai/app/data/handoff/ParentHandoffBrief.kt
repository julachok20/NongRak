package com.lullabyai.app.data.handoff

import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.model.ChronicCondition
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.MilkLog
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.model.WasteRecord
import com.lullabyai.app.data.routine.CareRoutineDuty
import com.lullabyai.app.util.TimeUtils
import java.time.LocalDate
import java.time.Period

/**
 * Labels and medical items for the parent "ส่งต่อการดูแล" briefing.
 *
 * Pure: the screen supplies already-loaded rows. Nothing here invents a drug, a dose, or a
 * diagnosis — a Stitch frame that shows Tempora millilitres is a placeholder, not a protocol.
 */
object ParentHandoffBrief {

    data class Vitals(
        val wake: String? = null,
        val mood: String? = null,
        val temperature: String? = null
    ) {
        fun line(isThai: Boolean): String = listOfNotNull(
            wake?.let { (if (isThai) "ตื่นนอนเช้า " else "Woke ") + it },
            mood,
            temperature
        ).joinToString(if (isThai) " • " else " · ")
    }

    data class MedicalItem(
        val kind: Kind,
        val title: String,
        val body: String
    ) {
        enum class Kind { FEVER, DIARRHEA, CHRONIC }
    }

    data class CaregiverCard(
        val userId: String,
        val name: String,
        val roleLabel: String,
        val avatarLabel: String,
        val shift: String,
        val status: String,
        val onDuty: Boolean,
        val avatarUrl: String? = null
    )

    /** "7ด 1ส" — compact enough to sit on the avatar. */
    fun compactAge(birthdate: String?, isThai: Boolean, today: LocalDate = LocalDate.now()): String {
        val dob = runCatching { LocalDate.parse(birthdate) }.getOrNull() ?: return ""
        val p = Period.between(dob, today)
        val weeks = p.days / 7
        return if (isThai) {
            when {
                p.years > 0 && p.months > 0 -> "${p.years}ป ${p.months}ด"
                p.years > 0 -> "${p.years}ป"
                weeks > 0 -> "${p.months}ด ${weeks}ส"
                else -> "${p.months}ด"
            }
        } else {
            when {
                p.years > 0 && p.months > 0 -> "${p.years}y ${p.months}m"
                p.years > 0 -> "${p.years}y"
                weeks > 0 -> "${p.months}m ${weeks}w"
                else -> "${p.months}m"
            }
        }
    }

    fun healthChip(feverC: Double?, mood: String?, isThai: Boolean): String {
        if (feverC != null && feverC >= 37.8) return if (isThai) "มีไข้" else "Fever"
        val moodBit = moodLabel(mood, isThai)
        val well = if (isThai) "สุขภาพดี" else "Well"
        return if (moodBit.isNullOrBlank()) well else "$well $moodBit"
    }

    fun moodLabel(mood: String?, isThai: Boolean): String? = when (mood?.lowercase()) {
        "happy" -> if (isThai) "ร่าเริง" else "cheerful"
        "calm" -> if (isThai) "อารมณ์ดี" else "calm"
        "fussy" -> if (isThai) "งอแง" else "fussy"
        else -> mood?.takeIf { it.isNotBlank() }
    }

    fun vitals(
        wakeIso: String?,
        mood: String?,
        temperatureC: Double?,
        isThai: Boolean
    ): Vitals {
        val wake = TimeUtils.localTime(wakeIso)?.let { it + if (isThai) " น." else "" }
        val moodBit = moodLabel(mood, isThai)?.let { if (isThai && it != "ร่าเริง" && it != "งอแง") it else if (!isThai) it else it }
        val temp = temperatureC?.let { (if (isThai) "อุณหภูมิ " else "") + String.format("%.1f°C", it) }
        return Vitals(wake, moodBit, temp)
    }

    /**
     * Wake = end of the latest finished main sleep that ended today (or is still open from last
     * night). An unfinished session is "still asleep", so it does not count as a wake time.
     */
    fun wakeIso(logs: List<SleepLog>, today: LocalDate): String? =
        logs
            .filter { it.sleepType == "main" && !it.endTime.isNullOrBlank() }
            .filter { TimeUtils.localDate(it.endTime) == today.toString() }
            .maxByOrNull { it.endTime.orEmpty() }
            ?.endTime

    fun latestMood(logs: List<SleepLog>): String? =
        logs.firstOrNull { !it.mood.isNullOrBlank() }?.mood

    fun allergyLine(
        childAllergies: String?,
        noKnown: Boolean,
        rows: List<Allergy>,
        isThai: Boolean
    ): String? {
        val names = (rows.map { it.name.trim() }.filter { it.isNotBlank() } +
            childAllergies.orEmpty().split(",", "،", "และ", "&")
                .map { it.trim() }.filter { it.isNotBlank() && it != "ไม่มี" && !it.equals("none", true) })
            .distinct()
        if (names.isEmpty() || noKnown) return null
        val joined = names.joinToString(" & ")
        return if (isThai) "เตือนภูมิแพ้สำคัญ: $joined (งดเด็ดขาด)"
        else "Allergy alert: $joined (strict avoid)"
    }

    /**
     * Only what the child's own records support. Fever tells the caregiver to call the parent —
     * it does not invent a medicine. Diarrhoea only appears when a current illness row names it.
     */
    fun medicalItems(
        childName: String,
        feverC: Double?,
        records: List<HealthRecord>,
        chronic: List<ChronicCondition>,
        isThai: Boolean
    ): List<MedicalItem> {
        val out = mutableListOf<MedicalItem>()
        if (feverC != null && feverC >= 37.8) {
            val t = String.format("%.1f°C", feverC)
            out += MedicalItem(
                MedicalItem.Kind.FEVER,
                if (isThai) "กรณีมีไข้สูงเกิน $t" else "Fever at $t",
                if (isThai)
                    "แจ้งผู้ปกครองทันที เช็ดตัวด้วยผ้าน้ำอุ่นอุณหภูมิห้อง และอย่าให้ยาเองจนกว่าผู้ปกครองหรือแพทย์จะสั่ง"
                else
                    "Call the parent now. Sponge with room-temperature water. Do not give medicine unless the parent or a doctor has already prescribed it."
            )
        }
        val diarrhea = records.any { r ->
            val blob = (r.title + " " + r.description.orEmpty() + " " + (r.additionalSymptoms ?: emptyList()).joinToString())
                .lowercase()
            listOf("ท้องเสีย", "diarrhea", "ขับถ่ายเหลว", "อุจจาระเหลว").any { it in blob }
        }
        if (diarrhea) {
            out += MedicalItem(
                MedicalItem.Kind.DIARRHEA,
                if (isThai) "สังเกตการขับถ่าย" else "Watch stools",
                if (isThai)
                    "สังเกตลักษณะอุจจาระ หากมีมูกเลือด หรือถ่ายเหลวมากกว่า 2 ครั้ง ให้แจ้งผู้ปกครองทันที"
                else
                    "Watch the stools. Call the parent at once if there is mucus or blood, or more than two loose stools."
            )
        }
        chronic.filter { it.name.isNotBlank() }.forEach { c ->
            out += MedicalItem(
                MedicalItem.Kind.CHRONIC,
                c.name,
                c.prevention?.takeIf { it.isNotBlank() }
                    ?: c.notes?.takeIf { it.isNotBlank() }
                    ?: if (isThai) "โรคประจำตัวของ$childName — ปฏิบัติตามที่ผู้ปกครองแจ้งไว้" else "Known condition — follow the parent's instructions."
            )
        }
        return out
    }

    /**
     * People the parent may assign today's handover to: ticked caretakers (`is_caregiver`)
     * other than the parent looking at the screen.
     */
    fun caregiverCards(
        people: List<CareRoutineDuty.Person>,
        caregiverIds: Set<String>,
        excludeUserId: String = "",
        onDuty: CareRoutineDuty.OnDuty?,
        isThai: Boolean,
        roleTitle: (String, Boolean) -> String
    ): List<CaregiverCard> {
        val visible = people.filter {
            it.userId.isNotBlank() &&
                it.userId != excludeUserId &&
                it.userId in caregiverIds
        }
        val ordered = buildList {
            onDuty?.person?.takeIf { p -> visible.any { it.userId == p.userId } }?.let { add(it) }
            visible.filter { it.userId != onDuty?.person?.userId }.forEach { add(it) }
        }
        return ordered.distinctBy { it.userId }.map { person ->
            val duty = onDuty?.takeIf { it.person.userId == person.userId }
            CaregiverCard(
                userId = person.userId,
                name = person.name,
                roleLabel = roleTitle(person.role, isThai),
                avatarLabel = avatarLabel(person.name, person.role),
                shift = shiftLabel(duty?.shiftStart, duty?.shiftEnd, isThai),
                status = when {
                    duty?.isOnDutyNow == true -> if (isThai) "กำลังดูแล" else "On duty"
                    else -> if (isThai) "พร้อมช่วยเสริม" else "On standby"
                },
                onDuty = duty?.isOnDutyNow == true,
                avatarUrl = person.avatarUrl
            )
        }
    }

    fun avatarLabel(name: String, role: String): String = when (role) {
        "nanny" -> {
            val given = name.removePrefix("พี่เลี้ยง").removePrefix("พี่").trim()
            "พี่" + given.take(2).ifBlank { name.take(2) }
        }
        "grandparent" -> name.take(2).ifBlank { "ยาย" }
        else -> name.take(2)
    }

    fun shiftLabel(startMinute: Int?, endMinute: Int?, isThai: Boolean): String {
        if (startMinute == null && endMinute == null) return ""
        fun clock(m: Int) = "%02d:%02d".format(m / 60, m % 60)
        val range = listOfNotNull(startMinute?.let(::clock), endMinute?.let(::clock)).joinToString(" - ")
        return if (isThai) "กะวันนี้ $range" else "Shift $range"
    }

    fun shortCategory(key: String, isThai: Boolean): String = when (key) {
        "feeding" -> if (isThai) "การกิน" else "Milk"
        "solid" -> if (isThai) "อาหารเสริม" else "Solids"
        "activity" -> if (isThai) "กิจกรรม" else "Activity"
        "sleep" -> if (isThai) "การนอน" else "Sleep"
        "hygiene" -> if (isThai) "สุขอนามัย" else "Hygiene"
        "health" -> if (isThai) "สุขภาพ" else "Health"
        "night" -> if (isThai) "กลางคืน" else "Night"
        else -> if (isThai) "อื่นๆ" else "Other"
    }

    /** "บันทึกล่าสุด: วันนี้ 09:12 น." */
    fun lastRecordLabel(iso: String?, today: LocalDate, isThai: Boolean): String? {
        val date = TimeUtils.localDate(iso) ?: return null
        val time = TimeUtils.localTime(iso) ?: return if (isThai) "บันทึกล่าสุด: $date" else "Last record: $date"
        val whenBit = if (date == today.toString()) {
            if (isThai) "วันนี้ $time น." else "today $time"
        } else {
            if (isThai) "$date $time น." else "$date $time"
        }
        return if (isThai) "บันทึกล่าสุด: $whenBit" else "Last record: $whenBit"
    }

    fun parentCallLabel(fullName: String?, relationship: String?, phone: String?, isThai: Boolean): Pair<String, String>? {
        if (fullName.isNullOrBlank() && phone.isNullOrBlank()) return null
        val who = when (relationship) {
            "mother" -> if (isThai) "คุณแม่" else "Mom"
            "father" -> if (isThai) "คุณพ่อ" else "Dad"
            else -> if (isThai) "ผู้ปกครอง" else "Parent"
        }
        val title = if (!fullName.isNullOrBlank()) {
            if (isThai) "$who ($fullName)" else "$who ($fullName)"
        } else who
        val sub = phone?.takeIf { it.isNotBlank() }?.let {
            if (isThai) "โทรด่วน ($it)" else "Call $it"
        } ?: if (isThai) "ยังไม่มีเบอร์" else "No phone on file"
        return title to sub
    }

    fun doctorCallLabel(doctorName: String?, isThai: Boolean): Pair<String, String>? {
        val name = doctorName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val title = if (isThai) "แชท นพ. $name" else "Message Dr $name"
        val sub = if (isThai) "ออนไลน์" else "Online"
        return title to sub
    }

    /** "7 เดือน 1 สัปดาห์" — spoken form for the caregiver identity card. */
    fun spokenAge(birthdate: String?, isThai: Boolean, today: LocalDate = LocalDate.now()): String {
        val dob = runCatching { LocalDate.parse(birthdate) }.getOrNull() ?: return ""
        val p = Period.between(dob, today)
        val weeks = p.days / 7
        return if (isThai) {
            when {
                p.years > 0 && p.months > 0 -> "${p.years} ปี ${p.months} เดือน"
                p.years > 0 -> "${p.years} ปี"
                weeks > 0 -> "${p.months} เดือน $weeks สัปดาห์"
                else -> "${p.months} เดือน"
            }
        } else {
            when {
                p.years > 0 && p.months > 0 -> "${p.years}y ${p.months}mo"
                p.years > 0 -> "${p.years}y"
                weeks > 0 -> "${p.months} mo $weeks wk"
                else -> "${p.months} mo"
            }
        }
    }

    /**
     * Standing caution on the caregiver card. Names recorded allergens only; fever copy tells
     * them to call the parent — it does not invent a medicine or a dose.
     */
    fun caregiverCaution(
        childAllergies: String?,
        noKnown: Boolean,
        rows: List<Allergy>,
        isThai: Boolean
    ): String {
        val names = (rows.map { it.name.trim() }.filter { it.isNotBlank() } +
            childAllergies.orEmpty().split(",", "،", "และ", "&")
                .map { it.trim() }.filter { it.isNotBlank() && it != "ไม่มี" && !it.equals("none", true) })
            .distinct()
        val severe = rows.any { it.severity.equals("severe", true) }
        val fever = if (isThai)
            "หากมีไข้เกิน 37.8°C แจ้งผู้ปกครองทันที และอย่าให้ยาเองจนกว่าผู้ปกครองหรือแพทย์จะสั่ง"
        else
            "If the temperature is over 37.8°C, call the parent. Do not give medicine unless already prescribed."
        if (names.isEmpty()) return fever
        val joined = names.joinToString(if (isThai) "และ" else " & ")
        val sev = if (severe) {
            if (isThai) " (แพ้รุนแรง)" else " (severe)"
        } else ""
        return if (isThai)
            "ข้อควรระวังสำคัญ: ห้าม$joined โดยเด็ดขาด$sev $fever"
        else
            "Important: strictly avoid $joined$sev. $fever"
    }

    fun todayMilkMl(logs: List<MilkLog>, today: String): Int =
        logs.filter { !it.isWater && TimeUtils.localDate(it.loggedAt) == today }.sumOf { it.volumeMl }

    /** Urine count, then stool count, for records on [today]. */
    fun todayWasteSplit(records: List<WasteRecord>, today: String): Pair<Int, Int> {
        val rows = records.filter { TimeUtils.localDate(it.recordedAt) == today }
        return rows.count { it.wasteType == "urine" } to rows.count { it.wasteType == "stool" }
    }
}
