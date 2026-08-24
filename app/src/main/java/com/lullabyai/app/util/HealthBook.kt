package com.lullabyai.app.util

import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorVisitRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.MedicationRepository
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.data.repository.SleepRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * One page of the health book, described once and rendered twice.
 *
 * The on-screen flip view and the exported PDF both consume this structure. Building the book as
 * data rather than as UI is the whole point: two independent renderers would drift, and the
 * printed copy a parent hands to a doctor would eventually stop matching what the app showed
 * them, with no way to notice until someone compared the two side by side.
 */
sealed interface BookBlock {
    /** A heading inside a page, below the page title. */
    data class Heading(val text: String) : BookBlock

    /** A label/value pair, the workhorse of a medical record. */
    data class Field(val label: String, val value: String, val alert: Boolean = false) : BookBlock

    data class Paragraph(val text: String) : BookBlock

    /** A table row; [header] rows are drawn emphasised by both renderers. */
    data class Row(val cells: List<String>, val header: Boolean = false) : BookBlock

    data object Divider : BookBlock

    /** Reassurance, not a finding. Rendered quieter than a Field. */
    data class Note(val text: String) : BookBlock
}

data class BookPage(
    val chapter: String,
    val title: String,
    val blocks: List<BookBlock>
)

data class HealthBook(
    val childName: String,
    val ageText: String,
    val birthdate: String,
    val bloodGroup: String,
    val generatedAt: String,
    val pages: List<BookPage>
)

/**
 * Gathers a child's whole record and lays it out as a paginated book.
 *
 * Every source comes from the repository that already owns it, so the book cannot disagree with
 * the screen the parent just came from.
 */
object HealthBookBuilder {

    /**
     * Blocks per page.
     *
     * A real page ends where the paper does. Long chapters (vaccines, illness history) split
     * across as many pages as they need, so the book grows with the child instead of silently
     * truncating a medical record at an arbitrary limit.
     */
    private const val BLOCKS_PER_PAGE = 14

    suspend fun build(child: Child, isThai: Boolean): HealthBook = coroutineScope {
        val id = child.id
        val health = HealthRepository()

        // Fetched together: the book is one document and a parent waiting on it should wait once.
        val vaccinesD = async { health.getVaccineRecords(id).getOrDefault(emptyList()) }
        val allergiesD = async { health.getAllergies(id).getOrDefault(emptyList()) }
        val chronicD = async { health.getChronicConditions(id).getOrDefault(emptyList()) }
        val growthD = async { health.getGrowthRecords(id).getOrDefault(emptyList()) }
        val recordsD = async { health.getHealthRecords(id).getOrDefault(emptyList()) }
        val medsD = async { MedicationRepository().getSchedules(id).getOrDefault(emptyList()) }
        val visitsD = async { DoctorVisitRepository().getVisits(id).getOrDefault(emptyList()) }
        val milestonesD = async { ChildRepository().getMilestones(id).getOrDefault(emptyList()) }
        val sleepD = async { SleepRepository().getLogs(id).getOrDefault(emptyList()) }
        val milkD = async { NutritionRepository().getMilkLogs(id) }

        val vaccines = vaccinesD.await()
        val allergies = allergiesD.await()
        val chronic = chronicD.await()
        val growth = growthD.await()
        val records = recordsD.await()
        val meds = medsD.await()
        val visits = visitsD.await()
        val milestones = milestonesD.await()
        val sleep = sleepD.await()
        val milk = milkD.await()

        // Symptom timelines hang off each illness episode, so they can only be fetched once the
        // episodes are known. Capped: the book is a record, not an unbounded fan-out.
        val timelines = records.take(12).associate { rec ->
            rec.id to health.getSymptomLogs(rec.id).getOrDefault(emptyList())
        }

        val pages = mutableListOf<BookPage>()
        pages += profilePages(child, allergies, chronic, isThai)
        pages += vitalsPages(growth, isThai)
        pages += vaccinePages(vaccines, isThai)
        pages += illnessPages(records, timelines, isThai)
        pages += medicationPages(meds, isThai)
        pages += milestonePages(milestones, isThai)
        pages += routinePages(sleep, milk, isThai)
        pages += visitPages(visits, isThai)

        HealthBook(
            childName = child.name,
            ageText = ageLabel(child.birthdate, isThai),
            birthdate = child.birthdate.ifBlank { "-" },
            bloodGroup = child.bloodGroup ?: "-",
            generatedAt = java.time.LocalDate.now().toString(),
            pages = pages
        )
    }

    // -----------------------------------------------------------------------------------------
    // Chapters
    // -----------------------------------------------------------------------------------------

    private fun profilePages(
        child: Child,
        allergies: List<com.lullabyai.app.data.model.Allergy>,
        chronic: List<com.lullabyai.app.data.model.ChronicCondition>,
        th: Boolean
    ): List<BookPage> {
        val blocks = mutableListOf<BookBlock>()
        blocks += BookBlock.Heading(if (th) "ข้อมูลทั่วไป" else "Profile")
        blocks += BookBlock.Field(if (th) "ชื่อ" else "Name", child.name)
        blocks += BookBlock.Field(if (th) "วันเกิด" else "Date of birth", child.birthdate.ifBlank { "-" })
        blocks += BookBlock.Field(if (th) "อายุ" else "Age", ageLabel(child.birthdate, th))
        blocks += BookBlock.Field(if (th) "เพศ" else "Sex", genderLabel(child.gender, th))
        blocks += BookBlock.Field(if (th) "กรุ๊ปเลือด" else "Blood group", child.bloodGroup ?: "-")
        blocks += BookBlock.Field(if (th) "สัญชาติ" else "Nationality", child.nationality ?: "-")
        blocks += BookBlock.Divider

        blocks += BookBlock.Heading(if (th) "การแพ้" else "Allergies")
        when {
            allergies.isNotEmpty() -> allergies.forEach {
                blocks += BookBlock.Field(
                    it.name,
                    listOf(typeLabel(it.type, th), severityLabel(it.severity, th)).joinToString(" · "),
                    alert = true
                )
            }
            // "Confirmed none" and "never asked" are different clinical facts. A book that renders
            // both as an empty section throws that distinction away.
            child.noKnownAllergies -> blocks += BookBlock.Note(
                if (th) "ยืนยันแล้วว่าไม่มีประวัติแพ้" else "Confirmed: no known allergies"
            )
            !child.allergies.isNullOrBlank() -> blocks += BookBlock.Field(
                child.allergies ?: "-", if (th) "บันทึกไว้" else "recorded", alert = true
            )
            else -> blocks += BookBlock.Note(
                if (th) "ยังไม่ได้บันทึกข้อมูลการแพ้" else "Not yet recorded"
            )
        }

        blocks += BookBlock.Divider
        blocks += BookBlock.Heading(if (th) "โรคประจำตัว" else "Chronic conditions")
        if (chronic.isEmpty()) {
            blocks += BookBlock.Note(if (th) "ไม่มีโรคประจำตัวที่บันทึกไว้" else "None recorded")
        } else {
            chronic.forEach { blocks += BookBlock.Field(it.name, it.notes ?: "-", alert = true) }
        }

        return paginate(if (th) "ประวัติส่วนตัว" else "Profile", blocks, th)
    }

    private fun vitalsPages(
        growth: List<com.lullabyai.app.data.model.GrowthRecord>,
        th: Boolean
    ): List<BookPage> {
        val blocks = mutableListOf<BookBlock>()
        blocks += BookBlock.Heading(if (th) "น้ำหนักและส่วนสูง" else "Weight and height")
        if (growth.isEmpty()) {
            blocks += BookBlock.Note(if (th) "ยังไม่มีบันทึกการเจริญเติบโต" else "No measurements recorded")
        } else {
            blocks += BookBlock.Row(
                listOf(
                    if (th) "วันที่" else "Date",
                    if (th) "น้ำหนัก (กก.)" else "Weight (kg)",
                    if (th) "ส่วนสูง (ซม.)" else "Height (cm)"
                ),
                header = true
            )
            // Newest first: a doctor reads the current value before the trend.
            growth.sortedByDescending { it.recordedAt ?: "" }.forEach { g ->
                blocks += BookBlock.Row(
                    listOf(
                        (g.recordedAt ?: "-").take(10),
                        g.weightKg?.toString() ?: "-",
                        g.heightCm?.toString() ?: "-"
                    )
                )
            }
        }
        return paginate(if (th) "การเจริญเติบโต" else "Growth", blocks, th)
    }

    private fun vaccinePages(
        vaccines: List<com.lullabyai.app.data.model.VaccineRecord>,
        th: Boolean
    ): List<BookPage> {
        val blocks = mutableListOf<BookBlock>()
        val given = vaccines.filter { !it.dateGiven.isNullOrBlank() }
        val today = java.time.LocalDate.now().toString()
        val overdue = vaccines.filter {
            val d = it.scheduledDate
            it.dateGiven.isNullOrBlank() && !d.isNullOrBlank() && d < today
        }
        val upcoming = vaccines.filter {
            val d = it.scheduledDate
            it.dateGiven.isNullOrBlank() && !d.isNullOrBlank() && d >= today
        }

        blocks += BookBlock.Heading(if (th) "สรุป" else "Summary")
        blocks += BookBlock.Field(if (th) "ฉีดแล้ว" else "Completed", "${given.size}")
        blocks += BookBlock.Field(if (th) "รอฉีด" else "Upcoming", "${upcoming.size}")
        if (overdue.isNotEmpty()) {
            blocks += BookBlock.Field(if (th) "เลยกำหนด" else "Overdue", "${overdue.size}", alert = true)
        }
        blocks += BookBlock.Divider

        if (overdue.isNotEmpty()) {
            blocks += BookBlock.Heading(if (th) "เลยกำหนด" else "Overdue")
            overdue.forEach {
                blocks += BookBlock.Field(it.vaccineName, it.scheduledDate ?: "-", alert = true)
            }
            blocks += BookBlock.Divider
        }

        blocks += BookBlock.Heading(if (th) "ที่ฉีดแล้ว" else "Doses given")
        if (given.isEmpty()) {
            blocks += BookBlock.Note(if (th) "ยังไม่มีวัคซีนที่บันทึกว่าฉีดแล้ว" else "None recorded")
        } else {
            blocks += BookBlock.Row(
                listOf(
                    if (th) "วัคซีน" else "Vaccine",
                    if (th) "วันที่ฉีด" else "Date given",
                    if (th) "สถานที่" else "Place"
                ),
                header = true
            )
            given.sortedByDescending { it.dateGiven }.forEach {
                blocks += BookBlock.Row(listOf(it.vaccineName, it.dateGiven ?: "-", it.place ?: "-"))
            }
        }

        if (upcoming.isNotEmpty()) {
            blocks += BookBlock.Divider
            blocks += BookBlock.Heading(if (th) "นัดครั้งต่อไป" else "Upcoming")
            upcoming.sortedBy { it.scheduledDate }.forEach {
                blocks += BookBlock.Field(it.vaccineName, it.scheduledDate ?: "-")
            }
        }

        return paginate(if (th) "สมุดวัคซีน" else "Immunisation", blocks, th)
    }

    private fun illnessPages(
        records: List<com.lullabyai.app.data.model.HealthRecord>,
        timelines: Map<String, List<com.lullabyai.app.data.model.SymptomLog>>,
        th: Boolean
    ): List<BookPage> {
        val blocks = mutableListOf<BookBlock>()
        if (records.isEmpty()) {
            blocks += BookBlock.Note(if (th) "ยังไม่มีบันทึกการเจ็บป่วย" else "No illness recorded")
        } else {
            records.forEach { rec ->
                blocks += BookBlock.Heading(rec.title.ifBlank { if (th) "อาการ" else "Illness" })
                blocks += BookBlock.Field(
                    if (th) "วันที่" else "Date",
                    (rec.recordedAt ?: "-").take(10),
                    alert = rec.status == "monitoring"
                )
                blocks += BookBlock.Field(
                    if (th) "สถานะ" else "Status",
                    statusLabel(rec.status, th),
                    alert = rec.status == "monitoring"
                )
                rec.temperature?.let {
                    blocks += BookBlock.Field(if (th) "อุณหภูมิ" else "Temperature", "$it °C")
                }
                val desc = rec.description
                if (!desc.isNullOrBlank()) blocks += BookBlock.Paragraph(desc)

                // The day-by-day timeline is what makes this a record rather than a headline.
                timelines[rec.id]?.sortedBy { it.logDate }?.forEach { log ->
                    val symptoms = log.symptoms.keys.joinToString(", ") { symptomLabel(it, th) }
                    blocks += BookBlock.Row(
                        listOf(
                            log.logDate,
                            symptoms.ifBlank { "-" },
                            log.temperature?.let { "$it°" } ?: "-"
                        )
                    )
                }
                blocks += BookBlock.Divider
            }
        }
        return paginate(if (th) "ประวัติการเจ็บป่วย" else "Illness history", blocks, th)
    }

    private fun medicationPages(
        meds: List<com.lullabyai.app.data.model.MedicationSchedule>,
        th: Boolean
    ): List<BookPage> {
        val blocks = mutableListOf<BookBlock>()
        val active = meds.filter { it.status == "active" }
        val past = meds.filter { it.status != "active" }

        blocks += BookBlock.Heading(if (th) "ยาที่ใช้อยู่" else "Current medication")
        if (active.isEmpty()) {
            blocks += BookBlock.Note(if (th) "ไม่มียาที่กำลังใช้" else "None active")
        } else {
            active.forEach { m ->
                blocks += BookBlock.Field(
                    m.medicineName,
                    listOfNotNull(
                        m.dosage?.let { d -> "$d ${m.dosageUnit}" },
                        m.frequency
                    ).joinToString(" · ").ifBlank { "-" },
                    alert = true
                )
            }
        }

        if (past.isNotEmpty()) {
            blocks += BookBlock.Divider
            blocks += BookBlock.Heading(if (th) "ยาที่เคยใช้" else "Past medication")
            past.forEach { m ->
                blocks += BookBlock.Field(
                    m.medicineName,
                    listOfNotNull(m.startDate, m.endDate).joinToString(" - ").ifBlank { "-" }
                )
            }
        }
        return paginate(if (th) "ยา" else "Medication", blocks, th)
    }

    private fun milestonePages(
        milestones: List<com.lullabyai.app.data.model.DevelopmentMilestone>,
        th: Boolean
    ): List<BookPage> {
        val blocks = mutableListOf<BookBlock>()
        val achieved = milestones.filter { it.isAchieved }
        val pending = milestones.filter { !it.isAchieved }

        blocks += BookBlock.Field(if (th) "ทำได้แล้ว" else "Achieved", "${achieved.size}")
        blocks += BookBlock.Field(if (th) "ยังไม่ถึง" else "Not yet", "${pending.size}")
        blocks += BookBlock.Divider

        if (achieved.isEmpty()) {
            blocks += BookBlock.Note(if (th) "ยังไม่มีพัฒนาการที่บันทึกไว้" else "None recorded")
        } else {
            blocks += BookBlock.Row(
                listOf(
                    if (th) "พัฒนาการ" else "Milestone",
                    if (th) "วันที่ทำได้" else "Achieved"
                ),
                header = true
            )
            achieved.sortedBy { it.ageMonthsExpected ?: 0 }.forEach {
                blocks += BookBlock.Row(listOf(it.milestoneName, (it.achievedAt ?: "-").take(10)))
            }
        }
        return paginate(if (th) "พัฒนาการ" else "Development", blocks, th)
    }

    private fun routinePages(
        sleep: List<com.lullabyai.app.data.model.SleepLog>,
        milk: List<com.lullabyai.app.data.model.MilkLog>,
        th: Boolean
    ): List<BookPage> {
        val blocks = mutableListOf<BookBlock>()
        val finished = sleep.filter { !it.endTime.isNullOrBlank() }
        val lengths = finished.mapNotNull { hoursBetween(it.startTime, it.endTime) }
        val rated = finished.mapNotNull { it.qualityStars }

        blocks += BookBlock.Heading(if (th) "การนอน" else "Sleep")
        blocks += BookBlock.Field(if (th) "บันทึกทั้งหมด" else "Sessions recorded", "${finished.size}")
        if (lengths.isNotEmpty()) {
            blocks += BookBlock.Field(
                if (th) "เฉลี่ยต่อครั้ง" else "Average length",
                String.format(java.util.Locale.US, "%.1f", lengths.average()) +
                    (if (th) " ชม." else " h")
            )
        }
        if (rated.isNotEmpty()) {
            blocks += BookBlock.Field(
                if (th) "คุณภาพเฉลี่ย" else "Average quality",
                String.format(java.util.Locale.US, "%.1f", rated.average()) + " / 5"
            )
        }

        blocks += BookBlock.Divider
        blocks += BookBlock.Heading(if (th) "โภชนาการ" else "Feeding")
        if (milk.isEmpty()) {
            blocks += BookBlock.Note(if (th) "ยังไม่มีบันทึกการให้นม" else "No feeds recorded")
        } else {
            blocks += BookBlock.Field(if (th) "จำนวนครั้ง" else "Feeds recorded", "${milk.size}")
            blocks += BookBlock.Field(
                if (th) "ปริมาณเฉลี่ยต่อครั้ง" else "Average volume",
                "${milk.sumOf { it.volumeMl } / milk.size} ml"
            )
        }
        return paginate(if (th) "กิจวัตร" else "Routine", blocks, th)
    }

    private fun visitPages(
        visits: List<com.lullabyai.app.data.model.DoctorVisit>,
        th: Boolean
    ): List<BookPage> {
        val blocks = mutableListOf<BookBlock>()
        if (visits.isEmpty()) {
            blocks += BookBlock.Note(if (th) "ยังไม่มีบันทึกการพบแพทย์" else "No visits recorded")
        } else {
            visits.sortedByDescending { it.visitDatetime }.forEach { v ->
                blocks += BookBlock.Heading(
                    v.facilityName ?: v.doctorName ?: (if (th) "พบแพทย์" else "Visit")
                )
                blocks += BookBlock.Field(if (th) "วันที่" else "Date", v.visitDatetime.take(10))
                v.doctorName?.let { blocks += BookBlock.Field(if (th) "แพทย์" else "Doctor", it) }
                v.diagnosis?.let { blocks += BookBlock.Field(if (th) "วินิจฉัย" else "Diagnosis", it) }
                v.doctorAdvice?.let { blocks += BookBlock.Paragraph(it) }
                blocks += BookBlock.Divider
            }
        }
        return paginate(if (th) "การพบแพทย์" else "Doctor visits", blocks, th)
    }

    // -----------------------------------------------------------------------------------------
    // Pagination and labels
    // -----------------------------------------------------------------------------------------

    /**
     * Splits a chapter across as many pages as its content needs.
     *
     * Continuation pages carry the chapter name plus "(ต่อ)", the way a printed record does, so a
     * page read out of context still says what it belongs to.
     */
    private fun paginate(chapter: String, blocks: List<BookBlock>, th: Boolean): List<BookPage> {
        if (blocks.isEmpty()) return listOf(BookPage(chapter, chapter, emptyList()))
        return blocks.chunked(BLOCKS_PER_PAGE).mapIndexed { index, chunk ->
            BookPage(
                chapter = chapter,
                title = if (index == 0) chapter else "$chapter ${if (th) "(ต่อ)" else "(cont.)"}",
                blocks = chunk
            )
        }
    }

    private fun hoursBetween(start: String, end: String?): Double? {
        if (end.isNullOrBlank()) return null
        return runCatching {
            val a = java.time.Instant.parse(start)
            val b = java.time.Instant.parse(end)
            val h = java.time.Duration.between(a, b).toMinutes() / 60.0
            if (h <= 0) null else h
        }.getOrNull()
    }

    fun ageLabel(birthdate: String?, th: Boolean): String {
        if (birthdate.isNullOrBlank()) return if (th) "ไม่ระบุอายุ" else "age unknown"
        val born = runCatching { java.time.LocalDate.parse(birthdate) }.getOrNull()
            ?: return if (th) "ไม่ระบุอายุ" else "age unknown"
        val p = java.time.Period.between(born, java.time.LocalDate.now())
        val months = p.years * 12 + p.months
        if (months < 24) return if (th) "$months เดือน" else "$months months"
        return if (th) "${months / 12} ปี ${months % 12} เดือน" else "${months / 12}y ${months % 12}m"
    }

    private fun genderLabel(g: String?, th: Boolean) = when (g) {
        "male" -> if (th) "ชาย" else "Male"
        "female" -> if (th) "หญิง" else "Female"
        else -> if (th) "ไม่ระบุ" else "Unspecified"
    }

    private fun typeLabel(t: String, th: Boolean) = when (t) {
        "food" -> if (th) "อาหาร" else "Food"
        "environment" -> if (th) "สิ่งแวดล้อม" else "Environment"
        "medication" -> if (th) "ยา" else "Medication"
        else -> t
    }

    private fun severityLabel(s: String, th: Boolean) = when (s) {
        "mild" -> if (th) "เล็กน้อย" else "Mild"
        "moderate" -> if (th) "ปานกลาง" else "Moderate"
        "severe" -> if (th) "รุนแรง" else "Severe"
        else -> s
    }

    private fun statusLabel(s: String, th: Boolean) = when (s) {
        "monitoring" -> if (th) "กำลังติดตาม" else "Monitoring"
        "recovered" -> if (th) "หายแล้ว" else "Recovered"
        else -> s
    }

    private fun symptomLabel(key: String, th: Boolean) = when (key) {
        "fever" -> if (th) "ตัวร้อน" else "Fever"
        "cough" -> if (th) "ไอ" else "Cough"
        "runny_nose" -> if (th) "น้ำมูกไหล" else "Runny nose"
        "rash" -> if (th) "ผื่น" else "Rash"
        "lethargic" -> if (th) "ซึม" else "Lethargic"
        "vomiting" -> if (th) "อาเจียน" else "Vomiting"
        "diarrhea" -> if (th) "ท้องเสีย" else "Diarrhea"
        else -> key
    }
}
