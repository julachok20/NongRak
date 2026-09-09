package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.AiArticleTip
import com.lullabyai.app.data.model.CommunityComment
import com.lullabyai.app.data.model.CommunityCommentDto
import com.lullabyai.app.data.model.CommunityCommentLikeDto
import com.lullabyai.app.data.model.CommunityPost
import com.lullabyai.app.data.model.CommunityPostDto
import com.lullabyai.app.data.model.CommunityPostLikeDto
import com.lullabyai.app.data.model.ProfessionalAdviceBlog
import com.lullabyai.app.data.model.formatRelativeTime
import com.lullabyai.app.data.model.parseIsoToEpochMillis
import com.lullabyai.app.data.offline.OfflineCache
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class AiSafetyResult(
    val isSafe: Boolean,
    val warningMessage: String? = null,
    val suggestedRevision: String? = null
)

class CommunityRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val _posts = MutableStateFlow<List<CommunityPost>>(emptyList())
    val posts: Flow<List<CommunityPost>> = _posts.asStateFlow()

    /**
     * Last failed community write, as a stable key for [communityWriteError] to translate.
     *
     * Every write here updates [_posts] optimistically and then persists in the background, so a
     * rejected write leaves the screen showing something the server does not have. Until now those
     * rejections went into a bare `runCatching { }` and vanished. The database enforces real rules
     * - author-only edits, a 48h post window, a 24h comment window - and a rule the user cannot
     * see fire is indistinguishable from a bug.
     */
    private val _writeError = MutableStateFlow<String?>(null)
    val writeError: Flow<String?> = _writeError.asStateFlow()

    fun clearWriteError() { _writeError.value = null }

    /** Records a failed write. Raw messages are never kept: they can carry ids and tokens. */
    private fun reportWriteFailure(e: Throwable) {
        val raw = (e.message ?: "").lowercase()
        _writeError.value = when {
            "post_edit_window_expired" in raw -> ERR_POST_EDIT_WINDOW
            "comment_edit_window_expired" in raw -> ERR_COMMENT_EDIT_WINDOW
            "row-level security" in raw || "violates row-level" in raw -> ERR_NOT_YOURS
            else -> ERR_WRITE_FAILED
        }
    }

    private val _blogs = MutableStateFlow<List<ProfessionalAdviceBlog>>(
        listOf(
            ProfessionalAdviceBlog(
                id = "blog-1",
                title = "แนวทางการเริ่มอาหารเสริมตามวัย (Complementary Feeding) 6-12 เดือน อย่างถูกต้องและปลอดภัย",
                subtitle = "คู่มือโภชนาการสำหรับเด็กเล็กโดย AI น้องรัก",
                summary = "หลักการเริ่มอาหารมื้อแรก สัญญาณความพร้อมของลูก อาหารที่ควรหลีกเลี่ยง และการป้องกันอาการแพ้อาหารอย่างถูกวิธี",
                category = "โภชนาการ",
                doctorName = "AI น้องรัก",
                doctorTitle = "AI ผู้เชี่ยวชาญโภชนาการและอาหารเด็กเล็ก",
                doctorHospital = "ระบบการดูแลอัจฉริยะ NongRak AI System (มาตรฐาน AAP & กรมอนามัย)",
                readTimeMinutes = 5,
                publishedDate = "2026-08-20",
                fullContent = """
                    การเริ่มอาหารเสริมตามวัย (Complementary Foods) มีความสำคัญอย่างยิ่งต่อการเจริญเติบโต พัฒนาการทางสมอง และการสร้างพฤติกรรมการกินที่ดีตลอดชีวิตของลูกน้อย
                    
                    1. สัญญาณความพร้อมของลูก (เมื่ออายุครบ 6 เดือน)
                    • สามารถชันคอและนั่งทรงตัวได้ดีเมื่อมีคนพยุง
                    • มีความสนใจอาหารที่ผู้ใหญ่รับประทาน เช่น มองตาม อ้าปากรับ
                    • รีเฟล็กซ์การใช้ลิ้นดันอาหารออก (Extrusion Reflex) เริ่มลดลง
                    
                    2. เมนูอาหารมื้อแรกและสารอาหารที่จำเป็น
                    • ธาตุเหล็ก (Iron): สำคัญมากที่สุดเพราะธาตุเหล็กสำรองจากนมแม่จะเริ่มหมดลงเมื่ออายุ 6 เดือน ควรให้ข้าวบดผสมตับไก่/ตับหมูบดละเอียด ไข่แดงต้มสุก หรือเนื้อปลา
                    • ผักและผลไม้: ฟักทองนึ่ง แครอทนึ่ง ตำลึง บดเนียนละเอียด
                    • หลีกเลี่ยงการปรุงรส: ห้ามเติมน้ำตาล เกลือ น้ำปลา หรือซีอิ๊วในอาหารเด็กก่อน 1 ขวบ เพื่อป้องกันการทำงานหนักของไตและพฤติกรรมติดรสหวาน/เค็ม
                    
                    3. ข้อควรระวังที่สำคัญ
                    • ห้ามให้น้ำผึ้งเด็ดขาดแก่ทารกอายุต่ำกว่า 1 ปี เนื่องจากเสี่ยงต่อเชื้อ Clostridium botulinum ที่สร้างสารพิษอันตรายถึงชีวิต
                    • การทดสอบภูมิแพ้: ควรเริ่มอาหารชนิดใหม่ทีละอย่าง ติดต่อกัน 3-5 วัน เพื่อสังเกตอาการแพ้ เช่น ผื่น ลมพิษ ถ่ายเหลว หรืออาเจียน
                """.trimIndent(),
                keyTakeaways = listOf(
                    "เริ่มอาหารมื้อแรกที่อายุ 6 เดือน ควบคู่กับนมแม่อย่างต่อเนื่อง",
                    "เน้นอาหารที่มีธาตุเหล็กสูง เช่น ตับบด เนื้อสัตว์บด ไข่แดงสุก",
                    "งดการปรุงรสทุกชนิด (เกลือ น้ำตาล ผงชูรส) ก่อนอายุครบ 1 ขวบ",
                    "ห้ามให้น้ำผึ้งแก่เด็กอายุต่ำกว่า 1 ปีโดยเด็ดขาด"
                ),
                faqs = listOf(
                    "ลูกกินน้อยมากในมื้อแรกๆ ปกติไหม?" to "ปกติอย่างยิ่งครับ ในช่วง 6-7 เดือน นมยังคงเป็นอาหารหลัก อาหารเสริมคือการฝึกการกลืนและการรับรู้รสชาติ ให้เน้นความสนุกและไม่บังคับลูกครับ",
                    "ควรเริ่มแบบบดป้อน (Puree) หรือหยิบกินเอง (BLW)?" to "สามารถผสมผสานได้ครับ เริ่มจากบดหยาบพอประมาณ และเริ่มให้อาหารทรงแท่งนุ่มๆ (Finger Foods) ช่วง 8-9 เดือนเพื่อฝึกกล้ามเนื้อมัดเล็ก"
                ),
                likesCount = 284,
                isLiked = false
            ),
            ProfessionalAdviceBlog(
                id = "blog-2",
                title = "เทคนิคการฝึกลูกนอนยาว (Gentle Sleep Training) และการจัดสภาพแวดล้อมที่ปลอดภัย",
                subtitle = "เข้าใจวงจรการนอนของทารกและวิธีช่วยให้ลูกหลับต่อได้เองโดย AI น้องรัก",
                summary = "วิธีสร้าง Bedtime Routine การสังเกต Sleep Cues และการจัดที่นอนตามมาตรฐานสากลเพื่อป้องกัน SIDS",
                category = "การนอน",
                doctorName = "AI น้องรัก",
                doctorTitle = "AI ผู้เชี่ยวชาญการนอนหลับและพฤติกรรมเด็ก",
                doctorHospital = "ระบบการดูแลอัจฉริยะ NongRak AI System (มาตรฐานเวชศาสตร์การนอนเด็ก)",
                readTimeMinutes = 4,
                publishedDate = "2026-08-18",
                fullContent = """
                    การนอนหลับที่มีคุณภาพส่งผลโดยตรงต่อการหลั่งโกรทฮอร์โมน (Growth Hormone) และการพัฒนาของระบบประสาทในสมองของเด็ก
                    
                    1. ทำความเข้าใจวงจรการนอนของเด็ก (Sleep Cycles)
                    วงจรการนอนของทารกสั้นเพียง 45-50 นาที ทำให้เด็กมักตื่นตื้นเมื่อเปลี่ยนรอบการนอน หากเด็กได้รับการฝึกฝนให้หล่อมตัวเองสู่วงจรนอนถัดไปได้ ลูกจะสามารถนอนยาวได้ 6-8 ชั่วโมงต่อเนื่อง
                    
                    2. การสร้างกิจวัตรก่อนนอน (Bedtime Routine) 4 ขั้นตอนสม่ำเสมอ
                    • อาบน้ำอุ่น เช็ดตัว ทาโลชั่น
                    • สวมชุดนอนที่ระบายอากาศได้ดี
                    • หรี่ไฟ อ่านนิทานหรือเปิด White Noise / เพลงกล่อมนอนเบาๆ
                    • วางลูกลงบนที่นอนขณะที่ยังตื่นอยู่แต่ง่วงนอน (Drowsy but Awake)
                    
                    3. กฎความปลอดภัย Safe Sleep (ป้องกัน SIDS)
                    • ทารกต้องนอนหงายเสมอ (Back to Sleep) บนเบาะนอนที่แน่นพอดี
                    • ที่นอนต้องไม่มีหมอน หมอนข้าง ตุ๊กตา หรือผ้าห่มหนาที่อาจปิดกั้นทางเดินหายใจ
                    • อุณหภูมิห้องที่เหมาะสมคือ 24-26 องศาเซลเซียส
                """.trimIndent(),
                keyTakeaways = listOf(
                    "วางลูกลงที่นอนขณะ Drowsy but Awake เพื่อฝึกให้หลับต่อได้ด้วยตัวเอง",
                    "รักษาเวลากิจวัตรก่อนนอน (Bedtime Routine) ให้คงที่ทุกวัน",
                    "จัดที่นอนให้โล่ง ไม่มีตุ๊กตา หมอน หรือผ้าห่มหนา เพื่อความปลอดภัยสูงสุด"
                ),
                faqs = listOf(
                    "ลูกตื่นมาร้องกลางดึก ควรให้นมทันทีไหม?" to "หากไม่ใช่เวลาหิวตามวัย ให้ลองรอ 2-3 นาที แล้วเข้าไปตบก้นเบาๆ พูดปลอบด้วยเสียงนุ่มนวลโดยไม่ต้องอุ้มขึ้นมาทันที เพื่อให้ลูกเรียนรู้การกล่อมตัวเอง"
                ),
                likesCount = 315,
                isLiked = true
            ),
            ProfessionalAdviceBlog(
                id = "blog-3",
                title = "สังเกตพัฒนาการและการสื่อสารวัย 0-2 ปี: Red Flags ที่พ่อแม่ไม่ควรมองข้าม",
                subtitle = "แนวทางการประเมินพัฒนาการ 4 ด้านหลักโดย AI น้องรัก",
                summary = "หมุดหมายสำคัญด้านการพูด การสบตา การเคลื่อนไหว และสัญญาณเตือนที่ควรรีบปรึกษาผู้เชี่ยวชาญ",
                category = "พัฒนาการ",
                doctorName = "AI น้องรัก",
                doctorTitle = "AI ผู้เชี่ยวชาญพัฒนาการและการสื่อสารเด็กปฐมวัย",
                doctorHospital = "ระบบการดูแลอัจฉริยะ NongRak AI System (มาตรฐาน DSPM & WHO)",
                readTimeMinutes = 6,
                publishedDate = "2026-08-15",
                fullContent = """
                    พัฒนาการของเด็กแบ่งออกเป็น 4 ด้านหลัก ได้แก่ กล้ามเนื้อมัดใหญ่ กล้ามเนื้อมัดเล็ก ภาษาและการสื่อสาร และสังคมและอารมณ์
                    
                    1. หมุดหมายสำคัญตามช่วงวัย
                    • 3 เดือน: สบตา ยิ้มตอบ ชันคอ 45 องศาขณะนอนคว่ำ
                    • 6 เดือน: พลิกคว่ำหงายได้เอง ส่งเสียงอ้อแอ้ (Babbling) เอื้อมมือหยิบของ
                    • 9 เดือน: นั่งได้มั่นคงโดยไม่ต้องพยุง ส่งเสียงเลียนแบบ รู้จักคำว่า 'ไม่'
                    • 12 เดือน: เกาะยืนหรือก้าวเดิน พูดคำเดี่ยวที่มีความหมายได้ 1-2 คำ (เช่น หม่ำๆ, แม่)
                    • 18 เดือน: เดินได้คล่อง ชี้สิ่งที่ต้องการ พูดคำศัพท์ได้อย่างน้อย 10-20 คำ
                    
                    2. สัญญาณเตือน (Red Flags) ที่ควรสังเกต
                    • อายุ 9 เดือน: ยังไม่สบตา ไม่ยิ้มตอบ หรือไม่หันตามเสียงเรียกชื่อ
                    • อายุ 12 เดือน: ยังไม่ส่งเสียงพยัญชนะ ไม่ชี้นิ้วบอกความต้องการ
                    • อายุ 18 เดือน: ยังไม่พูดคำเดี่ยวที่มีความหมาย ไม่เลียนแบบท่าทาง
                    • อายุใดก็ตาม: พัฒนาการที่เคยทำได้กลับถดถอยลง (Developmental Regression)
                    
                    3. ข้อแนะนำการจำกัดหน้าจอ (Screen Time)
                    สมาคมกุมารแพทย์แห่งประเทศไทยและ WHO แนะนำ 'งดหน้าจอทุกชนิด' (ทีวี มือถือ แท็บเล็ต) ในเด็กอายุต่ำกว่า 2 ขวบ เพื่อป้องกันภาวะออทิสติกเทียมและภาษาพูดล่าช้า
                """.trimIndent(),
                keyTakeaways = listOf(
                    "งดหน้าจอทุกชนิดในเด็กอายุต่ำกว่า 2 ปี เพื่อเสริมสร้างภาษาและการสบตา",
                    "หากอายุ 12 เดือนยังไม่ชี้นิ้วหรือส่งเสียงเรียก ควรรีบตรวจประเมิน",
                    "การเล่นแบบโต้ตอบ (Interactive Play) กับพ่อแม่คือการกระตุ้นพัฒนาการที่ดีที่สุด"
                ),
                faqs = listOf(
                    "เปิดเพลงเด็กใน YouTube ให้ลูกฟังได้ไหม?" to "แนะนำให้พ่อแม่เป็นผู้ร้องเพลง เล่นจ๊ะเอ๋ หรืออ่านนิทานให้ลูกฟังด้วยตัวเอง จะช่วยส่งเสริมสมองและสายสัมพันธ์ได้ดีกว่าหน้าจอดิจิทัลหลายเท่าครับ"
                ),
                likesCount = 420,
                isLiked = false
            ),
            ProfessionalAdviceBlog(
                id = "blog-4",
                title = "การดูแลเมื่อลูกมีไข้สูงและการรับมือภาวะชักจากไข้ (Febrile Seizures)",
                subtitle = "คู่มือปฐมพยาบาลฉุกเฉินและวิธีเช็ดตัวลดไข้ที่ถูกต้องโดย AI น้องรัก",
                summary = "วิธีคำนวณขนาดยาลดไข้พาราเซตามอล อุณหภูมิน้ำเช็ดตัว และขั้นตอนปฏิบัติเมื่อลูกมีอาการชัก",
                category = "สุขภาพ",
                doctorName = "AI น้องรัก",
                doctorTitle = "AI ผู้ช่วยการดูแลสุขภาพและการปฐมพยาบาลฉุกเฉิน",
                doctorHospital = "ระบบการดูแลอัจฉริยะ NongRak AI System (มาตรฐานการแพทย์ฉุกเฉิน)",
                readTimeMinutes = 5,
                publishedDate = "2026-08-10",
                fullContent = """
                    ไข้เป็นกลไกตามธรรมชาติของร่างกายในการต่อสู้กับเชื้อโรค สิ่งสำคัญที่สุดคือการเฝ้าระวังไม่ให้อุณหภูมิสูงเกิน 38.5 องศาเซลเซียส เพื่อลดความเสี่ยงการชักจากไข้
                    
                    1. วิธีเช็ดตัวลดไข้ที่ถูกต้อง (Tepid Sponging)
                    • ใช้น้ำอุ่นหรือน้ำอุณหภูมิห้อง (ห้ามใช้น้ำแข็งหรือแอลกอฮอล์เด็ดขาด)
                    • เช็ดจากปลายมือปลายเท้าเข้าสู่หัวใจ เพื่อระบายความร้อน
                    • พักผ้าชุบน้ำบริเวณซอกคอ รักแร้ และขาหนีบ นาน 10-15 นาที
                    
                    2. การให้ยาลดไข้ในเด็ก
                    • พาราเซตามอล (Paracetamol): ให้ขนาด 10-15 มก./กก./ครั้ง ทุก 4-6 ชั่วโมงเมื่อมีไข้
                    • ห้ามให้ยาแอสไพริน (Aspirin) ในเด็กเด็ดขาด เพราะอาจทำให้เกิดกลุ่มอาการเรย์ (Reye's syndrome)
                    
                    3. การปฐมพยาบาลเมื่อลูกชักจากไข้
                    • จับลูกนอนตะแคงบนพื้นราบที่ปลอดภัย เพื่อป้องกันการสำลักน้ำลายหรือเสมหะ
                    • ห้ามนำช้อน นิ้วมือ หรือสิ่งของใดๆ สอดเข้าไปในปากลูกเด็ดขาด
                    • จับเวลาอาการชัก หากชักนานเกิน 5 นาที หรือชักซ้ำ ให้เรียกรถพยาบาล 1669 ทันที
                """.trimIndent(),
                keyTakeaways = listOf(
                    "ใช้น้ำอุ่นหรือน้ำธรรมดาเช็ดตัว เช็ดเปิดรูขุมขนสู่ลำตัว",
                    "ขนาดยาพาราเซตามอลคำนวณตามน้ำหนักตัว (10-15 มก./กก.)",
                    "เมื่อลูกชัก จับนอนตะแคง ห้ามเอาสิ่งของสอดเข้าปาก และจับเวลา"
                ),
                faqs = listOf(
                    "ลูกเป็นไข้ เหงื่อออก มือเท้าเย็น แปลว่าอะไร?" to "เป็นช่วงที่อุณหภูมิร่างกายกำลังพุ่งสูงขึ้น ให้ห่มผ้าบางๆ เช็ดตัว และวัดไข้ซ้ำทุก 30 นาทีครับ"
                ),
                likesCount = 512,
                isLiked = false
            ),
            ProfessionalAdviceBlog(
                id = "blog-5",
                title = "คู่มือดูแลฟันน้ำนมซี่แรกและการใช้ฟลูออไรด์ตั้งแต่แรกเกิด",
                subtitle = "ฟันน้ำนมแข็งแรง คือรากฐานของฟันแท้ตลอดชีวิตโดย AI น้องรัก",
                summary = "การเลือกแปรงสีฟัน ปริมาณยาสีฟันผสมฟลูออไรด์ 1000 ppm และการป้องกันฟันผุจากขวดนม",
                category = "สุขภาพ",
                doctorName = "AI น้องรัก",
                doctorTitle = "AI ผู้เชี่ยวชาญการดูแลสุขภาพช่องปากและฟันน้ำนม",
                doctorHospital = "ระบบการดูแลอัจฉริยะ NongRak AI System (มาตรฐานทันตกรรมสำหรับเด็ก)",
                readTimeMinutes = 4,
                publishedDate = "2026-08-05",
                fullContent = """
                    ฟันน้ำนมซี่แรกมักจะเริ่มขึ้นเมื่ออายุประมาณ 6-8 เดือน การดูแลตั้งแต่ซี่แรกมีความสำคัญมากเพราะฟันน้ำนมผุง่ายและลุกลามถึงโพรงประสาทฟันได้เร็วกว่าฟันแท้
                    
                    1. การเลือกยาสีฟันและปริมาณฟลูออไรด์
                    • ใช้ยาสีฟันผสมฟลูออไรด์ 1000 ppm ตั้งแต่ฟันซี่แรกขึ้น
                    • ปริมาณที่ใช้: ฟันซี่แรก - 3 ขวบ ใช้ปริมาณแค่ 'แตะพอเปื้อนขนแปรง' (Smear / Rice grain size)
                    • วัย 3-6 ขวบ: ใช้ขนาดเท่า 'เม็ดถั่วเขียว' (Pea size)
                    
                    2. การป้องกันฟันผุจากขวดนม (Baby Bottle Tooth Decay)
                    • หลีกเลี่ยงการให้ลูกดูดขวดนมจนหลับคาปาก
                    • ฝึกเลิกขวดนมและเปลี่ยนมาใช้แก้วหัดดื่มช่วงอายุ 12-18 เดือน
                    • พาลูกไปพบทันตแพทย์สำหรับเด็กครั้งแรกภายในขวบปีแรก
                """.trimIndent(),
                keyTakeaways = listOf(
                    "ใช้ยาสีฟันฟลูออไรด์ 1000 ppm ตั้งแต่ฟันซี่แรก โดยใช้ปริมาณแตะบางๆ",
                    "งดการดูดขวดนมหลับคาปากเพื่อป้องกันฟันผุลุกลาม",
                    "ตรวจฟันครั้งแรกก่อนอายุครบ 1 ขวบ"
                ),
                faqs = listOf(
                    "เด็กเล็กกลืนยาสีฟันจะเป็นอันตรายไหม?" to "หากใช้ปริมาณแตะพอเปื้อนขนแปรงตามคำแนะนำ การกลืนจะไม่ก่อให้เกิดอันตรายหรือภาวะฟันตกกระครับ"
                ),
                likesCount = 196,
                isLiked = false
            )
        )
    )

    val blogs: Flow<List<ProfessionalAdviceBlog>> = _blogs.asStateFlow()

    init {
        scope.launch {
            // Load local cache immediately so screens render instant data
            runCatching {
                val cached = OfflineCache.get<List<CommunityPost>>("community_posts")
                if (!cached.isNullOrEmpty() && _posts.value.isEmpty()) {
                    _posts.value = cached
                }
            }
            // Fetch fresh posts from Supabase in the background
            fetchPosts()
        }
    }

    /**
     * Fetches community posts, their comments, and current user's likes from Supabase.
     * Maps them into [CommunityPost] objects and saves them into [OfflineCache].
     */
    suspend fun fetchPosts(): Result<List<CommunityPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val currentUserId = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()

            // 1. Fetch remote posts
            val postDtos = supabase.from("community_posts")
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<CommunityPostDto>()

            val postIds = postDtos.map { it.id }

            // 2. Fetch comments for fetched posts
            val commentDtos = if (postIds.isNotEmpty()) {
                runCatching {
                    supabase.from("community_comments")
                        .select {
                            filter { isIn("post_id", postIds) }
                            order("created_at", Order.ASCENDING)
                        }
                        .decodeList<CommunityCommentDto>()
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            // 3. Fetch user's post likes if logged in
            val likedPostIds = if (currentUserId != null && postIds.isNotEmpty()) {
                runCatching {
                    supabase.from("community_post_likes")
                        .select {
                            filter {
                                eq("user_id", currentUserId)
                                isIn("post_id", postIds)
                            }
                        }
                        .decodeList<CommunityPostLikeDto>()
                        .map { it.postId }
                        .toSet()
                }.getOrDefault(emptySet())
            } else {
                emptySet()
            }

            // 4. Fetch user's comment likes if logged in
            val commentIds = commentDtos.map { it.id }
            val likedCommentIds = if (currentUserId != null && commentIds.isNotEmpty()) {
                runCatching {
                    supabase.from("community_comment_likes")
                        .select {
                            filter {
                                eq("user_id", currentUserId)
                                isIn("comment_id", commentIds)
                            }
                        }
                        .decodeList<CommunityCommentLikeDto>()
                        .map { it.commentId }
                        .toSet()
                }.getOrDefault(emptySet())
            } else {
                emptySet()
            }

            val commentsByPostId = commentDtos.groupBy { it.postId }

            val mappedPosts = postDtos.map { dto ->
                val epoch = parseIsoToEpochMillis(dto.createdAt)
                val comments = (commentsByPostId[dto.id] ?: emptyList()).map { cDto ->
                    val cEpoch = parseIsoToEpochMillis(cDto.createdAt)
                    CommunityComment(
                        id = cDto.id,
                        postId = cDto.postId,
                        userId = cDto.userId,
                        authorName = cDto.authorName,
                        authorRole = cDto.authorRole,
                        authorAvatarUrl = cDto.authorAvatarUrl,
                        content = cDto.content,
                        timeAgo = formatRelativeTime(cEpoch, isThai = true),
                        likesCount = cDto.likesCount,
                        isLiked = likedCommentIds.contains(cDto.id),
                        createdAtMillis = cEpoch
                    )
                }

                CommunityPost(
                    id = dto.id,
                    userId = dto.userId,
                    authorName = dto.authorName,
                    authorRole = dto.authorRole,
                    authorAvatarUrl = dto.authorAvatarUrl,
                    timeAgo = formatRelativeTime(epoch, isThai = true),
                    content = dto.content,
                    tags = dto.tags,
                    imageUrl = dto.imageUrl,
                    likesCount = dto.likesCount,
                    isLiked = likedPostIds.contains(dto.id),
                    commentsCount = if (comments.size > dto.commentsCount) comments.size else dto.commentsCount,
                    comments = comments,
                    createdAtMillis = epoch
                )
            }

            _posts.value = mappedPosts
            runCatching { OfflineCache.put("community_posts", mappedPosts) }
            mappedPosts
        }.onFailure {
            // Keep existing in-memory / cached posts if network fails
        }
    }

    /**
     * Uploads an attached image to the public community-media storage bucket.
     */
    suspend fun uploadPostImage(bytes: ByteArray, extension: String = "jpg"): Result<String> = runCatching {
        val path = "posts/${UUID.randomUUID()}.$extension"
        val bucket = supabase.storage.from("community-media")
        bucket.upload(path, bytes) {
            upsert = true
        }
        bucket.publicUrl(path)
    }

    fun getProfessionalBlogs(): List<ProfessionalAdviceBlog> = _blogs.value

    fun getBlogById(id: String): ProfessionalAdviceBlog? = _blogs.value.find { it.id == id }

    companion object {
        val instance: CommunityRepository by lazy { CommunityRepository() }

        // Raised by community_post_edit_guard / community_comment_edit_guard in migration
        // 20260908180000_community_edit_windows.sql. Keep these strings in step with the
        // RAISE EXCEPTION names there - they are matched on, not parsed.
        const val ERR_POST_EDIT_WINDOW = "post_edit_window_expired"
        const val ERR_COMMENT_EDIT_WINDOW = "comment_edit_window_expired"
        const val ERR_NOT_YOURS = "not_yours"
        const val ERR_WRITE_FAILED = "write_failed"

        /** Turns a key from [reportWriteFailure] into text. Null means nothing to show. */
        fun communityWriteError(key: String?, isThai: Boolean): String? = when (key) {
            ERR_POST_EDIT_WINDOW ->
                if (isThai) "แก้ไขโพสต์ได้ภายใน 48 ชั่วโมงหลังโพสต์เท่านั้น"
                else "Posts can only be edited within 48 hours of posting."
            ERR_COMMENT_EDIT_WINDOW ->
                if (isThai) "แก้ไขความคิดเห็นได้ภายใน 24 ชั่วโมงหลังแสดงความคิดเห็นเท่านั้น"
                else "Comments can only be edited within 24 hours."
            ERR_NOT_YOURS ->
                if (isThai) "คุณแก้ไขหรือลบได้เฉพาะโพสต์และความคิดเห็นของคุณเองเท่านั้น"
                else "You can only edit or delete your own posts and comments."
            ERR_WRITE_FAILED ->
                if (isThai) "บันทึกไม่สำเร็จ กรุณาลองใหม่อีกครั้ง"
                else "Could not save. Please try again."
            else -> null
        }
    }

    fun getAiArticleTips(): List<AiArticleTip> = _blogs.value.map {
        AiArticleTip(
            id = it.id,
            title = it.title,
            summary = it.summary,
            category = it.category,
            imageUrl = "",
            readTimeMinutes = it.readTimeMinutes
        )
    }

    fun toggleBlogLike(blogId: String) {
        _blogs.update { current ->
            current.map { b ->
                if (b.id == blogId) {
                    val nextLiked = !b.isLiked
                    val nextCount = if (nextLiked) b.likesCount + 1 else (b.likesCount - 1).coerceAtLeast(0)
                    b.copy(isLiked = nextLiked, likesCount = nextCount)
                } else b
            }
        }
    }

    fun toggleLike(postId: String) {
        var nextLiked = false
        var nextLikesCount = 0

        _posts.update { current ->
            current.map { p ->
                if (p.id == postId) {
                    nextLiked = !p.isLiked
                    nextLikesCount = if (nextLiked) p.likesCount + 1 else (p.likesCount - 1).coerceAtLeast(0)
                    p.copy(isLiked = nextLiked, likesCount = nextLikesCount)
                } else p
            }
        }

        scope.launch {
            runCatching { OfflineCache.put("community_posts", _posts.value) }
            runCatching {
                val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                if (nextLiked) {
                    supabase.from("community_post_likes").insert(CommunityPostLikeDto(postId, currentUserId))
                } else {
                    supabase.from("community_post_likes").delete {
                        filter {
                            eq("post_id", postId)
                            eq("user_id", currentUserId)
                        }
                    }
                }
                // likes_count is deliberately not written here. A liker is not the post's author,
                // and the author-only UPDATE policy rejects it; the community_post_likes_count
                // trigger recounts from the likes table instead. nextLikesCount remains the
                // optimistic local value until the next fetch.
            }.onFailure { reportWriteFailure(it) }
        }
    }

    data class HashtagStat(
        val tag: String,
        val count: Int,
        val isTrending: Boolean = false
    )

    fun getRecommendedHashtags(): List<String> {
        return listOf(
            "#คุณแม่มือใหม่",
            "#อาหารเด็ก6เดือน",
            "#ฝึกนอนยาว",
            "#พัฒนาการวัยเตาะแตะ",
            "#ของใช้แม่และเด็ก",
            "#นมแม่",
            "#วัคซีนเด็ก",
            "#ลูกมีไข้"
        )
    }

    fun getPopularHashtags(): List<HashtagStat> {
        val allTags = _posts.value.flatMap { it.tags }
        val counts = allTags.groupingBy { it }.eachCount()

        val baseList = counts.map { (tag, count) ->
            HashtagStat(tag = tag, count = count, isTrending = count >= 2)
        }.sortedByDescending { it.count }

        if (baseList.isNotEmpty()) {
            return baseList
        }

        // Default popular tags when community is fresh
        return listOf(
            HashtagStat("#นมแม่", 15, isTrending = true),
            HashtagStat("#อาหารเด็ก6เดือน", 12, isTrending = true),
            HashtagStat("#คุณแม่มือใหม่", 9, isTrending = true),
            HashtagStat("#ฝึกนอนยาว", 7, isTrending = false),
            HashtagStat("#ของใช้แม่และเด็ก", 5, isTrending = false)
        )
    }

    fun addPost(post: CommunityPost) {
        // 1. Optimistic update
        _posts.update { listOf(post) + it }

        // 2. Persist locally and remotely
        scope.launch {
            runCatching { OfflineCache.put("community_posts", _posts.value) }
            runCatching {
                val currentUserId = post.userId ?: supabase.auth.currentUserOrNull()?.id
                val dto = CommunityPostDto(
                    id = post.id,
                    userId = currentUserId,
                    authorName = post.authorName,
                    authorRole = post.authorRole,
                    authorAvatarUrl = post.authorAvatarUrl,
                    content = post.content,
                    tags = post.tags,
                    imageUrl = post.imageUrl,
                    likesCount = post.likesCount,
                    commentsCount = post.commentsCount,
                    createdAt = java.time.Instant.ofEpochMilli(post.createdAtMillis).toString()
                )
                supabase.from("community_posts").insert(dto)
            }.onFailure { reportWriteFailure(it) }
        }
    }

    fun addComment(
        postId: String,
        authorName: String,
        authorRole: String,
        content: String,
        authorAvatarUrl: String? = null,
        userId: String? = null
    ) {
        val newCommentId = UUID.randomUUID().toString()
        val nowMillis = System.currentTimeMillis()
        val newComment = CommunityComment(
            id = newCommentId,
            postId = postId,
            userId = userId,
            authorName = authorName,
            authorRole = authorRole,
            authorAvatarUrl = authorAvatarUrl,
            content = content,
            timeAgo = "เมื่อสักครู่",
            likesCount = 0,
            isLiked = false,
            createdAtMillis = nowMillis
        )

        _posts.update { current ->
            current.map { post ->
                if (post.id == postId) {
                    val updatedComments = post.comments + newComment
                    post.copy(
                        comments = updatedComments,
                        commentsCount = updatedComments.size
                    )
                } else post
            }
        }

        scope.launch {
            runCatching { OfflineCache.put("community_posts", _posts.value) }
            runCatching {
                val currentUserId = userId ?: supabase.auth.currentUserOrNull()?.id
                val cDto = CommunityCommentDto(
                    id = newCommentId,
                    postId = postId,
                    userId = currentUserId,
                    authorName = authorName,
                    authorRole = authorRole,
                    authorAvatarUrl = authorAvatarUrl,
                    content = content,
                    likesCount = 0,
                    createdAt = java.time.Instant.ofEpochMilli(nowMillis).toString()
                )
                supabase.from("community_comments").insert(cDto)

                // comments_count is not written from here: a commenter is rarely the post's author,
                // and the author-only UPDATE policy rejects it. The community_comments_count
                // trigger recounts the post from the comments table on every insert and delete.
            }.onFailure { reportWriteFailure(it) }
        }
    }

    fun toggleCommentLike(postId: String, commentId: String) {
        var nextLiked = false
        var nextLikesCount = 0

        _posts.update { current ->
            current.map { post ->
                if (post.id == postId) {
                    val updatedComments = post.comments.map { c ->
                        if (c.id == commentId) {
                            nextLiked = !c.isLiked
                            nextLikesCount = if (nextLiked) c.likesCount + 1 else (c.likesCount - 1).coerceAtLeast(0)
                            c.copy(isLiked = nextLiked, likesCount = nextLikesCount)
                        } else c
                    }
                    post.copy(comments = updatedComments)
                } else post
            }
        }

        scope.launch {
            runCatching { OfflineCache.put("community_posts", _posts.value) }
            runCatching {
                val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                if (nextLiked) {
                    supabase.from("community_comment_likes").insert(CommunityCommentLikeDto(commentId, currentUserId))
                } else {
                    supabase.from("community_comment_likes").delete {
                        filter {
                            eq("comment_id", commentId)
                            eq("user_id", currentUserId)
                        }
                    }
                }
                // Same as post likes: the comment's author owns the row, the liker does not.
                // community_comment_likes_count recounts it server-side.
            }.onFailure { reportWriteFailure(it) }
        }
    }

    /**
     * AI Content Safety & Medical Misinformation Moderation
     * Inspects post content before publishing to protect infants and parents from dangerous advice.
     */
    fun checkAiSafety(content: String): AiSafetyResult {
        val lower = content.lowercase()

        // Dangerous food for infants under 1 year (e.g. honey / botulism risk)
        if (lower.contains("น้ำผึ้ง") && (lower.contains("ทารก") || lower.contains("ต่ำกว่า 1 ขวบ") || lower.contains("เด็กแรกเกิด"))) {
            return AiSafetyResult(
                isSafe = false,
                warningMessage = "คำเตือนทางการแพทย์: ห้ามให้ทารกอายุต่ำกว่า 1 ปีรับประทานน้ำผึ้ง เนื่องจากมีความเสี่ยงสูงต่อภาวะโบทูลิซึมในทารก (Infant Botulism) ซึ่งเป็นอันตรายถึงชีวิต",
                suggestedRevision = "โปรดปรับเนื้อหาโดยไม่แนะนำการให้น้ำผึ้งแก่เด็กอายุต่ำกว่า 1 ปี หรือปรึกษากุมารแพทย์"
            )
        }

        // Dangerous unauthorized medications for children (e.g. Aspirin in viral infections / Reye's syndrome)
        if (lower.contains("แอสไพริน") || lower.contains("aspirin")) {
            return AiSafetyResult(
                isSafe = false,
                warningMessage = "คำเตือนทางการแพทย์: ห้ามให้ยาแอสไพรินแก่ทารกและเด็กเล็กโดยเด็ดขาด เนื่องจากอาจทำให้เกิดกลุ่มอาการเรย์ (Reye's Syndrome) ซึ่งทำลายตับและสมองอย่างรุนแรง",
                suggestedRevision = "โปรดแนะนำให้ปรึกษาแพทย์หรือเภสัชกรเกี่ยวกับการใช้ยาลดไข้ในเด็ก (เช่น พาราเซตามอลสำหรับเด็ก)"
            )
        }

        // Harsh chemicals or dangerous home treatments
        if (lower.contains("แอลกอฮอล์เช็ดตัว") || lower.contains("น้ำยาล้างห้องน้ำ") || lower.contains("สารฟอกขาว")) {
            return AiSafetyResult(
                isSafe = false,
                warningMessage = "คำเตือนความปลอดภัย: สารเคมีหรือแอลกอฮอล์เข้มข้นอาจซึมผ่านผิวหนังทารกและทำให้เกิดภาวะพิษรุนแรง",
                suggestedRevision = "โปรดใช้น้ำอุณหภูมิห้องหรือผ้าชุบน้ำสะอาดเช็ดตัวลดไข้ตามคำแนะนำของแพทย์"
            )
        }

        // Profanity / Harassment keywords
        val abusiveWords = listOf("กู", "มึง", "ควย", "เหี้ย", "สัตว์", "fuck", "bitch", "shit", "stupid")
        if (abusiveWords.any { lower.contains(it) }) {
            return AiSafetyResult(
                isSafe = false,
                warningMessage = "เนื้อหามีถ้อยคำที่ไม่สุภาพหรือไม่เหมาะสมตามข้อกำหนดและแนวทางชุมชนน้องรัก",
                suggestedRevision = "โปรดใช้ถ้อยคำที่สุภาพและให้เกียรติสมาชิกท่านอื่นในชุมชน"
            )
        }

        return AiSafetyResult(isSafe = true)
    }
}
