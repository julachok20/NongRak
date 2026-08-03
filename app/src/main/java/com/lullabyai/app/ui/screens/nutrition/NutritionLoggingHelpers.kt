package com.lullabyai.app.ui.screens.nutrition

import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.util.TimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared meal / snack / age-badge helpers for nutrition logging screens (Stitch 28.nutrition). */

internal fun mealLabel(key: String, isThai: Boolean): String = when (key) {
    "breakfast" -> if (isThai) "มื้อเช้า" else "Breakfast"
    "lunch" -> if (isThai) "มื้อเที่ยง" else "Lunch"
    "dinner" -> if (isThai) "มื้อเย็น" else "Dinner"
    "snack" -> if (isThai) "ของว่าง" else "Snack"
    "snack_morning" -> if (isThai) "มื้อว่างเช้า" else "Morning snack"
    "snack_afternoon" -> if (isThai) "มื้อว่างบ่าย" else "Afternoon snack"
    else -> if (isThai) "อื่นๆ" else "Other"
}

/** Stitch splits snacks into morning/afternoon by clock time — derive without schema change. */
internal fun snackSlotForHour(hour: Int): String =
    if (hour < 12) "snack_morning" else "snack_afternoon"

internal fun displayMealLabel(food: FoodLog, isThai: Boolean): String {
    if (food.mealType != "snack") return mealLabel(food.mealType, isThai)
    val hour = TimeUtils.localTime(food.createdAt)
        ?.take(2)
        ?.toIntOrNull()
        ?: 15
    return mealLabel(snackSlotForHour(hour), isThai)
}

internal fun formatLogDateHeader(dateIso: String, isThai: Boolean): String {
    val d = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return dateIso
    return if (isThai) {
        val months = listOf(
            "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
            "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม"
        )
        "${d.dayOfMonth} ${months[d.monthValue - 1]} ${d.year + 543}"
    } else {
        d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
    }
}

/** Rough "เหมาะสมตามวัย" badge when intake is within ~70–120% of age target. */
internal fun ageAppropriateBadge(intake: Int, target: Int, isThai: Boolean): String? {
    if (target <= 0 || intake <= 0) return null
    val pct = intake.toFloat() / target
    return when {
        pct in 0.70f..1.20f -> if (isThai) "เหมาะสมตามวัย" else "Age-appropriate"
        pct < 0.70f -> if (isThai) "ต่ำกว่าเป้าหมาย" else "Below target"
        else -> if (isThai) "เกินเป้าหมาย" else "Above target"
    }
}

/** Compact nutrient tags (Fe / Ca / A …) from free-text nutrition notes or AI tags. */
internal fun nutrientTagSymbols(texts: List<String>): List<String> {
    val joined = texts.joinToString(" ").lowercase()
    val out = linkedSetOf<String>()
    if (joined.contains("iron") || joined.contains("เหล็ก")) out += "Fe"
    if (joined.contains("calcium") || joined.contains("แคลเซียม")) out += "Ca"
    if (joined.contains("vitamin a") || joined.contains("วิตามินเอ") ||
        joined.contains("beta") || joined.contains("เบต้า")
    ) out += "A"
    if (joined.contains("protein") || joined.contains("โปรตีน")) out += "P"
    if (joined.contains("omega") || joined.contains("โอเมก้า") || joined.contains("dha")) out += "Ω"
    if (joined.contains("zinc") || joined.contains("สังกะสี")) out += "Zn"
    // Prefer explicit AI short tags when present (e.g. "Fe", "Ca")
    texts.forEach { t ->
        val s = t.trim()
        if (s.length in 1..3 && s.all { it.isLetter() || it == 'Ω' }) out += s.replaceFirstChar { it.uppercase() }
    }
    return out.take(4)
}
