package com.lullabyai.app.util

import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorVisitRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.data.repository.SleepRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The health book, shaped as the printed NongRak "สมุดบันทึกสุขภาพเด็ก" form.
 *
 * Each page here corresponds to one sheet of the paper form in `Health-Form/`, in the same order
 * and with the same sections. The structure is deliberately NOT a generic block flow: a form is
 * a fixed layout with fixed column headings, and describing it as free-flowing blocks is how the
 * printed copy and the paper original drift apart.
 *
 * Rendering happens ONCE, in [HealthBookPageRenderer] — the on-screen book and the exported PDF
 * are the same drawing at two sizes, so they cannot disagree about layout either.
 */
sealed interface FormPage {

    /** Section name for the running head and the contents list. */
    val chapter: String

    data class Cover(
        val childName: String,
        val nickname: String,
        val birthdate: String,
        val healthcareCenter: String
    ) : FormPage {
        override val chapter get() = "ปก"
    }

    data class Instructions(
        val steps: List<Pair<String, String>>,
        val symbols: List<Pair<String, String>>,
        val importantNotes: List<String>
    ) : FormPage {
        override val chapter get() = "คำแนะนำ"
    }

    data class BasicInfo(
        val child: List<Pair<String, String>>,
        val gender: String,
        val birth: List<Pair<String, String>>,
        val deliveryType: String,
        val guardian: List<Pair<String, String>>,
        val healthcare: List<Pair<String, String>>,
        val feeding: List<Pair<String, Boolean>>,
        val sleepHours: String,
        val sleepWindow: String,
        val development: List<Pair<String, Boolean>>,
        val notes: List<String>
    ) : FormPage {
        override val chapter get() = "1. ข้อมูลเบื้องต้น"
    }

    data class Immunization(
        val rows: List<List<String>>,
        val part: Int,
        val parts: Int,
        val reactions: List<String>,
        val healthcareNotes: List<String>
    ) : FormPage {
        override val chapter get() = "2. ประวัติการได้รับวัคซีน"
    }

    data class Illness(
        val rows: List<List<String>>,
        val part: Int,
        val parts: Int,
        val allergies: List<String>,
        val familyHistory: List<String>
    ) : FormPage {
        override val chapter get() = "3. ประวัติการเจ็บป่วย"
    }

    data class Lifestyle(
        val nutrition: List<List<String>>,
        val exercise: List<List<String>>,
        val oral: List<List<String>>,
        val part: Int,
        val parts: Int,
        val notes: List<String>
    ) : FormPage {
        override val chapter get() = "4. โภชนาการ ออกกำลังกาย สุขภาพช่องปาก"
    }

    data class Growth(
        val rows: List<List<String>>,
        val points: List<GrowthPoint>,
        val part: Int,
        val parts: Int,
        val notes: List<String>
    ) : FormPage {
        override val chapter get() = "5. การเจริญเติบโต"
    }

    data class Closing(
        val notes: List<String>,
        val primaryDoctor: String,
        val healthcareCenter: String,
        val phone: String,
        val firstUsed: String,
        val lastUpdated: String
    ) : FormPage {
        override val chapter get() = "ปิดเล่ม"
    }
}

/** One plotted measurement. Nulls are allowed: a visit may weigh but not measure height. */
data class GrowthPoint(
    val ageMonths: Double,
    val weightKg: Double?,
    val heightCm: Double?,
    val headCm: Double?
)

data class HealthBook(
    val childName: String,
    val ageText: String,
    val birthdate: String,
    val bloodGroup: String,
    val generatedAt: String,
    val pages: List<FormPage>
)

/**
 * Gathers a child's whole record and lays it into the printed form.
 *
 * Every source comes from the repository that already owns it, so the book cannot disagree with
 * the screen the parent just came from.
 */
object HealthBookBuilder {

    // Row counts per sheet, taken from the paper form. Matching them exactly is what makes a
    // half-empty page look like the real book rather than a table that stopped early.
    private const val VACCINE_ROWS = 8
    private const val ILLNESS_ROWS = 9
    private const val NUTRITION_ROWS = 6
    private const val EXERCISE_ROWS = 4
    private const val ORAL_ROWS = 4
    private const val GROWTH_ROWS = 6

    suspend fun build(child: Child, isThai: Boolean): HealthBook = coroutineScope {
        val id = child.id
        val health = HealthRepository()

        // Fetched together: the book is one document and a parent waiting on it should wait once.
        val vaccinesD = async { health.getVaccineRecords(id).getOrDefault(emptyList()) }
        val allergiesD = async { health.getAllergies(id).getOrDefault(emptyList()) }
        val chronicD = async { health.getChronicConditions(id).getOrDefault(emptyList()) }
        val growthD = async { health.getGrowthRecords(id).getOrDefault(emptyList()) }
        val recordsD = async { health.getHealthRecords(id).getOrDefault(emptyList()) }
        val visitsD = async { DoctorVisitRepository().getVisits(id).getOrDefault(emptyList()) }
        val milestonesD = async { ChildRepository().getMilestones(id).getOrDefault(emptyList()) }
        val sleepD = async { SleepRepository().getLogs(id).getOrDefault(emptyList()) }
        val milkD = async { NutritionRepository().getMilkLogs(id) }
        val foodD = async {
            NutritionRepository().getFoodLogsRange(
                id,
                java.time.LocalDate.now().minusDays(60).toString()
            )
        }

        val vaccines = vaccinesD.await()
        val allergies = allergiesD.await()
        val chronic = chronicD.await()
        val growth = growthD.await()
        val records = recordsD.await()
        val visits = visitsD.await()
        val milestones = milestonesD.await()
        val sleep = sleepD.await()
        val milk = milkD.await()
        val food = foodD.await()

        val pages = mutableListOf<FormPage>()

        pages += FormPage.Cover(
            childName = child.name,
            nickname = nicknameOf(child),
            birthdate = child.birthdate.ifBlank { "" },
            healthcareCenter = visits.firstNotNullOfOrNull { it.facilityName?.takeIf(String::isNotBlank) } ?: ""
        )
        pages += instructionsPage()
        pages += basicInfoPage(child, allergies, chronic, milk, sleep, milestones, visits)
        pages += immunizationPages(vaccines)
        pages += illnessPages(records, visits, allergies, chronic)
        pages += lifestylePages(food, milk)
        pages += growthPages(child, growth)
        pages += closingPage(child, visits, growth, records)

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
    // Pages
    // -----------------------------------------------------------------------------------------

    private fun instructionsPage() = FormPage.Instructions(
        steps = listOf(
            "บันทึกข้อมูลให้ครบถ้วน" to "กรอกข้อมูลให้ครบถ้วนทุกครั้งที่มีการรับบริการ หรือมีการเปลี่ยนแปลงข้อมูลสำคัญ",
            "อัปเดตอย่างสม่ำเสมอ" to "นำสมุดมาทุกครั้งที่พบแพทย์ เพื่อติดตามการเจริญเติบโตและพัฒนาการ",
            "ติดตามกราฟการเจริญเติบโต" to "สังเกตแนวโน้มการเจริญเติบโตของลูกน้อย จากกราฟน้ำหนัก ส่วนสูง และรอบศีรษะ",
            "เก็บรักษาอย่างดี" to "เก็บสมุดไว้ในที่ปลอดภัย ไม่ชำรุด หรือสูญหาย เพื่อใช้ประโยชน์ระยะยาว",
            "ใช้ร่วมกันระหว่างผู้ปกครองและแพทย์" to "ข้อมูลในสมุดนี้จะช่วยให้ทีมแพทย์สามารถดูแลลูกน้อยได้อย่างต่อเนื่องและเหมาะสมที่สุด"
        ),
        symbols = listOf(
            "วัคซีน" to "บันทึกการได้รับวัคซีน",
            "การเจริญเติบโต" to "บันทึกน้ำหนัก ส่วนสูง และรอบศีรษะ",
            "พัฒนาการ" to "บันทึกพัฒนาการตามช่วงวัย",
            "นัดหมาย" to "บันทึกการนัดหมายครั้งถัดไป",
            "หมายเหตุ" to "บันทึกข้อมูลเพิ่มเติม"
        ),
        importantNotes = listOf(
            "ข้อมูลในสมุดนี้เป็นความลับ ใช้สำหรับการดูแลสุขภาพของลูกน้อยเท่านั้น",
            "หากสมุดหาย กรุณาแจ้งโรงพยาบาล/สถานพยาบาล เพื่อขอข้อมูลใหม่",
            "สามารถนำข้อมูลในสมุดไปใช้ต่อเนื่อง แม้เปลี่ยนโรงพยาบาลหรือย้ายสถานที่"
        )
    )

    private fun basicInfoPage(
        child: Child,
        allergies: List<com.lullabyai.app.data.model.Allergy>,
        chronic: List<com.lullabyai.app.data.model.ChronicCondition>,
        milk: List<com.lullabyai.app.data.model.MilkLog>,
        sleep: List<com.lullabyai.app.data.model.SleepLog>,
        milestones: List<com.lullabyai.app.data.model.DevelopmentMilestone>,
        visits: List<com.lullabyai.app.data.model.DoctorVisit>
    ): FormPage.BasicInfo {
        val months = ageMonths(child.birthdate)

        // "Confirmed none" and "never asked" are different clinical facts; a blank line for both
        // throws that distinction away on the one page a doctor reads first.
        val allergyText = when {
            allergies.isNotEmpty() -> allergies.joinToString(", ") { it.name }
            child.noKnownAllergies -> "ไม่มีประวัติแพ้ (ยืนยันแล้ว)"
            !child.allergies.isNullOrBlank() -> child.allergies!!
            else -> ""
        }

        val breast = milk.count { it.milkType == "breast" }
        val formula = milk.count { it.milkType == "formula" }

        val nightSleep = sleep.filter { it.sleepType == "main" }
            .mapNotNull { hoursBetween(it.startTime, it.endTime) }
        val avgSleep = if (nightSleep.isEmpty()) "" else
            String.format(java.util.Locale.US, "%.1f", nightSleep.average())
        val lastNight = sleep.firstOrNull { it.sleepType == "main" && it.endTime != null }

        val achieved = milestones.filter { it.isAchieved }.map { it.milestoneName }
        fun done(vararg keys: String) = achieved.any { name -> keys.any { name.contains(it) } }

        return FormPage.BasicInfo(
            child = listOf(
                "ชื่อ-นามสกุล (Child's Name)" to child.name,
                "ชื่อเล่น (Nickname)" to nicknameOf(child)
            ),
            gender = child.gender,
            birth = listOf(
                "วันเดือนปีเกิด (Date of Birth)" to child.birthdate,
                "อายุ (Age)" to if (months >= 0) "$months" else "",
                "กรุ๊ปเลือด (Blood Type)" to (child.bloodGroup ?: ""),
                "โรคประจำตัว (Chronic Disease)" to chronic.joinToString(", ") { it.name },
                "การแพ้ยา/อาหาร (Allergy)" to allergyText,
                "สถานที่คลอด (Place of Birth)" to "",
                "อายุครรภ์ (Gestational Age)" to "",
                "น้ำหนักแรกเกิด (Birth Weight)" to "",
                "ส่วนสูงแรกเกิด (Birth Length)" to "",
                "เส้นรอบศีรษะแรกเกิด (Head Circumference)" to ""
            ),
            deliveryType = "",
            guardian = listOf(
                "ชื่อบิดา (Father's Name)" to "",
                "ชื่อมารดา (Mother's Name)" to "",
                "ผู้ปกครอง (Guardian)" to "",
                "ความสัมพันธ์ (Relation)" to "",
                "ที่อยู่ (Address)" to "",
                "โทรศัพท์ (Phone)" to "",
                "อีเมล (Email)" to ""
            ),
            healthcare = listOf(
                "ชื่อโรงพยาบาล/สถานพยาบาล (Hospital/Clinic Name)" to
                    (visits.firstNotNullOfOrNull { it.facilityName?.takeIf(String::isNotBlank) } ?: ""),
                "ที่อยู่ (Address)" to "",
                "โทรศัพท์ (Phone)" to "",
                "แพทย์ประจำตัว (Primary Doctor)" to
                    (visits.firstNotNullOfOrNull { it.doctorName?.takeIf(String::isNotBlank) } ?: "")
            ),
            feeding = listOf(
                "นมแม่อย่างเดียว (Breastmilk Only)" to (breast > 0 && formula == 0),
                "นมผสม (Formula)" to (formula > 0 && breast == 0),
                "ทั้งนมแม่และนมผสม (Breastmilk & Formula)" to (breast > 0 && formula > 0),
                "อื่น ๆ (Others)" to false
            ),
            sleepHours = avgSleep,
            sleepWindow = lastNight?.let {
                "${clock(it.startTime)} ถึง ${clock(it.endTime)}"
            } ?: "",
            development = listOf(
                "ยิ้ม (Smiling)" to done("ยิ้ม", "Smil"),
                "พลิกคว่ำ/หงาย (Turning over)" to done("พลิก", "Roll", "Turn"),
                "นั่ง (Sitting)" to done("นั่ง", "Sit"),
                "คลาน (Crawling)" to done("คลาน", "Crawl"),
                "ยืน (Standing)" to done("ยืน", "Stand"),
                "เดิน (Walking)" to done("เดิน", "Walk"),
                "พูด (Talking)" to done("พูด", "Talk", "Speak"),
                "อื่น ๆ (Others)" to false
            ),
            notes = (child.notes ?: "").split("\n").filter { it.isNotBlank() }.take(6)
        )
    }

    private fun immunizationPages(
        vaccines: List<com.lullabyai.app.data.model.VaccineRecord>
    ): List<FormPage> {
        val given = vaccines
            .filter { it.status == "completed" || !it.dateGiven.isNullOrBlank() }
            .sortedBy { it.dateGiven ?: "" }
            .map { v ->
                listOf(
                    v.ageGroup ?: "",
                    v.vaccineName,
                    doseOf(v),
                    (v.dateGiven ?: "").take(10),
                    v.place ?: "",
                    v.notes ?: ""
                )
            }

        val reactions = vaccines.mapNotNull { v ->
            v.notes?.takeIf { it.isNotBlank() && it.contains("แพ้") }?.let { "${v.vaccineName}: $it" }
        }

        return paginateTable(given, VACCINE_ROWS) { rows, part, parts ->
            FormPage.Immunization(rows, part, parts, reactions, emptyList())
        }
    }

    private fun illnessPages(
        records: List<com.lullabyai.app.data.model.HealthRecord>,
        visits: List<com.lullabyai.app.data.model.DoctorVisit>,
        allergies: List<com.lullabyai.app.data.model.Allergy>,
        chronic: List<com.lullabyai.app.data.model.ChronicCondition>
    ): List<FormPage> {
        // An illness episode and a doctor visit are the same line in a paper book: "this happened,
        // this is what was done about it". Merging them here is what makes the sheet readable.
        val fromRecords = records.map { r ->
            val visit = visits.firstOrNull { v ->
                v.visitDatetime.take(10) == (r.recordedAt ?: "").take(10)
            }
            listOf(
                (r.recordedAt ?: r.createdAt ?: "").take(10),
                listOfNotNull(
                    r.title.takeIf(String::isNotBlank),
                    r.additionalSymptoms?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ).joinToString(" · "),
                listOfNotNull(
                    visit?.treatmentPlan?.takeIf(String::isNotBlank),
                    r.description?.takeIf(String::isNotBlank)
                ).joinToString(" · "),
                visit?.facilityName ?: "",
                visit?.doctorName ?: "",
                statusLabel(r.status)
            )
        }

        val visitOnly = visits
            .filter { v -> records.none { (it.recordedAt ?: "").take(10) == v.visitDatetime.take(10) } }
            .filter { it.visitType == "completed" }
            .map { v ->
                listOf(
                    v.visitDatetime.take(10),
                    v.diagnosis ?: v.reason ?: "",
                    v.treatmentPlan ?: "",
                    v.facilityName ?: "",
                    v.doctorName ?: "",
                    ""
                )
            }

        val all = (fromRecords + visitOnly).sortedByDescending { it[0] }

        return paginateTable(all, ILLNESS_ROWS) { rows, part, parts ->
            FormPage.Illness(
                rows, part, parts,
                allergies = allergies.map { "${it.name} (${severityLabel(it.severity)})" },
                familyHistory = chronic.map { c -> listOfNotNull(c.name, c.notes).joinToString(" — ") }
            )
        }
    }

    private fun lifestylePages(
        food: List<com.lullabyai.app.data.model.FoodLog>,
        milk: List<com.lullabyai.app.data.model.MilkLog>
    ): List<FormPage> {
        // One row per DAY, not per item: the paper form has a Date column and a Main Meals column,
        // so a day with four foods is one line, not four.
        val byDay = food.groupBy { it.logDate }
        val milkByDay = milk.groupBy { (it.loggedAt ?: "").take(10) }

        val nutrition = byDay.keys.sortedDescending().map { day ->
            val items = byDay[day].orEmpty()
            fun meals(vararg types: String) = items
                .filter { it.mealType in types }
                .joinToString(", ") { it.itemName }
            val water = milkByDay[day].orEmpty().sumOf { it.volumeMl }
            listOf(
                day,
                meals("breakfast", "lunch", "dinner"),
                meals("snack"),
                items.filter { veggieOrFruit(it.itemName) }.joinToString(", ") { it.itemName },
                if (water > 0) "$water" else "",
                items.mapNotNull { it.calories }.sum().takeIf { it > 0 }?.let { "$it kcal" } ?: ""
            )
        }

        // The app records neither exercise sessions nor tooth-brushing, so those tables print as
        // blank ruled rows — the same thing the paper book does, and a parent can fill them in.
        return paginateTable(nutrition, NUTRITION_ROWS) { rows, part, parts ->
            FormPage.Lifestyle(
                nutrition = rows,
                exercise = List(EXERCISE_ROWS) { List(4) { "" } },
                oral = List(ORAL_ROWS) { List(4) { "" } },
                part = part,
                parts = parts,
                notes = emptyList()
            )
        }
    }

    private fun growthPages(
        child: Child,
        growth: List<com.lullabyai.app.data.model.GrowthRecord>
    ): List<FormPage> {
        val sorted = growth.sortedByDescending { it.recordedAt ?: "" }

        val rows = sorted.map { g ->
            val date = (g.recordedAt ?: "").take(10)
            listOf(
                date,
                monthsBetween(child.birthdate, date)?.let { "$it" } ?: "",
                g.weightKg?.let { fmt(it) } ?: "",
                g.heightCm?.let { fmt(it) } ?: "",
                "",
                g.notes ?: ""
            )
        }

        // Charts plot the whole history on every page, oldest first — a trend line that restarts
        // per page is not a trend line.
        val points = growth
            .mapNotNull { g ->
                val m = monthsBetween(child.birthdate, (g.recordedAt ?: "").take(10)) ?: return@mapNotNull null
                GrowthPoint(m.toDouble(), g.weightKg, g.heightCm, null)
            }
            .sortedBy { it.ageMonths }

        return paginateTable(rows, GROWTH_ROWS) { r, part, parts ->
            FormPage.Growth(r, points, part, parts, emptyList())
        }
    }

    private fun closingPage(
        child: Child,
        visits: List<com.lullabyai.app.data.model.DoctorVisit>,
        growth: List<com.lullabyai.app.data.model.GrowthRecord>,
        records: List<com.lullabyai.app.data.model.HealthRecord>
    ): FormPage.Closing {
        val stamps = listOfNotNull(
            child.createdAt,
            growth.mapNotNull { it.recordedAt }.maxOrNull(),
            records.mapNotNull { it.recordedAt }.maxOrNull()
        )
        return FormPage.Closing(
            notes = emptyList(),
            primaryDoctor = visits.firstNotNullOfOrNull { it.doctorName?.takeIf(String::isNotBlank) } ?: "",
            healthcareCenter = visits.firstNotNullOfOrNull { it.facilityName?.takeIf(String::isNotBlank) } ?: "",
            phone = "",
            firstUsed = (child.createdAt ?: "").take(10),
            lastUpdated = stamps.mapNotNull { it.take(10).takeIf(String::isNotBlank) }.maxOrNull() ?: ""
        )
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Splits data rows across as many sheets as the form needs, padding the last one with blanks.
     *
     * Padding matters: a half-filled sheet with ruled empty rows reads as a book with room left,
     * while a table that simply stops reads as a record that was truncated.
     */
    private fun <T : FormPage> paginateTable(
        rows: List<List<String>>,
        perPage: Int,
        page: (List<List<String>>, Int, Int) -> T
    ): List<FormPage> {
        val cols = rows.firstOrNull()?.size ?: 6
        if (rows.isEmpty()) return listOf(page(List(perPage) { List(cols) { "" } }, 1, 1))
        val chunks = rows.chunked(perPage)
        return chunks.mapIndexed { i, chunk ->
            val padded = chunk + List(perPage - chunk.size) { List(cols) { "" } }
            page(padded, i + 1, chunks.size)
        }
    }

    private fun nicknameOf(child: Child): String {
        val n = child.notes ?: return ""
        return if (n.startsWith("ชื่อเล่น:")) n.substringAfter("ชื่อเล่น:").trim().lineSequence().first() else ""
    }

    private fun doseOf(v: com.lullabyai.app.data.model.VaccineRecord): String {
        val order = v.ageOrder ?: return ""
        return if (order > 0) "$order" else ""
    }

    private fun veggieOrFruit(name: String): Boolean {
        val keys = listOf("ผัก", "ผลไม้", "แครอท", "ฟักทอง", "กล้วย", "แอปเปิ", "ส้ม", "มะละกอ", "บรอกโคลี", "ตำลึง")
        return keys.any { name.contains(it) }
    }

    private fun fmt(d: Double) = String.format(java.util.Locale.US, "%.1f", d)

    private fun clock(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
                .toLocalTime().toString().take(5)
        }.getOrDefault("")
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

    private fun ageMonths(birthdate: String?): Int = monthsBetween(birthdate, null) ?: -1

    private fun monthsBetween(birthdate: String?, dateIso: String?): Int? {
        if (birthdate.isNullOrBlank()) return null
        val born = runCatching { java.time.LocalDate.parse(birthdate) }.getOrNull() ?: return null
        val at = if (dateIso.isNullOrBlank()) java.time.LocalDate.now()
        else runCatching { java.time.LocalDate.parse(dateIso) }.getOrNull() ?: return null
        val p = java.time.Period.between(born, at)
        val m = p.years * 12 + p.months
        return if (m < 0) null else m
    }

    fun ageLabel(birthdate: String?, th: Boolean): String {
        if (birthdate.isNullOrBlank()) return if (th) "ไม่ระบุอายุ" else "age unknown"
        val months = monthsBetween(birthdate, null) ?: return if (th) "ไม่ระบุอายุ" else "age unknown"
        if (months < 24) return if (th) "$months เดือน" else "$months months"
        return if (th) "${months / 12} ปี ${months % 12} เดือน" else "${months / 12}y ${months % 12}m"
    }

    private fun severityLabel(s: String) = when (s) {
        "mild" -> "เล็กน้อย"
        "moderate" -> "ปานกลาง"
        "severe" -> "รุนแรง"
        else -> s
    }

    private fun statusLabel(s: String) = when (s) {
        "monitoring" -> "กำลังติดตาม"
        "recovered" -> "หายแล้ว"
        else -> s
    }
}
