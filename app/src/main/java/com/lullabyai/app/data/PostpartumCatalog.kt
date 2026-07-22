package com.lullabyai.app.data

/**
 * Parent (not child) monthly postpartum PHYSICAL-recovery self-assessment — separate axis from
 * MentalHealthCatalog (mood/EPDS). Covers bleeding, wound/incision healing, fatigue, and infection
 * warning signs. 8 items, each answer carries an explicit score (0-3).
 *
 * IMPORTANT: this is a lightweight in-app screening aid, not a validated clinical instrument like
 * EPDS — phrasing/scoring is original, not adapted from a published scale. Its purpose is to catch
 * obvious red flags (heavy bleeding, fever) and nudge toward a doctor, not to replace postpartum
 * medical follow-up visits.
 */
object PostpartumCatalog {

    data class Option(val labelTh: String, val labelEn: String, val score: Int)
    data class Question(val textTh: String, val textEn: String, val options: List<Option>)

    /** Items where the WORST option (score 3) is a medical red flag (postpartum hemorrhage /
     *  infection signs) — any of these at max score triggers an immediate "see a doctor" message,
     *  same mechanism as MentalHealthCatalog.SELF_HARM_INDEX. */
    val URGENT_FLAG_INDICES = setOf(0, 2)

    val questions: List<Question> = listOf(
        Question(
            "ในช่วง 7 วันที่ผ่านมา เลือดที่ออกหลังคลอด (น้ำคาวปลา) เป็นอย่างไร?",
            "In the past 7 days, how has your postpartum bleeding (lochia) been?",
            listOf(Option("ลดลงเรื่อยๆ ตามปกติ", "Steadily decreasing, as expected", 0), Option("ยังพอมีอยู่ ไม่มากขึ้น", "Still present but not increasing", 1),
                Option("มากขึ้นกว่าวันก่อน", "Increasing compared to previous days", 2), Option("ต้องเปลี่ยนผ้าอนามัยทุก 1 ชม. หรือมีลิ่มเลือดก้อนใหญ่", "Soaking a pad within 1 hour, or passing large clots", 3))
        ),
        Question(
            "แผล (ฝีเย็บหรือแผลผ่าคลอด) เจ็บมากน้อยเพียงใด?",
            "How much pain from your wound (perineal tear or C-section incision)?",
            listOf(Option("ไม่เจ็บหรือเจ็บเล็กน้อย", "None or mild", 0), Option("เจ็บปานกลาง พอรับไหว", "Moderate, manageable", 1),
                Option("เจ็บมาก ต้องใช้ยาแก้ปวด", "Significant, needs pain medication", 2), Option("เจ็บรุนแรงขึ้นเรื่อยๆ หรือแผลบวมแดงมีหนอง", "Worsening pain, or wound is swollen/red/oozing", 3))
        ),
        Question(
            "มีไข้หรือหนาวสั่นหรือไม่ในช่วงนี้?",
            "Have you had a fever or chills recently?",
            listOf(Option("ไม่มี", "No", 0), Option("รู้สึกตัวร้อนเล็กน้อยแต่ไม่ได้วัดไข้", "Felt slightly warm but didn't measure", 1),
                Option("มีไข้ต่ำๆ (ต่ำกว่า 38°C)", "Low-grade fever (under 38°C)", 2), Option("มีไข้สูง (38°C ขึ้นไป) หรือหนาวสั่น", "High fever (38°C or above) or chills", 3))
        ),
        Question(
            "การให้นมลูกเป็นอย่างไรบ้าง?",
            "How has breastfeeding been going?",
            listOf(Option("ราบรื่นดี", "Going smoothly", 0), Option("มีเจ็บบ้างแต่พอรับไหว", "Some discomfort, manageable", 1),
                Option("เจ็บมากหรือกังวลเรื่องน้ำนม", "Significant pain, or worried about milk supply", 2), Option("เต้านมบวมแดงเจ็บมาก หรือมีไข้ร่วมด้วย", "Breast is red/swollen/very painful, possibly with fever", 3))
        ),
        Question(
            "ระดับพลังงาน/ความเหนื่อยล้าของคุณเป็นอย่างไร?",
            "How would you rate your energy/fatigue level?",
            listOf(Option("พอมีแรงทำกิจวัตรได้", "Enough energy for daily routines", 0), Option("เหนื่อยกว่าปกติแต่ยังไหว", "More tired than usual but managing", 1),
                Option("เหนื่อยมาก พักผ่อนเท่าไหร่ก็ไม่พอ", "Very tired, rest doesn't seem to help", 2), Option("หมดแรงจนทำกิจวัตรพื้นฐานลำบาก", "Exhausted to the point daily tasks are hard", 3))
        ),
        Question(
            "มีอาการหน้ามืด วิงเวียน หรือใจสั่นหรือไม่?",
            "Any dizziness, lightheadedness, or heart palpitations?",
            listOf(Option("ไม่มี", "No", 0), Option("มีเล็กน้อยเป็นครั้งคราว", "Occasionally, mild", 1),
                Option("มีบ่อยขึ้น", "More frequent", 2), Option("เป็นบ่อยจนกระทบการใช้ชีวิต", "Frequent enough to interfere with daily life", 3))
        ),
        Question(
            "ขาหรือบริเวณแผลมีอาการบวม แดง หรือปวดร้อนหรือไม่ (สัญญาณลิ่มเลือด)?",
            "Any swelling, redness, or warmth in your legs or around the wound (possible clot warning sign)?",
            listOf(Option("ไม่มี", "No", 0), Option("บวมเล็กน้อยตามปกติหลังคลอด", "Mild swelling, typical after birth", 1),
                Option("บวมแดงข้างเดียวชัดเจน", "Noticeable one-sided swelling/redness", 2), Option("ขาบวมแดงร้อนและเจ็บมากข้างเดียว", "One leg significantly swollen, red, warm, and painful", 3))
        ),
        Question(
            "โดยรวมคุณรู้สึกว่าร่างกายฟื้นตัวเป็นอย่างไร?",
            "Overall, how do you feel your body is recovering?",
            listOf(Option("ดีขึ้นตามลำดับ", "Improving steadily", 0), Option("ทรงตัว ไม่ดีขึ้นไม่แย่ลง", "Stable, neither better nor worse", 1),
                Option("ฟื้นตัวช้ากว่าที่คิด", "Recovering slower than expected", 2), Option("รู้สึกแย่ลงเรื่อยๆ", "Feeling progressively worse", 3))
        )
    )

    fun riskTier(totalScore: Int): String = when {
        totalScore >= 16 -> "high"
        totalScore >= 8 -> "moderate"
        else -> "low"
    }

    fun riskTierLabel(tier: String, isThai: Boolean): String = when (tier) {
        "high" -> if (isThai) "ควรพบแพทย์เร็วๆ นี้" else "See a doctor soon"
        "moderate" -> if (isThai) "ควรติดตามอาการ" else "Worth monitoring"
        else -> if (isThai) "ฟื้นตัวได้ดี" else "Recovering well"
    }
}
