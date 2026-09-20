package com.lullabyai.app.data.infoask

import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChildActivity
import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.data.model.MilkLog
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.model.SleepSchedule
import com.lullabyai.app.util.TimeUtils
import java.time.LocalDate

enum class InfoAskSubject { CHILD, USER }

enum class InfoAskKind { CHIPS, TEXT, DATE, NUMBER }

data class InfoAskChip(
    val id: String,
    val labelTh: String,
    val labelEn: String
) {
    fun label(isThai: Boolean) = if (isThai) labelTh else labelEn
}

data class InfoAskPrompt(
    val fieldId: String,
    val subject: InfoAskSubject,
    val kind: InfoAskKind,
    val questionTh: String,
    val questionEn: String,
    val chips: List<InfoAskChip> = emptyList(),
    val healthBook: Boolean = false,
    val hintTh: String? = null,
    val hintEn: String? = null
) {
    fun question(isThai: Boolean) = if (isThai) questionTh else questionEn
    fun hint(isThai: Boolean) = if (isThai) hintTh else hintEn
    fun subjectName(childName: String, isThai: Boolean): String = when (subject) {
        InfoAskSubject.CHILD -> childName
        InfoAskSubject.USER -> if (isThai) "คุณ" else "User"
    }
}

data class InfoAskSnapshot(
    val child: Child,
    val profile: Profile? = null,
    val authEmail: String? = null,
    val sleepLogs: List<SleepLog> = emptyList(),
    val sleepSchedules: List<SleepSchedule> = emptyList(),
    val milkLogs: List<MilkLog> = emptyList(),
    val activities: List<ChildActivity> = emptyList(),
    val visits: List<DoctorVisit> = emptyList(),
    val shownToday: Set<String> = emptySet(),
    val answeredEver: Set<String> = emptySet(),
    val today: LocalDate = LocalDate.now()
)

data class InfoAskAnswer(
    val fieldId: String,
    val text: String = "",
    val chipId: String? = null
)

/**
 * All currently open gaps, mixed across topics so the form is not a birth-only checklist.
 * Skip rules live here so Home and tests agree.
 */
object InfoAskCatalog {

    val PERSIST_ANSWERED = setOf(
        "activity_level",
        "favorite_toy", "favorite_food", "favorite_sound", "favorite_activity", "favorite_soothing"
    )

    /** Topic buckets — round-robin so sleep / feeding / Health Book appear together, not in blocks. */
    private val GROUPS = listOf(
        listOf("birthdate", "delivery_type", "place_of_birth", "gestational_weeks", "birth_weight_g", "birth_length_cm", "birth_head_cm", "blood_group", "allergies"),
        listOf("guardian_name", "guardian_relation", "guardian_phone", "guardian_address"),
        listOf("sleep_last_night", "typical_bedtime", "nap_today", "mood_after_sleep"),
        listOf("activity_level", "play_today"),
        listOf("feeding_type", "recent_facility"),
        listOf("favorite_toy", "favorite_food", "favorite_sound", "favorite_activity", "favorite_soothing")
    )

    /**
     * The questions whose answer is decided by the child's own row plus this device's history.
     *
     * The rest of the catalog needs a week of sleep, milk, activity and visit rows — one round trip
     * per child, which is too much to spend on a badge in the child switcher. Counting only these
     * means the badge can under-report (a child may also owe sleep answers), never over-report.
     */
    val CHILD_ROW_FIELDS = listOf(
        "birthdate", "delivery_type", "place_of_birth", "gestational_weeks", "birth_weight_g",
        "birth_length_cm", "birth_head_cm", "blood_group", "allergies", "activity_level"
    )

    /**
     * How many of [CHILD_ROW_FIELDS] this child still owes an answer to.
     *
     * Deliberately blind to "already shown today": skipping the form for today does not answer the
     * question, and the badge is there to say the gap is still open.
     */
    fun openChildRowCount(
        child: Child,
        answeredEver: Set<String> = emptySet(),
        today: LocalDate = LocalDate.now()
    ): Int {
        val snapshot = InfoAskSnapshot(child = child, answeredEver = answeredEver, today = today)
        return CHILD_ROW_FIELDS.count { isOpen(it, snapshot) }
    }

    fun next(snapshot: InfoAskSnapshot): InfoAskPrompt? = openAll(snapshot).firstOrNull()

    /**
     * Whether the open questions have to be built again.
     *
     * Questions belong to one child, but the answers are written to whichever child is selected
     * when the parent taps save. Keeping a list that was built for another child is how one child's
     * birth weight ends up on their sibling, so a change of child always reloads.
     */
    fun needsReload(selectedChildId: String?, loadedForChildId: String?, loaded: Int): Boolean =
        selectedChildId != null && (loaded == 0 || loadedForChildId != selectedChildId)

    fun isFilled(prompt: InfoAskPrompt, answer: InfoAskAnswer): Boolean = when (prompt.kind) {
        InfoAskKind.CHIPS -> !answer.chipId.isNullOrBlank()
        InfoAskKind.DATE, InfoAskKind.TEXT, InfoAskKind.NUMBER -> answer.text.isNotBlank()
    }

    fun bannerSubjectName(prompts: List<InfoAskPrompt>, childName: String, isThai: Boolean): String {
        val hasChild = prompts.any { it.subject == InfoAskSubject.CHILD }
        val hasUser = prompts.any { it.subject == InfoAskSubject.USER }
        val you = if (isThai) "คุณ" else "you"
        val name = childName.ifBlank { if (isThai) "ลูก" else "your child" }
        return when {
            hasChild && hasUser -> if (isThai) "$name และคุณ" else "$name and you"
            hasUser -> you
            else -> name
        }
    }

    /**
     * Every unanswered field, interleaved by topic and shuffled with [seed] (usually today's
     * epoch day) so two days in a row do not open on the same first question.
     */
    fun openAll(snapshot: InfoAskSnapshot, seed: Long = snapshot.today.toEpochDay()): List<InfoAskPrompt> {
        val childName = snapshot.child.name.ifBlank { "ลูก" }
        val open = ALL.filter { it.fieldId != "open_notes" && isOpen(it.fieldId, snapshot) }
        val byId = open.associateBy { it.fieldId }
        val rng = kotlin.random.Random(seed)
        val queues = GROUPS
            .map { ids -> ids.mapNotNull { byId[it] }.shuffled(rng).toMutableList() }
            .filter { it.isNotEmpty() }
            .shuffled(rng)
            .map { it.toMutableList() }
            .toMutableList()
        val out = mutableListOf<InfoAskPrompt>()
        while (queues.any { it.isNotEmpty() }) {
            val iter = queues.iterator()
            while (iter.hasNext()) {
                val q = iter.next()
                if (q.isEmpty()) iter.remove()
                else out += q.removeAt(0)
            }
        }
        val used = out.map { it.fieldId }.toSet()
        out += open.filter { it.fieldId !in used }
        if (out.isEmpty()) {
            ALL.find { it.fieldId == "open_notes" }?.let { out += it }
        }
        return out.map { fillName(it, childName) }
    }

    fun isOpen(fieldId: String, snapshot: InfoAskSnapshot): Boolean {
        if (fieldId in snapshot.shownToday) return false
        if (fieldId in PERSIST_ANSWERED && fieldId in snapshot.answeredEver) return false
        val c = snapshot.child
        val p = snapshot.profile
        val today = snapshot.today
        return when (fieldId) {
            "birthdate" -> c.birthdate.isBlank()
            "delivery_type" -> c.deliveryType.isNullOrBlank()
            "place_of_birth" -> c.placeOfBirth.isNullOrBlank()
            "gestational_weeks" -> c.gestationalWeeks == null
            "birth_weight_g" -> c.birthWeightG == null
            "birth_length_cm" -> c.birthLengthCm == null
            "birth_head_cm" -> c.birthHeadCm == null
            "blood_group" -> c.bloodGroup.isNullOrBlank()
            "allergies" -> c.allergies.isNullOrBlank() && !c.noKnownAllergies
            "guardian_name" -> p?.fullName.isNullOrBlank()
            "guardian_relation" -> p?.relationship.isNullOrBlank()
            "guardian_phone" -> p?.phone.isNullOrBlank()
            "guardian_address" -> p?.address.isNullOrBlank()
            "sleep_last_night" -> !hasSleepSince(snapshot, days = 1, type = "main")
            "typical_bedtime" -> snapshot.sleepSchedules.isEmpty()
            "nap_today" -> !hasSleepOn(snapshot, today, type = "nap")
            "activity_level" -> true
            // Never "complete" — there is always another thing a child likes. The
            // PERSIST_ANSWERED guard above is what stops it being asked twice.
            "favorite_toy", "favorite_food", "favorite_sound", "favorite_activity", "favorite_soothing" -> true
            "play_today" -> !hasActivityOn(snapshot, today)
            "feeding_type" -> !hasMilkSince(snapshot, days = 2)
            "recent_facility" -> !hasVisitSince(snapshot, days = 7)
            "mood_after_sleep" -> latestSleepMoodMissing(snapshot)
            "open_notes" -> false
            else -> false
        }
    }

    /** Values under 20 are treated as kilograms (3.2 → 3200 g); otherwise grams. */
    fun parseBirthWeightG(raw: String): Double? {
        val n = raw.trim().replace(",", "").toDoubleOrNull() ?: return null
        if (n <= 0) return null
        return if (n < 20) n * 1000.0 else n
    }

    fun parsePositiveNumber(raw: String): Double? =
        raw.trim().replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

    private fun fillName(prompt: InfoAskPrompt, childName: String): InfoAskPrompt {
        val th = prompt.questionTh.replace("{name}", childName)
        val en = prompt.questionEn.replace("{name}", childName)
        return prompt.copy(questionTh = th, questionEn = en)
    }

    private fun logDate(iso: String?): LocalDate? =
        TimeUtils.localDate(iso)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun hasSleepSince(snapshot: InfoAskSnapshot, days: Int, type: String): Boolean {
        val cutoff = snapshot.today.minusDays(days.toLong())
        return snapshot.sleepLogs.any { log ->
            log.sleepType == type && (logDate(log.startTime)?.let { !it.isBefore(cutoff) } == true)
        }
    }

    private fun hasSleepOn(snapshot: InfoAskSnapshot, day: LocalDate, type: String): Boolean =
        snapshot.sleepLogs.any { log ->
            log.sleepType == type && logDate(log.startTime) == day
        }

    private fun hasActivityOn(snapshot: InfoAskSnapshot, day: LocalDate): Boolean =
        snapshot.activities.any { logDate(it.startsAt) == day }

    private fun hasMilkSince(snapshot: InfoAskSnapshot, days: Int): Boolean {
        val cutoff = snapshot.today.minusDays(days.toLong())
        return snapshot.milkLogs.any { logDate(it.loggedAt)?.let { !it.isBefore(cutoff) } == true }
    }

    private fun hasVisitSince(snapshot: InfoAskSnapshot, days: Int): Boolean {
        val cutoff = snapshot.today.minusDays(days.toLong())
        return snapshot.visits.any { logDate(it.visitDatetime)?.let { !it.isBefore(cutoff) } == true }
    }

    private fun latestSleepMoodMissing(snapshot: InfoAskSnapshot): Boolean {
        val latest = snapshot.sleepLogs.firstOrNull() ?: return false
        return latest.mood.isNullOrBlank()
    }

    internal val ALL: List<InfoAskPrompt> = listOf(
        InfoAskPrompt(
            "favorite_toy", InfoAskSubject.CHILD, InfoAskKind.TEXT,
            "ของเล่นหรือของชิ้นไหนที่ {name} ชอบที่สุดคะ?",
            "Which toy or object does {name} love most?",
            hintTh = "เช่น ตุ๊กตากระต่าย 'มีมี่'",
            hintEn = "e.g. a rabbit doll called Mimi"
        ),
        InfoAskPrompt(
            "favorite_food", InfoAskSubject.CHILD, InfoAskKind.TEXT,
            "อาหารหรือรสชาติไหนที่ {name} ชอบเป็นพิเศษคะ?",
            "Which food or flavour does {name} like best?",
            hintTh = "เช่น กล้วยน้ำว้าบด",
            hintEn = "e.g. mashed banana"
        ),
        InfoAskPrompt(
            "favorite_sound", InfoAskSubject.CHILD, InfoAskKind.TEXT,
            "เพลงหรือเสียงไหนที่ช่วยให้ {name} ผ่อนคลายคะ?",
            "Which song or sound calms {name} down?",
            hintTh = "เช่น White Noise เสียงฝน",
            hintEn = "e.g. rain white noise"
        ),
        InfoAskPrompt(
            "favorite_activity", InfoAskSubject.CHILD, InfoAskKind.TEXT,
            "กิจกรรมไหนที่ {name} ชอบทำที่สุดคะ?",
            "Which activity does {name} enjoy most?",
            hintTh = "เช่น ออกไปรับลมที่ระเบียง",
            hintEn = "e.g. sitting out on the balcony"
        ),
        InfoAskPrompt(
            "favorite_soothing", InfoAskSubject.CHILD, InfoAskKind.TEXT,
            "เวลา {name} งอแง ทำแบบไหนแล้วสงบลงคะ?",
            "What settles {name} when they are upset?",
            hintTh = "เช่น ลูบหลังเบาๆ จังหวะสม่ำเสมอ",
            hintEn = "e.g. slow, steady pats on the back"
        ),
        InfoAskPrompt(
            "birthdate", InfoAskSubject.CHILD, InfoAskKind.DATE,
            "วันเกิดของ {name} คือวันไหนคะ?",
            "What is {name}'s date of birth?",
            healthBook = true
        ),
        InfoAskPrompt(
            "delivery_type", InfoAskSubject.CHILD, InfoAskKind.CHIPS,
            "{name} คลอดแบบไหนคะ?",
            "How was {name} born?",
            chips = listOf(
                InfoAskChip("natural", "คลอดธรรมชาติ", "Natural birth"),
                InfoAskChip("cesarean", "ผ่าคลอด", "Cesarean"),
                InfoAskChip("assisted", "ช่วยคลอด", "Assisted"),
                InfoAskChip("other", "อื่นๆ", "Other")
            ),
            healthBook = true
        ),
        InfoAskPrompt(
            "place_of_birth", InfoAskSubject.CHILD, InfoAskKind.TEXT,
            "{name} คลอดที่โรงพยาบาล หรือคลินิกไหนคะ?",
            "Where was {name} born (hospital or clinic)?",
            healthBook = true
        ),
        InfoAskPrompt(
            "gestational_weeks", InfoAskSubject.CHILD, InfoAskKind.NUMBER,
            "อายุครรภ์ตอนคลอด {name} กี่สัปดาห์คะ?",
            "How many weeks pregnant were you when {name} was born?",
            healthBook = true,
            hintTh = "เช่น 38",
            hintEn = "e.g. 38"
        ),
        InfoAskPrompt(
            "birth_weight_g", InfoAskSubject.CHILD, InfoAskKind.NUMBER,
            "น้ำหนักแรกเกิดของ {name} เท่าไหร่คะ?",
            "What was {name}'s birth weight?",
            healthBook = true,
            hintTh = "กรัม หรือ กิโลกรัม เช่น 3200 หรือ 3.2",
            hintEn = "grams or kg, e.g. 3200 or 3.2"
        ),
        InfoAskPrompt(
            "birth_length_cm", InfoAskSubject.CHILD, InfoAskKind.NUMBER,
            "ส่วนสูงแรกเกิดของ {name} กี่เซนติเมตรคะ?",
            "What was {name}'s birth length in cm?",
            healthBook = true
        ),
        InfoAskPrompt(
            "birth_head_cm", InfoAskSubject.CHILD, InfoAskKind.NUMBER,
            "เส้นรอบศีรษะแรกเกิดของ {name} กี่เซนติเมตรคะ?",
            "What was {name}'s birth head circumference in cm?",
            healthBook = true
        ),
        InfoAskPrompt(
            "blood_group", InfoAskSubject.CHILD, InfoAskKind.CHIPS,
            "กรุ๊ปเลือดของ {name} คืออะไรคะ?",
            "What is {name}'s blood type?",
            chips = listOf(
                InfoAskChip("A", "A", "A"),
                InfoAskChip("B", "B", "B"),
                InfoAskChip("O", "O", "O"),
                InfoAskChip("AB", "AB", "AB"),
                InfoAskChip("unknown", "ยังไม่ทราบ", "Not sure")
            ),
            healthBook = true
        ),
        InfoAskPrompt(
            "allergies", InfoAskSubject.CHILD, InfoAskKind.TEXT,
            "{name} มีประวัติแพ้ยาหรือแพ้อาหารอะไรไหมคะ? (ถ้าไม่มีพิมพ์ \"ไม่มี\")",
            "Does {name} have any allergies to medicine or food? (type \"None\" if none)",
            healthBook = true,
            hintTh = "เช่น นมวัว, ไข่ขาว, ยาพารา หรือ ไม่มี",
            hintEn = "e.g. Cow milk, eggs, or None"
        ),
        InfoAskPrompt(
            "guardian_name", InfoAskSubject.USER, InfoAskKind.TEXT,
            "ชื่อ-นามสกุลของคุณที่ใช้ในสมุดสุขภาพคืออะไรคะ?",
            "What name should we use for you in the Health Book?",
            healthBook = true
        ),
        InfoAskPrompt(
            "guardian_relation", InfoAskSubject.USER, InfoAskKind.CHIPS,
            "คุณเกี่ยวข้องกับลูกอย่างไรคะ?",
            "How are you related to the child?",
            chips = listOf(
                InfoAskChip("mother", "แม่", "Mother"),
                InfoAskChip("father", "พ่อ", "Father"),
                InfoAskChip("guardian", "ผู้ปกครอง", "Guardian")
            ),
            healthBook = true
        ),
        InfoAskPrompt(
            "guardian_phone", InfoAskSubject.USER, InfoAskKind.TEXT,
            "เบอร์โทรติดต่อของคุณคืออะไรคะ?",
            "What is your phone number?",
            healthBook = true
        ),
        InfoAskPrompt(
            "guardian_address", InfoAskSubject.USER, InfoAskKind.TEXT,
            "ที่อยู่สำหรับสมุดสุขภาพน้องรักคืออะไรคะ?",
            "What address should go in the NongRak Health Book?",
            healthBook = true
        ),
        InfoAskPrompt(
            "sleep_last_night", InfoAskSubject.CHILD, InfoAskKind.CHIPS,
            "เมื่อคืน {name} นอนเป็นอย่างไรบ้างคะ?",
            "How did {name} sleep last night?",
            chips = listOf(
                InfoAskChip("1", "1 ดาว", "1 star"),
                InfoAskChip("2", "2 ดาว", "2 stars"),
                InfoAskChip("3", "3 ดาว", "3 stars"),
                InfoAskChip("4", "4 ดาว", "4 stars"),
                InfoAskChip("5", "5 ดาว", "5 stars")
            )
        ),
        InfoAskPrompt(
            "typical_bedtime", InfoAskSubject.CHILD, InfoAskKind.CHIPS,
            "ปกติ {name} เข้านอนกี่โมงคะ?",
            "What time does {name} usually go to bed?",
            chips = listOf(
                InfoAskChip("19:00", "19:00", "7:00 pm"),
                InfoAskChip("20:00", "20:00", "8:00 pm"),
                InfoAskChip("21:00", "21:00", "9:00 pm"),
                InfoAskChip("22:00", "22:00", "10:00 pm")
            )
        ),
        InfoAskPrompt(
            "nap_today", InfoAskSubject.CHILD, InfoAskKind.CHIPS,
            "วันนี้ {name} นอนกลางวันไหมคะ?",
            "Did {name} nap today?",
            chips = listOf(
                InfoAskChip("yes", "นอนแล้ว", "Yes"),
                InfoAskChip("no", "ยังไม่นอน", "No")
            )
        ),
        InfoAskPrompt(
            "activity_level", InfoAskSubject.CHILD, InfoAskKind.CHIPS,
            "{name} ขยับตัว / เล่นมากน้อยแค่ไหนคะ?",
            "How active is {name} usually?",
            chips = listOf(
                InfoAskChip("low", "น้อย", "Low"),
                InfoAskChip("moderate", "ปานกลาง", "Moderate"),
                InfoAskChip("high", "มาก", "High")
            )
        ),
        InfoAskPrompt(
            "play_today", InfoAskSubject.CHILD, InfoAskKind.CHIPS,
            "วันนี้ {name} เล่นอะไรเป็นหลักคะ?",
            "What did {name} mainly do today?",
            chips = listOf(
                InfoAskChip("indoor", "เล่นในบ้าน", "Played indoors"),
                InfoAskChip("outdoor", "ออกไปข้างนอก", "Went outside"),
                InfoAskChip("book", "อ่านหนังสือ", "Read a book"),
                InfoAskChip("other", "อื่นๆ", "Other")
            )
        ),
        InfoAskPrompt(
            "feeding_type", InfoAskSubject.CHILD, InfoAskKind.CHIPS,
            "ตอนนี้ {name} กินนมแบบไหนเป็นหลักคะ?",
            "How is {name} mainly fed right now?",
            chips = listOf(
                InfoAskChip("breast", "นมแม่", "Breastmilk"),
                InfoAskChip("formula", "นมผสม", "Formula"),
                InfoAskChip("both", "ทั้งสอง", "Both")
            )
        ),
        InfoAskPrompt(
            "recent_facility", InfoAskSubject.CHILD, InfoAskKind.TEXT,
            "โรงพยาบาลหรือคลินิกที่พา {name} ไปเมื่อไม่นานนี้ชื่ออะไรคะ?",
            "Which hospital or clinic has {name} visited recently?"
        ),
        InfoAskPrompt(
            "mood_after_sleep", InfoAskSubject.CHILD, InfoAskKind.CHIPS,
            "หลังนอน {name} อารมณ์เป็นอย่างไรคะ?",
            "How was {name}'s mood after sleep?",
            chips = listOf(
                InfoAskChip("happy", "สดใส", "Happy"),
                InfoAskChip("calm", "สงบ", "Calm"),
                InfoAskChip("fussy", "งอแง", "Fussy")
            )
        ),
        InfoAskPrompt(
            "open_notes", InfoAskSubject.CHILD, InfoAskKind.TEXT,
            "มีอะไรเกี่ยวกับ {name} ที่อยากบอกเพิ่มไหมคะ?",
            "Anything else you'd like to tell us about {name}?"
        )
    )
}
