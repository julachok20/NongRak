package com.lullabyai.app.util

import androidx.compose.ui.graphics.Color
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.GrowthRecord
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Growth status from the WHO Child Growth Standards (0–5 years), by sex and age in days.
 *
 * Tables are WHO's own LMS files (github.com/WorldHealthOrganization/anthro, data-raw/growthstandards),
 * bundled as assets/who/{weianthro,lenanthro,bmianthro}.txt. z-scores use WHO's LMS formula, with WHO's
 * restricted adjustment beyond ±3 SD for weight and BMI.
 *
 * Every status is judged at the age the measurement was TAKEN, for the child's sex. "สมส่วน" is
 * BMI-for-age: the same BMI means different things for a newborn and a 3-year-old, and the old
 * fixed 13–18.5 cut-off ignored age. No birthdate means no status (it used to be judged as a newborn).
 */
object GrowthReference {
    const val MAX_AGE_DAYS = 1856
    private const val DAYS_PER_MONTH = 30.4375

    private val RED = Color(0xFFC62828)
    private val ORANGE = Color(0xFFEF6C00)
    private val GREEN = Color(0xFF2E7D32)
    private val GREY = Color(0xFF757575)

    enum class Indicator(val file: String) { WEIGHT("weianthro"), HEIGHT("lenanthro"), BMI("bmianthro") }

    class Lms(val l: Double, val m: Double, val s: Double)

    data class GrowthStatus(val labelTh: String, val labelEn: String, val color: Color, val z: Double? = null)

    /** indicator -> sex (1 boy, 2 girl) -> LMS by age in days. */
    @Volatile private var tables: Map<Indicator, Map<Int, Array<Lms?>>> = emptyMap()

    /** Reads the bundled tables once. Called at app start; also on first use if that has not run yet. */
    fun load(context: android.content.Context) {
        if (tables.isNotEmpty()) return
        synchronized(this) {
            if (tables.isNotEmpty()) return
            tables = Indicator.entries.associateWith { ind ->
                context.assets.open("who/${ind.file}.txt").bufferedReader().use { parse(it.readText()) }
            }
        }
    }

    /** Tests (plain JVM, no assets): install the same files read from disk. */
    fun install(files: Map<Indicator, String>) {
        tables = files.mapValues { parse(it.value) }
    }

    internal fun parse(text: String): Map<Int, Array<Lms?>> {
        val bySex = mapOf(1 to arrayOfNulls<Lms>(MAX_AGE_DAYS + 1), 2 to arrayOfNulls<Lms>(MAX_AGE_DAYS + 1))
        text.lineSequence().drop(1).forEach { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 5) return@forEach
            val row = bySex[p[0].toIntOrNull()] ?: return@forEach
            val age = p[1].toIntOrNull() ?: return@forEach
            if (age in row.indices) row[age] = Lms(p[2].toDouble(), p[3].toDouble(), p[4].toDouble())
        }
        return bySex
    }

    private fun table(ind: Indicator): Map<Int, Array<Lms?>> {
        if (tables.isEmpty()) load(com.lullabyai.app.NongRakApp.instance)
        return tables.getValue(ind)
    }

    internal fun lms(ind: Indicator, sex: Int, ageDays: Int): Lms? = table(ind)[sex]?.getOrNull(ageDays)

    /** ponytail: sex not set averages the boys' and girls' results; set the child's sex for an exact answer. */
    private fun sexes(gender: String?): List<Int> = when (gender?.trim()?.lowercase()) {
        "male", "boy", "m", "ชาย" -> listOf(1)
        "female", "girl", "f", "หญิง" -> listOf(2)
        else -> listOf(1, 2)
    }

    /** WHO z-score, or null when the age is unknown / outside 0–5 years or the value is not positive. */
    fun zScore(ind: Indicator, value: Double?, ageDays: Int?, gender: String?): Double? {
        if (value == null || value <= 0.0 || ageDays == null || ageDays !in 0..MAX_AGE_DAYS) return null
        val zs = sexes(gender).mapNotNull { sex -> lms(ind, sex, ageDays)?.let { z(ind, value, it) } }
        return if (zs.isEmpty()) null else zs.average()
    }

    private fun z(ind: Indicator, x: Double, t: Lms): Double {
        val raw = if (t.l == 0.0) ln(x / t.m) / t.s else ((x / t.m).pow(t.l) - 1) / (t.l * t.s)
        if (ind == Indicator.HEIGHT || abs(raw) <= 3.0) return raw
        // WHO restricted application of the LMS method beyond ±3 SD (weight- and BMI-for-age).
        return if (raw > 0) {
            val sd3 = valueAt(t, 3.0)
            3 + (x - sd3) / (sd3 - valueAt(t, 2.0))
        } else {
            val sd3 = valueAt(t, -3.0)
            -3 + (x - sd3) / (valueAt(t, -2.0) - sd3)
        }
    }

    private fun valueAt(t: Lms, z: Double): Double =
        if (t.l == 0.0) t.m * exp(t.s * z) else t.m * (1 + t.l * t.s * z).pow(1 / t.l)

    /** Whole days from birth to the measurement date (today when null). Null when either date is unknown. */
    fun ageDaysAt(birthdate: String?, measuredAt: String? = null): Int? {
        val birth = birthdate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val on = measuredAt?.let { iso ->
            runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
                ?: runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
        } ?: LocalDate.now()
        return ChronoUnit.DAYS.between(birth, on).toInt().takeIf { it >= 0 }
    }

    fun monthsToDays(months: Int): Int = (months * DAYS_PER_MONTH).roundToInt()

    private fun outsideReference(ageDays: Int?): GrowthStatus? =
        if (ageDays != null && ageDays > MAX_AGE_DAYS)
            GrowthStatus("เกินช่วงอ้างอิง WHO (0–5 ปี)", "Beyond WHO 0–5 y reference", GREY)
        else null

    private fun implausible(z: Double) =
        GrowthStatus("ค่านี้ดูผิดปกติ ตรวจสอบตัวเลขที่บันทึก", "Unlikely value — check the entry", RED, z)

    fun weightStatus(weightKg: Double?, ageDays: Int?, gender: String?): GrowthStatus? {
        if (weightKg == null) return null
        outsideReference(ageDays)?.let { return it }
        val z = zScore(Indicator.WEIGHT, weightKg, ageDays, gender) ?: return null
        return when {
            z < -6 || z > 5 -> implausible(z)
            z < -3 -> GrowthStatus("น้ำหนักน้อยมาก ควรพบแพทย์", "Severely underweight — see a doctor", RED, z)
            z < -2 -> GrowthStatus("น้ำหนักน้อยกว่าเกณฑ์", "Underweight for age", ORANGE, z)
            z > 2 -> GrowthStatus("น้ำหนักมากกว่าเกณฑ์ตามวัย", "Above weight for age", ORANGE, z)
            else -> GrowthStatus("อยู่ในเกณฑ์ปกติ", "Normal range", GREEN, z)
        }
    }

    fun heightStatus(heightCm: Double?, ageDays: Int?, gender: String?): GrowthStatus? {
        if (heightCm == null) return null
        outsideReference(ageDays)?.let { return it }
        val z = zScore(Indicator.HEIGHT, heightCm, ageDays, gender) ?: return null
        return when {
            z < -6 || z > 6 -> implausible(z)
            z < -3 -> GrowthStatus("เตี้ยมาก ควรพบแพทย์", "Severely short for age — see a doctor", RED, z)
            z < -2 -> GrowthStatus("ค่อนข้างเตี้ย ต่ำกว่าเกณฑ์", "Short for age", ORANGE, z)
            z > 3 -> GrowthStatus("สูงกว่าเกณฑ์มาก", "Very tall for age", ORANGE, z)
            else -> GrowthStatus("อยู่ในเกณฑ์ปกติ", "Normal range", GREEN, z)
        }
    }

    /** "สมส่วน": BMI-for-age (WHO's weight-for-length/height counterpart that also accounts for age and sex). */
    fun weightForHeightStatus(weightKg: Double?, heightCm: Double?, ageDays: Int?, gender: String?): GrowthStatus? {
        if (weightKg == null || heightCm == null || heightCm <= 0.0) return null
        outsideReference(ageDays)?.let { return it }
        val bmi = weightKg / (heightCm / 100.0).pow(2)
        val z = zScore(Indicator.BMI, bmi, ageDays, gender) ?: return null
        return when {
            z < -5 || z > 5 -> implausible(z)
            z < -3 -> GrowthStatus("ผอมมาก ควรพบแพทย์", "Severely thin — see a doctor", RED, z)
            z < -2 -> GrowthStatus("ผอมกว่าเกณฑ์", "Thin for age", ORANGE, z)
            z > 3 -> GrowthStatus("อ้วน ควรปรึกษาแพทย์", "Obese — talk to a doctor", RED, z)
            z > 2 -> GrowthStatus("น้ำหนักเกิน", "Overweight", ORANGE, z)
            z > 1 -> GrowthStatus("เริ่มท้วม เฝ้าติดตาม", "Possible risk of overweight", ORANGE, z)
            else -> GrowthStatus("สมส่วน", "Proportional", GREEN, z)
        }
    }

    /**
     * The child's current weight and height statuses. Each value is judged at the date of the growth
     * record it came from; a value typed into the profile with no matching record is judged at today.
     * [ageDaysOverride] is for sample data with a fixed age (the app tour).
     */
    fun current(child: Child?, records: List<GrowthRecord>, ageDaysOverride: Int? = null): Pair<GrowthStatus?, GrowthStatus?> {
        child ?: return null to null
        fun measuredOn(value: Double?, pick: (GrowthRecord) -> Double?): String? =
            records.filter { pick(it) != null }.maxByOrNull { it.recordedAt ?: "" }?.takeIf { pick(it) == value }?.recordedAt
        val weightAge = ageDaysOverride ?: ageDaysAt(child.birthdate, measuredOn(child.weightKg) { it.weightKg })
        val heightAge = ageDaysOverride ?: ageDaysAt(child.birthdate, measuredOn(child.heightCm) { it.heightCm })
        return weightStatus(child.weightKg, weightAge, child.gender) to heightStatus(child.heightCm, heightAge, child.gender)
    }

    /** WHO normal range (−2 SD … +2 SD) at [ageDays] for the chart, or null outside 0–5 years. */
    fun band(ageDays: Int, isWeight: Boolean, gender: String?): Pair<Double, Double>? {
        if (ageDays !in 0..MAX_AGE_DAYS) return null
        val ind = if (isWeight) Indicator.WEIGHT else Indicator.HEIGHT
        val rows = sexes(gender).mapNotNull { lms(ind, it, ageDays) }
        if (rows.isEmpty()) return null
        return rows.map { valueAt(it, -2.0) }.average() to rows.map { valueAt(it, 2.0) }.average()
    }

    /** WHO median at [ageDays], or null outside 0–5 years. */
    fun median(ageDays: Int, isWeight: Boolean, gender: String?): Double? {
        if (ageDays !in 0..MAX_AGE_DAYS) return null
        val ind = if (isWeight) Indicator.WEIGHT else Indicator.HEIGHT
        return sexes(gender).mapNotNull { lms(ind, it, ageDays)?.m }.takeIf { it.isNotEmpty() }?.average()
    }
}
