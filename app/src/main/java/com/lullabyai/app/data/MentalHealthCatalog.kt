package com.lullabyai.app.data

/**
 * Parent (not child) monthly mental-health self-assessment, modeled on the Edinburgh Postnatal
 * Depression Scale (EPDS) — the tool กรมสุขภาพจิต (Thailand's Dept. of Mental Health) endorses for
 * perinatal/parental screening in maternal-child health programs. 10 items, each answer carries an
 * explicit score (0-3) so the scoring is correct regardless of on-screen answer order.
 *
 * IMPORTANT: phrasing below is adapted from the standard English EPDS, not pasted from an official
 * DMH source document — verify against the official DMH Thai translation before relying on exact
 * wording for clinical use. Scoring logic (which items are reverse-scored, cutoffs) follows the
 * standard published EPDS.
 */
object MentalHealthCatalog {

    data class Option(val labelTh: String, val labelEn: String, val score: Int)
    data class Question(val textTh: String, val textEn: String, val options: List<Option>)

    /** Item 10 (self-harm) index — any score > 0 here triggers an immediate safety message. */
    const val SELF_HARM_INDEX = 9

    val questions: List<Question> = listOf(
        Question(
            "ในช่วง 7 วันที่ผ่านมา ฉันสามารถหัวเราะและมองเห็นเรื่องขำขันของสิ่งต่างๆ ได้มากน้อยเพียงใด?",
            "In the past 7 days, I have been able to laugh and see the funny side of things",
            listOf(Option("ได้เหมือนเดิมทุกอย่าง", "As much as I always could", 0), Option("ได้น้อยลงกว่าเดิม", "Not quite so much now", 1),
                Option("ได้น้อยลงชัดเจน", "Definitely not so much now", 2), Option("ไม่ได้เลย", "Not at all", 3))
        ),
        Question(
            "ฉันรู้สึกตั้งตารอที่จะทำสิ่งต่างๆ ด้วยความเพลิดเพลินมากน้อยเพียงใด?",
            "I have looked forward with enjoyment to things",
            listOf(Option("เท่าเดิมเสมอ", "As much as I ever did", 0), Option("น้อยกว่าเดิมเล็กน้อย", "Rather less than I used to", 1),
                Option("น้อยกว่าเดิมชัดเจน", "Definitely less than I used to", 2), Option("แทบไม่รู้สึกเลย", "Hardly at all", 3))
        ),
        Question(
            "ฉันโทษตัวเองโดยไม่จำเป็นเมื่อมีอะไรผิดพลาด",
            "I have blamed myself unnecessarily when things went wrong",
            listOf(Option("ไม่เลย", "No, never", 0), Option("ไม่บ่อยนัก", "Not very often", 1),
                Option("บ่อยพอสมควร", "Yes, some of the time", 2), Option("เกือบตลอดเวลา", "Yes, most of the time", 3))
        ),
        Question(
            "ฉันรู้สึกกังวลหรือวิตกโดยไม่มีเหตุผลชัดเจน",
            "I have been anxious or worried for no good reason",
            listOf(Option("ไม่เลย", "No, not at all", 0), Option("แทบไม่รู้สึก", "Hardly ever", 1),
                Option("รู้สึกบ้างเป็นบางครั้ง", "Yes, sometimes", 2), Option("รู้สึกบ่อยมาก", "Yes, very often", 3))
        ),
        Question(
            "ฉันรู้สึกกลัวหรือตื่นตระหนกโดยไม่มีเหตุผลชัดเจน",
            "I have felt scared or panicky for no very good reason",
            listOf(Option("ไม่เลย", "No, not at all", 0), Option("ไม่ค่อยรู้สึก", "No, not much", 1),
                Option("รู้สึกบ้างเป็นบางครั้ง", "Yes, sometimes", 2), Option("รู้สึกค่อนข้างมาก", "Yes, quite a lot", 3))
        ),
        Question(
            "เรื่องต่างๆ รุมเร้าจนฉันรับมือไม่ไหว",
            "Things have been getting on top of me",
            listOf(Option("รับมือได้ดีเหมือนเดิม", "No, I have been coping as well as ever", 0), Option("ส่วนใหญ่ยังรับมือได้ดี", "No, most of the time I have coped quite well", 1),
                Option("บางครั้งรับมือได้ไม่ดีเท่าที่ควร", "Yes, sometimes I haven't been coping as well as usual", 2), Option("ส่วนใหญ่รับมือไม่ไหวเลย", "Yes, most of the time I haven't been able to cope", 3))
        ),
        Question(
            "ฉันไม่มีความสุขจนนอนหลับยาก",
            "I have been so unhappy that I have had difficulty sleeping",
            listOf(Option("ไม่เลย", "No, not at all", 0), Option("ไม่บ่อยนัก", "Not very often", 1),
                Option("บ่อยพอสมควร", "Yes, sometimes", 2), Option("เกือบตลอดเวลา", "Yes, most of the time", 3))
        ),
        Question(
            "ฉันรู้สึกเศร้าหรือทุกข์ใจ",
            "I have felt sad or miserable",
            listOf(Option("ไม่เลย", "No, not at all", 0), Option("ไม่บ่อยนัก", "Not very often", 1),
                Option("ค่อนข้างบ่อย", "Yes, quite often", 2), Option("เกือบตลอดเวลา", "Yes, most of the time", 3))
        ),
        Question(
            "ฉันไม่มีความสุขจนร้องไห้",
            "I have been so unhappy that I have been crying",
            listOf(Option("ไม่เลย", "No, never", 0), Option("นานๆ ครั้ง", "Only occasionally", 1),
                Option("ค่อนข้างบ่อย", "Yes, quite often", 2), Option("เกือบตลอดเวลา", "Yes, most of the time", 3))
        ),
        Question(
            "ความคิดที่จะทำร้ายตัวเองผุดขึ้นมาในใจฉัน",
            "The thought of harming myself has occurred to me",
            listOf(Option("ไม่เคยเลย", "Never", 0), Option("แทบไม่เคย", "Hardly ever", 1),
                Option("เป็นบางครั้ง", "Sometimes", 2), Option("ค่อนข้างบ่อย", "Yes, quite often", 3))
        )
    )

    fun riskTier(totalScore: Int): String = when {
        totalScore >= 20 -> "high"
        totalScore >= 13 -> "moderate"
        else -> "low"
    }

    fun riskTierLabel(tier: String, isThai: Boolean): String = when (tier) {
        "high" -> if (isThai) "ความเสี่ยงสูง" else "High risk"
        "moderate" -> if (isThai) "ความเสี่ยงปานกลาง" else "Moderate risk"
        else -> if (isThai) "ความเสี่ยงต่ำ" else "Low risk"
    }
}
