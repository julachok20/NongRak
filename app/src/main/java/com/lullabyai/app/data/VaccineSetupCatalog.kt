package com.lullabyai.app.data

/**
 * Thai NIP 0–3y schedule shared by onboarding vaccine pickers and [HealthRepository.insertDefaultVaccines].
 * [dbName] values must match vaccine_records.vaccine_name exactly.
 */
object VaccineSetupCatalog {

    data class Dose(
        val dbName: String,
        val description: String,
        val ageGroup: String,
        val ageMonths: Int
    )

    data class AgeGroup(
        val label: String,
        val hint: String,
        val doses: List<Dose>
    )

    val SCHEDULE: List<Dose> = listOf(
        Dose("BCG", "ป้องกันวัณโรครุนแรง", "แรกเกิด", 0),
        Dose("HBV เข็มที่ 1", "วัคซีนตับอักเสบบี เข็มแรก", "แรกเกิด", 0),
        Dose("DTP-HB-Hib เข็มที่ 1", "คอตีบ-บาดทะยัก-ไอกรน-ตับอักเสบบี-ฮิบ", "2 เดือน", 2),
        Dose("OPV เข็มที่ 1", "วัคซีนโปลิโอชนิดหยอด", "2 เดือน", 2),
        Dose("Rota เข็มที่ 1", "ป้องกันท้องเสียรุนแรงจากไวรัสโรต้า", "2 เดือน", 2),
        Dose("DTP-HB-Hib เข็มที่ 2", "คอตีบ-บาดทะยัก-ไอกรน เข็มที่ 2", "4 เดือน", 4),
        Dose("OPV เข็มที่ 2", "โปลิโอชนิดหยอด เข็มที่ 2", "4 เดือน", 4),
        Dose("IPV", "โปลิโอชนิดฉีด", "4 เดือน", 4),
        Dose("Rota เข็มที่ 2", "ป้องกันท้องเสียรุนแรง เข็มที่ 2", "4 เดือน", 4),
        Dose("DTP-HB-Hib เข็มที่ 3", "คอตีบ-บาดทะยัก-ไอกรน เข็มที่ 3", "6 เดือน", 6),
        Dose("OPV เข็มที่ 3", "โปลิโอชนิดหยอด เข็มที่ 3", "6 เดือน", 6),
        Dose("Rota เข็มที่ 3", "โรต้า เข็มที่ 3 (ถ้าใช้ชนิด 3 ครั้ง)", "6 เดือน", 6),
        Dose("ไข้หวัดใหญ่", "วัคซีนไข้หวัดใหญ่ตามฤดูกาล", "6 เดือน", 6),
        Dose("MMR เข็มที่ 1", "หัด-คางทูม-หัดเยอรมัน เข็มแรก", "9 เดือน", 9),
        Dose("JE เข็มที่ 1", "ไข้สมองอักเสบเจอี เข็มแรก", "1 ปี", 12),
        Dose("DTP กระตุ้น", "คอตีบ-บาดทะยัก-ไอกรน เข็มกระตุ้น", "1 ปี 6 เดือน", 18),
        Dose("OPV กระตุ้น", "โปลิโอชนิดหยอด เข็มกระตุ้น", "1 ปี 6 เดือน", 18),
        Dose("MMR เข็มที่ 2", "หัด-คางทูม-หัดเยอรมัน เข็มที่ 2", "1 ปี 6 เดือน", 18),
        Dose("JE เข็มที่ 2", "ไข้สมองอักเสบเจอี เข็มที่ 2", "2 ปี 6 เดือน", 30)
    )

    private val GROUP_HINTS = mapOf(
        "แรกเกิด" to "วัคซีนที่มักได้รับที่โรงพยาบาลหลังคลอด",
        "2 เดือน" to "นัดแรกหลังคลอดประมาณ 2 เดือน",
        "4 เดือน" to "นัดติดตามเมื่อลูกอายุประมาณ 4 เดือน",
        "6 เดือน" to "รวมวัคซีนไข้หวัดใหญ่ (ถ้าได้รับแล้ว)",
        "9 เดือน" to "MMR เข็มแรก — ตามตารางไทย 2567",
        "1 ปี" to "ไข้สมองอักเสบเจอี เข็มแรก",
        "1 ปี 6 เดือน" to "เข็มกระตุ้น + MMR เข็มที่ 2",
        "2 ปี 6 เดือน" to "ไข้สมองอักเสบเจอี เข็มที่ 2"
    )

    val SETUP_GROUPS: List<AgeGroup> = SCHEDULE
        .groupBy { it.ageGroup }
        .map { (label, doses) ->
            AgeGroup(
                label = label,
                hint = GROUP_HINTS[label].orEmpty(),
                doses = doses.sortedBy { it.ageMonths }
            )
        }
        .sortedBy { it.doses.first().ageMonths }

    val ALL_DB_NAMES: Set<String> = SCHEDULE.map { it.dbName }.toSet()

    fun ageMonthsFromBirthdate(birthdate: String): Int = try {
        val birth = java.time.LocalDate.parse(birthdate)
        val now = java.time.LocalDate.now()
        ((now.year - birth.year) * 12 + (now.monthValue - birth.monthValue)).coerceAtLeast(0)
    } catch (_: Exception) {
        0
    }

    /** Doses the child is old enough to have received — helpful default for beginner parents. */
    fun suggestedDosesForAge(ageMonths: Int): Set<String> =
        SCHEDULE.filter { it.ageMonths <= ageMonths }.map { it.dbName }.toSet()

    /** Map legacy onboarding chip labels (เข็ม vs เข็มที่) to canonical db names. */
    fun normalizeSelection(name: String): String? {
        if (name in ALL_DB_NAMES) return name
        val legacy = name
            .replace(" เข็ม ", " เข็มที่ ")
            .replace("Rota เข็มที่", "Rota เข็มที่")
        if (legacy in ALL_DB_NAMES) return legacy
        // Custom / other vaccine — not in the schedule
        return null
    }
}
