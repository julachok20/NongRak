package com.lullabyai.app.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ตารางแสดงคุณค่าทางโภชนาการของอาหารไทย (กรมอนามัย 2561), per 100 g edible portion, bundled as
 * assets/nutrition/thai_fct_2561.json (built by tools/fct/build_fct.py from the book's pages).
 *
 * A nutrient missing from [Food.n] was NOT ANALYSED in the book — unknown, never zero. Callers must
 * keep that distinction or a day's total silently under-counts.
 */
object ThaiFoodTable {

    data class Food(
        val id: String,
        val th: String,
        val en: String?,
        val group: Int,
        val n: Map<String, Double>,
        val suspect: Set<String>,
        val servingGrams: Double?
    ) {
        /** Energy per 100 g. The book's figure, unless it contradicts the book's own macros (checked
         *  against the scan when the table was built) — then the Atwater value from those macros. */
        fun kcalPer100g(): Double? {
            val printed = n["kcal"]
            if (printed != null && "kcal" !in suspect) return printed
            val p = n["protein"] ?: return printed
            val f = n["fat"] ?: return printed
            val c = n["carb"] ?: return printed
            return 4 * p + 9 * f + 4 * c + 2 * (n["fibre"] ?: 0.0)
        }
    }

    data class Match(val food: Food, val score: Double)

    @Volatile private var foods: List<Food> = emptyList()
    @Volatile private var byId: Map<String, Food> = emptyMap()

    fun load(context: android.content.Context) {
        if (foods.isNotEmpty()) return
        synchronized(this) {
            if (foods.isNotEmpty()) return
            install(context.assets.open("nutrition/thai_fct_2561.json").bufferedReader().use { it.readText() })
        }
    }

    /** Tests (plain JVM, no assets): install the same file read from disk. */
    fun install(json: String) {
        val list = Json.parseToJsonElement(json).jsonObject.getValue("foods").jsonArray.map { e ->
            val o = e.jsonObject
            Food(
                id = o.str("id"),
                th = o.str("th"),
                en = o["en"]?.jsonPrimitive?.contentOrNull,
                group = o.getValue("group").jsonPrimitive.intOrNull ?: 0,
                n = o.getValue("n").jsonObject.mapNotNull { (k, v) -> v.jsonPrimitive.doubleOrNull?.let { k to it } }.toMap(),
                suspect = o["suspect"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
                servingGrams = (o["serving"] as? JsonObject)?.get("grams")?.jsonPrimitive?.doubleOrNull
            )
        }
        byId = list.associateBy { it.id }
        foods = list
    }

    private fun ensure() {
        if (foods.isEmpty()) load(com.lullabyai.app.NongRakApp.instance)
    }

    fun byId(id: String): Food? { ensure(); return byId[id] }

    /**
     * Everyday words parents type → the table row that represents them. The book uses formal,
     * variety-level names ("ข้าวเจ้า, นึ่ง", "นม, ยูเอชที, รสธรรมชาติ") that a parent never writes.
     * ponytail: a hand list of common child foods; grow it from the unmatched names the page shows.
     */
    private val ALIASES: List<Pair<String, String>> = listOf(
        "ข้าวสวย" to "01017", "ข้าวหุง" to "01017", "ข้าวเจ้า" to "01017", "ข้าว" to "01017",
        "ข้าวเหนียว" to "01039", "ข้าวโพด" to "01029", "ขนมปัง" to "01006", "ขนมจีน" to "01004",
        "มันเทศ" to "02012", "มันฝรั่ง" to "02013", "เผือก" to "02004",
        "ถั่วเขียว" to "03017", "เต้าหู้" to "03011", "เต้าหู้ขาว" to "03011", "นมถั่วเหลือง" to "03046",
        "ฟักทอง" to "04154", "แครอท" to "04038", "บร็อคโคลี่" to "04082", "บรอกโคลี" to "04082",
        "ผักบุ้ง" to "04121", "ตำลึง" to "04059", "ผักกาดขาว" to "04099", "ผักโขม" to "04110",
        "คะน้า" to "04111", "ผักคะน้า" to "04111", "ปวยเล้ง" to "04095", "กะหล่ำปลี" to "04019",
        "มะเขือเทศ" to "04172", "แตงกวา" to "04061", "ถั่วฝักยาว" to "04070", "ข้าวโพดอ่อน" to "04026",
        "กล้วย" to "05015", "กล้วยน้ำว้า" to "05015", "กล้วยหอม" to "05021", "กล้วยไข่" to "05001",
        "มะละกอ" to "05107", "มะละกอสุก" to "05107", "แตงโม" to "05051", "ส้ม" to "05133",
        "ส้มเขียวหวาน" to "05133", "แอปเปิ้ล" to "05158", "แอปเปิล" to "05158", "ฝรั่ง" to "05067",
        "มะม่วงสุก" to "05093", "มะม่วง" to "05093", "องุ่น" to "05153", "ลำไย" to "05117",
        "สตรอว์เบอร์รี่" to "05131", "สตรอเบอร์รี่" to "05131", "แก้วมังกร" to "05029", "ลิ้นจี่" to "05120",
        "หมู" to "06050", "หมูสับ" to "06050", "เนื้อหมู" to "06050", "ไก่" to "06005", "เนื้อไก่" to "06005",
        "อกไก่" to "06005", "เนื้อวัว" to "06032", "ตับหมู" to "06054", "ตับ" to "06054", "ตับไก่" to "06004",
        "เลือดหมู" to "06052", "ไส้กรอก" to "06044", "ลูกชิ้นหมู" to "06059", "หมูยอ" to "06058",
        "ปลาทู" to "07035", "ปลานิล" to "07041", "ปลาช่อน" to "07024", "ปลาทับทิม" to "07039",
        "ปลาทูน่า" to "07036", "ปลาแซลมอน" to "11111", "แซลมอน" to "11111", "กุ้ง" to "07002",
        "ลูกชิ้นปลา" to "07085", "ปลาเส้น" to "07061",
        "ไข่" to "08003", "ไข่ไก่" to "08003", "ไข่ต้ม" to "08003", "ไข่แดง" to "08002", "ไข่ขาว" to "08001",
        "ไข่นกกระทา" to "08008", "ไข่เป็ด" to "08012", "ไข่ตุ๋น" to "08019", "เต้าหู้ไข่" to "08019",
        "นมแม่" to "09011", "นมจืด" to "09003", "นมกล่อง" to "09003", "นมวัว" to "09003", "นม" to "09003",
        "นมยูเอชที" to "09003", "นมหวาน" to "09004", "นมพาสเจอร์ไรส์" to "09001", "นมพร่องมันเนย" to "09024",
        "โยเกิร์ต" to "09035", "นมเปรี้ยว" to "09014", "ชีส" to "09033", "เนย" to "09032",
        "ผัดไทย" to "11007", "ข้าวผัด" to "11044", "ข้าวมันไก่" to "11045", "ข้าวขาหมู" to "11042",
        "ข้าวหมูแดง" to "11087", "ส้มตำ" to "11204", "แกงจืดเต้าหู้" to "11060", "ต้มจืด" to "11060"
    ).sortedByDescending { it.first.length }   // longest key wins ("ไข่ต้ม" before "ไข่")

    /** Quantity words and numbers that are not part of the food ("กล้วย 1 ลูก", "นม 200 มล."). */
    private val NOISE = Regex(
        "[0-9๐-๙]+(\\.[0-9]+)?|ครึ่ง|ลูก|ชิ้น|ถ้วย|แก้ว|ช้อนโต๊ะ|ช้อนชา|ช้อน|ฟอง|กล่อง|ขวด|จาน|ชาม|มล\\.?|ml|กรัม|g\\b|ออนซ์|oz|นิดหน่อย|เล็กน้อย|คำ"
    )

    fun normalize(s: String): String =
        s.lowercase().replace(NOISE, "").replace(Regex("[\\s,()/\\-.:;'\"]"), "")

    /** Minimum share of the typed text that a table name must cover to count as the same food. */
    private const val MIN_SCORE = 0.7

    /**
     * Best table row for a typed food name, or null when nothing is close enough — the caller then
     * falls back to an AI estimate rather than forcing a wrong official figure onto the food.
     */
    fun match(typed: String): Match? {
        ensure()
        val q = normalize(typed)
        if (q.isEmpty()) return null

        // 1. The typed text IS a table name (full, or the part before the first comma).
        foods.firstOrNull { normalize(it.th) == q }?.let { return Match(it, 1.0) }
        foods.firstOrNull { normalize(it.th.substringBefore(',')) == q }?.let { return Match(it, 0.95) }

        // 2. An everyday word covering most of the typed text ("ข้าวผัดหมู" → ข้าวผัด).
        ALIASES.firstOrNull { (key, _) -> q == key }?.let { (_, id) -> byId[id]?.let { return Match(it, 0.95) } }
        ALIASES.firstOrNull { (key, _) -> q.contains(key) && key.length.toDouble() / q.length >= MIN_SCORE }
            ?.let { (key, id) -> byId[id]?.let { return Match(it, key.length.toDouble() / q.length) } }

        // 3. A table name that shares most of its letters with the typed text.
        return foods.asSequence().mapNotNull { f ->
            val head = normalize(f.th.substringBefore(','))
            if (head.length < 3) return@mapNotNull null
            val score = when {
                q.contains(head) -> head.length.toDouble() / q.length
                head.contains(q) -> q.length.toDouble() / head.length
                else -> 0.0
            }
            if (score >= MIN_SCORE) Match(f, score) else null
        }.maxByOrNull { it.score }
    }

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content
}
