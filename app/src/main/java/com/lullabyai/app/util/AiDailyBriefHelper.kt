package com.lullabyai.app.util

import com.lullabyai.app.data.model.Child
import java.time.LocalDate

data class AiDailyBriefInfo(
    val title: String,
    val headline: String,
    val developmentalFocus: String,
    val recommendedAction: String,
    val sleepScheduleTip: String,
    val badge: String,
    val isThai: Boolean
)

object AiDailyBriefHelper {
    fun getZeroDataAiBrief(child: Child?, isThai: Boolean): AiDailyBriefInfo {
        val birthdateStr = child?.birthdate
        val ageMonths = if (!birthdateStr.isNullOrBlank()) {
            runCatching {
                val parts = birthdateStr.split("-")
                val by = parts[0].toInt(); val bm = parts[1].toInt()
                val now = LocalDate.now()
                val m = (now.year - by) * 12 + (now.monthValue - bm)
                m.coerceAtLeast(0)
            }.getOrDefault(3)
        } else 3

        val childName = child?.name?.takeIf { it.isNotBlank() } ?: if (isThai) "ลูกน้อย" else "your baby"

        return when {
            ageMonths <= 1 -> AiDailyBriefInfo(
                title = if (isThai) "AI Guidance • วัยแรกเกิด - 1 เดือน" else "AI Guidance • 0-1 Month",
                headline = if (isThai) "$childName วัยแรกเกิด — สัปดาห์นี้ควรเจออะไร" else "$childName (Newborn) — What to expect this week",
                developmentalFocus = if (isThai)
                    "มองเห็นใบหน้าในระยะ 20-30 ซม., ส่งเสียงร้องเมื่อต้องการความช่วยเหลือ, แขนขาขยับสะท้อนกลับตามสัญชาตญาณ (Moro/Grasp Reflex)"
                else "Focuses on faces 20-30cm away, communicates via crying, displays primitive reflexes (Moro & grasp).",
                recommendedAction = if (isThai)
                    "สบตาและพูดคุยด้วยน้ำเสียงอบอุ่น อุ้มแนบอก (Skin-to-skin) บ่อยๆ เพื่อสร้างความผูกพันและความอบอุ่นใจ"
                else "Make eye contact, talk gently, and practice skin-to-skin holding to nurture secure emotional bonding.",
                sleepScheduleTip = if (isThai)
                    "นอนหลับรวม 16-18 ชม./วัน ตื่นกินนมทุก 2-3 ชม. ยังไม่แยกกลางวัน-กลางคืน"
                else "Sleeps 16-18 hrs/day total, waking every 2-3 hrs to feed. Day/night rhythm is still forming.",
                badge = if (isThai) "วัยแรกเกิด • สร้างความผูกพัน" else "Newborn • Bonding Stage",
                isThai = isThai
            )
            ageMonths == 2 -> AiDailyBriefInfo(
                title = if (isThai) "AI Guidance • วัย 2 เดือน" else "AI Guidance • 2 Months",
                headline = if (isThai) "$childName วัย 2 เดือน — สัปดาห์นี้ควรเจออะไร" else "$childName (2 Months) — What to expect this week",
                developmentalFocus = if (isThai)
                    "เริ่มส่งยิ้มทักทายตอบรับ (Social Smile), ส่งเสียงอ้อแอ้ในลำคอ (Cooing), และเริ่มยกคอได้เล็กน้อยขณะนอนคว่ำ"
                else "Starting to smile in response to voices (Social Smile), cooing vowel sounds, lifting head slightly during tummy time.",
                recommendedAction = if (isThai)
                    "ฝึกนอนคว่ำ (Tummy Time) ครั้งละ 2-3 นาที บนเบาะนิ่ม และพูดคุยตอบรับทุกครั้งที่ลูกส่งเสียงอ้อแอ้"
                else "Engage in 2-3 mins of Tummy Time daily and respond enthusiastically to their coos.",
                sleepScheduleTip = if (isThai)
                    "นอนหลับรวม 14-16 ชม./วัน เริ่มนอนกลางคืนยาวขึ้น 4-5 ชม. ติดต่อกัน"
                else "Sleeps 14-16 hrs/day, beginning to consolidate night sleep to 4-5 hr stretches.",
                badge = if (isThai) "วัย 2 เดือน • รอยยิ้มแรก & อ้อแอ้" else "2 Months • First Smiles",
                isThai = isThai
            )
            ageMonths == 3 -> AiDailyBriefInfo(
                title = if (isThai) "AI Guidance • วัย 3 เดือน" else "AI Guidance • 3 Months",
                headline = if (isThai) "$childName วัย 3 เดือน — สัปดาห์นี้ควรเจออะไร" else "$childName (3 Months) — What to expect this week",
                developmentalFocus = if (isThai)
                    "ชันคอได้ 45-90 องศาขณะนอนคว่ำ, สบตาและหันตามเสียง, คว้าของเล่นใกล้ตัว, และหัวเราะส่งเสียงชอบใจ"
                else "Lifting head & chest 45-90° in tummy time, tracking moving objects, batting at toys, laughing aloud.",
                recommendedAction = if (isThai)
                    "ใช้ของเล่นเขย่ามีเสียงกรุ๊งกริ๊ง หรือโมบายล์สีสดใส เพื่อกระตุ้นการประสานสายตาและมือ"
                else "Provide lightweight rattles and colorful toys to encourage hand-eye coordination and reaching.",
                sleepScheduleTip = if (isThai)
                    "นอนหลับ 14-15 ชม./วัน เริ่มมีตารางการนอนกลางวัน 3 รอบ และนอนกลางคืนยาวขึ้น 5-6 ชม."
                else "Sleeps 14-15 hrs/day across ~3 daytime naps, with 5-6 hr stretches at night.",
                badge = if (isThai) "วัย 3 เดือน • ชันคอ & เล่นสนุก" else "3 Months • Neck Support",
                isThai = isThai
            )
            ageMonths in 4..5 -> AiDailyBriefInfo(
                title = if (isThai) "AI Guidance • วัย $ageMonths เดือน" else "AI Guidance • $ageMonths Months",
                headline = if (isThai) "$childName วัย $ageMonths เดือน — สัปดาห์นี้ควรเจออะไร" else "$childName ($ageMonths Months) — What to expect this week",
                developmentalFocus = if (isThai)
                    "เริ่มพลิกคว่ำพลิกหงายได้เอง, ใช้สองมือจับสิ่งของเข้าปากเพื่อสำรวจ, หัวเราะเสียงดังและชอบเล่นจ๊ะเอ๋"
                else "Rolling tummy-to-back, exploring objects with mouth, grasping with both hands, laughing with pure joy.",
                recommendedAction = if (isThai)
                    "จัดพื้นที่เบาะปลอดภัย ปราศจากของมีคมหรือชิ้นเล็ก ให้ลูกได้ฝึกกลิ้งตัวและเอื้อมหยิบของเล่น"
                else "Create a clear, cushioned floor space for rolling and reaching. Keep small objects away.",
                sleepScheduleTip = if (isThai)
                    "นอนหลับ 13-14 ชม./วัน อาจเจอช่วง Sleep Regression 4 เดือน แนะนำรักษากิจวัตรก่อนนอนให้สม่ำเสมอ"
                else "Sleeps 13-14 hrs/day. Maintain a calm, consistent bedtime routine to ease any 4-month sleep shifts.",
                badge = if (isThai) "วัย $ageMonths เดือน • เริ่มพลิกคว่ำ" else "$ageMonths Months • Rolling Stage",
                isThai = isThai
            )
            ageMonths in 6..8 -> AiDailyBriefInfo(
                title = if (isThai) "AI Guidance • วัย $ageMonths เดือน" else "AI Guidance • $ageMonths Months",
                headline = if (isThai) "$childName วัย $ageMonths เดือน — สัปดาห์นี้ควรเจออะไร" else "$childName ($ageMonths Months) — What to expect this week",
                developmentalFocus = if (isThai)
                    "นั่งได้มั่นคง, เริ่มคืบคลานสำรวจห้อง, เปลี่ยนมือถือของเล่น, และเริ่มพร้อมสำหรับอาหารเสริมตามวัย"
                else "Sitting steadily without support, beginning to crawl, transferring objects hand-to-hand, ready for solid foods.",
                recommendedAction = if (isThai)
                    "ให้อาหารเสริมบดละเอียด 1-2 มื้อ เน้นธาตุเหล็ก (ไข่แดง, ตับ, ผักใบเขียว) ควบคู่กับนมแม่/นมผง"
                else "Introduce nutrient-rich solid foods (iron-fortified puree, veggies) while keeping milk as main nutrition.",
                sleepScheduleTip = if (isThai)
                    "นอนกลางวัน 2 รอบ (เช้า-บ่าย) รวม 2.5-3 ชม. และนอนกลางคืน 10-11 ชม."
                else "2 daytime naps (morning and afternoon) total ~3 hrs, plus 10-11 hrs overnight.",
                badge = if (isThai) "วัย $ageMonths เดือน • เริ่มนั่ง & อาหารเสริม" else "$ageMonths Months • Sitting & Solids",
                isThai = isThai
            )
            ageMonths in 9..11 -> AiDailyBriefInfo(
                title = if (isThai) "AI Guidance • วัย $ageMonths เดือน" else "AI Guidance • $ageMonths Months",
                headline = if (isThai) "$childName วัย $ageMonths เดือน — สัปดาห์นี้ควรเจออะไร" else "$childName ($ageMonths Months) — What to expect this week",
                developmentalFocus = if (isThai)
                    "เกาะยืน, ใช้นิ้วโป้งและนิ้วชี้หยิบของชิ้นเล็ก (Pincer Grasp), โบกมือบ๊ายบาย, และจำชื่อตัวเองได้"
                else "Pulling to stand, developing fine pincer grasp, waving bye-bye, responding to own name.",
                recommendedAction = if (isThai)
                    "ฝึกใช้นิ้วหยิบ Finger Food ชิ้นนิ่ม และเล่นเกมซ่อนหาของเพื่อส่งเสริมความเข้าใจเรื่องวัตถุ (Object Permanence)"
                else "Offer soft finger foods for pincer grasp practice and play peek-a-boo / hiding games.",
                sleepScheduleTip = if (isThai)
                    "นอนหลับ 12-14 ชม./วัน นอนกลางวัน 2 รอบ ควรระวังช่วงกังวลการแยกจาก (Separation Anxiety)"
                else "Sleeps 12-14 hrs/day across 2 naps. Offer soothing comfort during separation anxiety peaks.",
                badge = if (isThai) "วัย $ageMonths เดือน • เกาะยืน & หยิบจับ" else "$ageMonths Months • Standing Up",
                isThai = isThai
            )
            ageMonths in 12..17 -> AiDailyBriefInfo(
                title = if (isThai) "AI Guidance • วัย $ageMonths เดือน" else "AI Guidance • $ageMonths Months",
                headline = if (isThai) "$childName วัย $ageMonths เดือน — สัปดาห์นี้ควรเจออะไร" else "$childName ($ageMonths Months) — What to expect this week",
                developmentalFocus = if (isThai)
                    "เริ่มก้าวเดินก้าวแรก, พูดคำเดี่ยวมีความหมาย (หม่ำๆ, แม่, พ่อ), ดื่มน้ำจากแก้ว, และชี้บอกสิ่งที่ต้องการ"
                else "First independent steps, single meaningful words, drinking from a cup, pointing to show desire.",
                recommendedAction = if (isThai)
                    "จัดสภาพแวดล้อมให้เดินปลอดภัย ปูพื้นกันลื่น อ่านหนังสือนิทานภาพชี้ชวนคุยทุกวัน"
                else "Childproof walking areas and read illustrated picture books together daily.",
                sleepScheduleTip = if (isThai)
                    "นอนหลับรวม 11-14 ชม./วัน เริ่มเปลี่ยนจากการนอนกลางวัน 2 รอบเป็น 1 รอบยาว"
                else "Sleeps 11-14 hrs/day. Starting the gradual transition from 2 naps to 1 midday nap.",
                badge = if (isThai) "วัย 1 ขวบขึ้นไป • ก้าวเดินแรก & พูดคำแรก" else "1+ Year • First Steps & Words",
                isThai = isThai
            )
            else -> AiDailyBriefInfo(
                title = if (isThai) "AI Guidance • วัย 1.5 - 3 ปี" else "AI Guidance • Toddler Stage",
                headline = if (isThai) "$childName วัยหัดเดิน — สัปดาห์นี้ควรเจออะไร" else "$childName (Toddler) — What to expect this week",
                developmentalFocus = if (isThai)
                    "วิ่งเล่นคล่องตัว, พูดเป็นวลี 2 คำ, เลียนแบบพฤติกรรมผู้ใหญ่, และเริ่มมีความเป็นตัวของตัวเอง (Autonomy)"
                else "Running smoothly, combining 2 words, imitating adult chores, showing blooming independence.",
                recommendedAction = if (isThai)
                    "เปิดโอกาสให้ลูกได้เลือกสิ่งของง่ายๆ ด้วยตัวเอง และส่งเสริมการเล่นบทบาทสมมติเพื่อพัฒนาจินตนาการ"
                else "Offer simple choices (e.g. which shirt to wear) and encourage imaginative pretend play.",
                sleepScheduleTip = if (isThai)
                    "นอนหลับ 11-13 ชม./วัน นอนกลางวัน 1 รอบ (1.5-2 ชม.) และนอนกลางคืน 10-11 ชม."
                else "Sleeps 11-13 hrs/day with 1 midday nap (~2 hrs) and restful nighttime sleep.",
                badge = if (isThai) "วัยเตาะแตะ • เสริมสร้างทักษะ & ความมั่นใจ" else "Toddler • Exploration & Growth",
                isThai = isThai
            )
        }
    }
}
