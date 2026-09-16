package com.lullabyai.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lullabyai.app.ui.navigation.Screen
import java.time.LocalTime

/** Categories for grouping all Quick Actions in the app */
enum class QuickActionCategory(val labelTh: String, val labelEn: String, val icon: ImageVector) {
    ALL("ทั้งหมด", "All", Icons.Filled.GridView),
    ROUTINE("กิจวัตร & นม", "Routine & Habits", Icons.Filled.BabyChangingStation),
    HEALTH("สุขภาพ & ยา", "Health & Meds", Icons.Filled.HealthAndSafety),
    NUTRITION("โภชนาการ", "Nutrition", Icons.Filled.Restaurant),
    GROWTH("การเติบโต & สถิติ", "Growth & Insights", Icons.Filled.TrendingUp),
    REPORTS("เอกสาร & รายงาน", "Reports & Vault", Icons.Filled.FolderShared),
    TOOLS("ผู้ช่วย AI & เครื่องมือ", "AI & Tools", Icons.Filled.AutoAwesome)
}

/**
 * Universal Quick Action model representing all reachable in-app destinations.
 */
data class QuickAction(
    val route: String,
    val labelTh: String,
    val labelEn: String,
    val icon: ImageVector,
    val keywords: String,
    val category: QuickActionCategory = QuickActionCategory.TOOLS,
    val iconTint: Color = Color(0xFF1E88E5),
    val iconBg: Color = Color(0xFFE3F2FD),
    val subtitleTh: String? = null,
    val subtitleEn: String? = null,
    val isPrimaryQuickLog: Boolean = false
)

object QuickActionCatalog {
    val all: List<QuickAction> = listOf(
        // ==================== 1. ROUTINE & HABITS (กิจวัตร & ติดตาม) ====================
        QuickAction(
            route = Screen.MilkRecord.route,
            labelTh = "บันทึกการดื่มนม",
            labelEn = "Milk Record",
            icon = Icons.Filled.BabyChangingStation,
            keywords = "milk feeding log นม ให้นม ดื่มนม ขวดนม เต้านม",
            category = QuickActionCategory.ROUTINE,
            iconTint = Color(0xFF00897B),
            iconBg = Color(0xFFE0F2F1),
            subtitleTh = "นมแม่ / นมผง / ปริมาณ",
            subtitleEn = "Breast / Bottle / Amount",
            isPrimaryQuickLog = true
        ),
        QuickAction(
            route = Screen.SleepTracker.route,
            labelTh = "ติดตามการนอน",
            labelEn = "Sleep Tracker",
            icon = Icons.Filled.Bedtime,
            keywords = "sleep การนอน นอน หลับ nap งีบ กลางคืน กลางวัน",
            category = QuickActionCategory.ROUTINE,
            iconTint = Color(0xFF5E35B1),
            iconBg = Color(0xFFEDE7F6),
            subtitleTh = "จับเวลานอน / ประวัติการนอน",
            subtitleEn = "Sleep timer & history",
            isPrimaryQuickLog = true
        ),
        QuickAction(
            route = Screen.WasteRecord.route,
            labelTh = "บันทึกขับถ่าย & ผ้าอ้อม",
            labelEn = "Diaper & Waste",
            icon = Icons.Filled.WaterDrop,
            keywords = "diaper urine stool waste ขับถ่าย ผ้าอ้อม อุจจาระ ปัสสาวะ",
            category = QuickActionCategory.ROUTINE,
            iconTint = Color(0xFF0288D1),
            iconBg = Color(0xFFE1F5FE),
            subtitleTh = "ปัสสาวะ / อุจจาระ / เปลี่ยนผ้าอ้อม",
            subtitleEn = "Wet / Dirty / Diaper change",
            isPrimaryQuickLog = true
        ),
        QuickAction(
            route = com.lullabyai.app.ui.screens.sleep.RECORD_SLEEP_HISTORY_ROUTE,
            labelTh = "บันทึกการนอนย้อนหลัง",
            labelEn = "Record Sleep History",
            icon = Icons.Filled.Alarm,
            keywords = "log sleep add บันทึกนอน ย้อนหลัง",
            category = QuickActionCategory.ROUTINE,
            iconTint = Color(0xFF3949AB),
            iconBg = Color(0xFFE8EAF6),
            subtitleTh = "เพิ่มเวลานอนที่ผ่านมา",
            subtitleEn = "Add past sleep session"
        ),
        QuickAction(
            route = Screen.SleepSchedule.route,
            labelTh = "ตารางการนอน & ปลุก",
            labelEn = "Sleep Schedule",
            icon = Icons.Filled.Schedule,
            keywords = "sleep schedule nap ตารางนอน เวลาเข้านอน ปลุก",
            category = QuickActionCategory.ROUTINE,
            iconTint = Color(0xFF1E88E5),
            iconBg = Color(0xFFE3F2FD),
            subtitleTh = "ตั้งเวลาเข้านอนและตื่น",
            subtitleEn = "Bedtime & wake routines"
        ),
        QuickAction(
            route = Screen.SleepGuidance.route,
            labelTh = "คำแนะนำการนอนจาก AI",
            labelEn = "AI Sleep Guidance",
            icon = Icons.Filled.NightsStay,
            keywords = "sleep tips guidance คำแนะนำนอน กล่อมนอน นอนไม่หลับ",
            category = QuickActionCategory.ROUTINE,
            iconTint = Color(0xFF6A1B9A),
            iconBg = Color(0xFFF3E5F5),
            subtitleTh = "เทคนิคกล่อมนอนและปรับเวลานอน",
            subtitleEn = "Sleep coaching & tips"
        ),
        QuickAction(
            route = Screen.OralHealth.route,
            labelTh = "สุขภาพช่องปาก & ฟัน",
            labelEn = "Oral Health & Teeth",
            icon = Icons.Filled.HealthAndSafety,
            keywords = "oral health teeth dentistry ฟัน ช่องปาก แปรงฟัน แผนผังฟัน หมอฟัน",
            category = QuickActionCategory.ROUTINE,
            iconTint = Color(0xFF00ACC1),
            iconBg = Color(0xFFE0F7FA),
            subtitleTh = "แผนผังฟันน้ำนม & แปรงฟัน",
            subtitleEn = "Teeth chart & brushing",
            isPrimaryQuickLog = true
        ),
        QuickAction(
            route = Screen.RecordOralHealthEvent.route,
            labelTh = "บันทึกเหตุการณ์ช่องปาก",
            labelEn = "Record Oral Event",
            icon = Icons.Filled.AddCircle,
            keywords = "oral event teeth log บันทึกฟัน ฟันซี่แรก เคลือบฟลูออไรด์",
            category = QuickActionCategory.ROUTINE,
            iconTint = Color(0xFF00897B),
            iconBg = Color(0xFFE0F2F1),
            subtitleTh = "ฟันซี่แรกงอก / เคลือบฟลูออไรด์",
            subtitleEn = "First tooth / Fluoride visit"
        ),

        // ==================== 2. HEALTH & MEDICAL (สุขภาพ & การแพทย์) ====================
        QuickAction(
            route = Screen.ChildHealth.route,
            labelTh = "ศูนย์สุขภาพและอาการ",
            labelEn = "Health Center",
            icon = Icons.Filled.HealthAndSafety,
            keywords = "health allergy โรคประจำตัว ภูมิแพ้ เจ็บป่วย สุขภาพ ไข้",
            category = QuickActionCategory.HEALTH,
            iconTint = Color(0xFFE53935),
            iconBg = Color(0xFFFFEBEE),
            subtitleTh = "ภาพรวมสุขภาพและการรักษา",
            subtitleEn = "Health overview & symptoms",
            isPrimaryQuickLog = true
        ),
        QuickAction(
            route = Screen.AddHealthRecord.route,
            labelTh = "เพิ่มบันทึกสุขภาพ / ไข้",
            labelEn = "Add Health Record",
            icon = Icons.Filled.Thermostat,
            keywords = "log health fever ไข้ อาการ บันทึกสุขภาพ วัดไข้ ตัวร้อน",
            category = QuickActionCategory.HEALTH,
            iconTint = Color(0xFFD81B60),
            iconBg = Color(0xFFFCE4EC),
            subtitleTh = "บันทึกอุณหภูมิ / อาการผิดปกติ",
            subtitleEn = "Log temperature & illness",
            isPrimaryQuickLog = true
        ),
        QuickAction(
            route = Screen.VaccineBook.route,
            labelTh = "สมุดวัคซีนดิจิทัล",
            labelEn = "Vaccine Book",
            icon = Icons.Filled.Vaccines,
            keywords = "vaccine immunization วัคซีน ฉีด vaccine ฉีดวัคซีน สมุด",
            category = QuickActionCategory.HEALTH,
            iconTint = Color(0xFF8E24AA),
            iconBg = Color(0xFFF3E5F5),
            subtitleTh = "ตารางวัคซีนตามเกณฑ์ & ประวัติ",
            subtitleEn = "Immunization schedule"
        ),
        QuickAction(
            route = Screen.MedicationHistory.route,
            labelTh = "ประวัติการใช้ยา",
            labelEn = "Medication History",
            icon = Icons.Filled.Medication,
            keywords = "medicine dose history ยา กินยา ป้อนยา ทานยา",
            category = QuickActionCategory.HEALTH,
            iconTint = Color(0xFF3949AB),
            iconBg = Color(0xFFE8EAF6),
            subtitleTh = "บันทึกการกินยา & รายการยา",
            subtitleEn = "Active meds & dosage"
        ),
        QuickAction(
            route = Screen.AddMedicineSchedule.route,
            labelTh = "เพิ่มตารางแจ้งเตือนยา",
            labelEn = "Add Medicine Schedule",
            icon = Icons.Filled.AddAlarm,
            keywords = "add medicine schedule dose ตารางยา เตือนกินยา",
            category = QuickActionCategory.HEALTH,
            iconTint = Color(0xFF1E88E5),
            iconBg = Color(0xFFE3F2FD),
            subtitleTh = "ตั้งเวลาเตือนป้อนยาตรงเวลา",
            subtitleEn = "Medication reminder alarms"
        ),
        QuickAction(
            route = Screen.AllergyHistory.route,
            labelTh = "ประวัติภูมิแพ้ & แพ้อาหาร",
            labelEn = "Allergy History",
            icon = Icons.Filled.Warning,
            keywords = "allergy แพ้ ภูมิแพ้ แพ้อาหาร แพ้ยา ผื่น",
            category = QuickActionCategory.HEALTH,
            iconTint = Color(0xFFF4511E),
            iconBg = Color(0xFFFBE9E7),
            subtitleTh = "อาหารและยาที่ต้องระวัง",
            subtitleEn = "Food & drug allergies"
        ),
        QuickAction(
            route = Screen.ChronicRegistry.route,
            labelTh = "โรคประจำตัวและการป้องกัน",
            labelEn = "Chronic Conditions",
            icon = Icons.Filled.Shield,
            keywords = "chronic disease prevention registry โรคประจำตัว ป้องกัน เสี่ยง risk",
            category = QuickActionCategory.HEALTH,
            iconTint = Color(0xFF43A047),
            iconBg = Color(0xFFE8F5E9),
            subtitleTh = "แผนป้องกัน & เบอร์ฉุกเฉิน",
            subtitleEn = "Care plans & precautions"
        ),
        QuickAction(
            route = Screen.ChildAiSummary.route,
            labelTh = "ตรวจสอบสุขภาพด้วย AI",
            labelEn = "AI Child Summary",
            icon = Icons.Filled.Summarize,
            keywords = "ai summary check health สรุป วิเคราะห์ สุขภาพ",
            category = QuickActionCategory.HEALTH,
            iconTint = Color(0xFF7CB342),
            iconBg = Color(0xFFF1F8E9),
            subtitleTh = "ประเมินสุขภาพองค์รวม",
            subtitleEn = "Holistic health assessment"
        ),
        QuickAction(
            route = Screen.DoctorVisitHistory.route,
            labelTh = "ประวัติการพบแพทย์",
            labelEn = "Doctor Visit History",
            icon = Icons.Filled.MedicalServices,
            keywords = "doctor visit appointment หาหมอ นัดหมาย ประวัติหาหมอ",
            category = QuickActionCategory.HEALTH,
            iconTint = Color(0xFF00897B),
            iconBg = Color(0xFFE0F2F1),
            subtitleTh = "ใบนัด & บันทึกการตรวจ",
            subtitleEn = "Clinic visits & doctor notes"
        ),

        // ==================== 3. NUTRITION & MEALS (โภชนาการ & อาหาร) ====================
        QuickAction(
            route = Screen.Nutrition.route,
            labelTh = "ศูนย์โภชนาการและอาหาร",
            labelEn = "Nutrition Center",
            icon = Icons.Filled.Restaurant,
            keywords = "food nutrient สารอาหาร อาหาร เมนู กิน โภชนาการ",
            category = QuickActionCategory.NUTRITION,
            iconTint = Color(0xFF43A047),
            iconBg = Color(0xFFE8F5E9),
            subtitleTh = "สารอาหาร & คุณค่ารายวัน",
            subtitleEn = "Daily nutrient balance"
        ),
        QuickAction(
            route = Screen.CalorieCalculator.route,
            labelTh = "คำนวณแคลอรี่อาหาร",
            labelEn = "Calorie Calculator",
            icon = Icons.Filled.Calculate,
            keywords = "calorie calculator แคลอรี่ คำนวณ บันทึกอาหาร มื้ออาหาร",
            category = QuickActionCategory.NUTRITION,
            iconTint = Color(0xFFFB8C00),
            iconBg = Color(0xFFFFF3E0),
            subtitleTh = "คำนวณพลังงานตามมื้ออาหาร",
            subtitleEn = "Meal calories logger",
            isPrimaryQuickLog = true
        ),
        QuickAction(
            route = Screen.CalorieCheck.route,
            labelTh = "ตรวจสอบแคลอรี่เมนูจานโปรด",
            labelEn = "Calorie Check",
            icon = Icons.Filled.Bolt,
            keywords = "calorie check dish food ตรวจแคลอรี่ เมนูอาหาร",
            category = QuickActionCategory.NUTRITION,
            iconTint = Color(0xFFF57C00),
            iconBg = Color(0xFFFFF3E0),
            subtitleTh = "ค้นหาแคลอรี่จากฐานข้อมูลอาหาร",
            subtitleEn = "Search dish calories"
        ),
        QuickAction(
            route = Screen.CalorieSummary.route,
            labelTh = "สรุปแคลอรี่รายวัน",
            labelEn = "Calorie Summary",
            icon = Icons.Filled.LocalFireDepartment,
            keywords = "calorie summary today สรุปแคลอรี่ พลังงานที่ได้รับ",
            category = QuickActionCategory.NUTRITION,
            iconTint = Color(0xFFE53935),
            iconBg = Color(0xFFFFEBEE),
            subtitleTh = "กราฟพลังงานเทียบเกณฑ์",
            subtitleEn = "Daily intake vs target"
        ),
        QuickAction(
            route = Screen.RecommendedFood.route,
            labelTh = "เมนูอาหารแนะนำตามวัย",
            labelEn = "Recommended Food",
            icon = Icons.Filled.FoodBank,
            keywords = "recommended food menu เมนูแนะนำ อาหารตามวัย BLW",
            category = QuickActionCategory.NUTRITION,
            iconTint = Color(0xFF8E24AA),
            iconBg = Color(0xFFF3E5F5),
            subtitleTh = "เมนูเสริมสร้างพัฒนาการ",
            subtitleEn = "Age-appropriate recipes"
        ),
        QuickAction(
            route = Screen.RefrigeratorScanner.route,
            labelTh = "ตู้เย็นอัจฉริยะ & สแกนวัตถุดิบ",
            labelEn = "Smart Fridge & Scanner",
            icon = Icons.Filled.Kitchen,
            keywords = "fridge refrigerator scan ingredients pantry ตู้เย็น สแกนตู้เย็น วัตถุดิบ",
            category = QuickActionCategory.NUTRITION,
            iconTint = Color(0xFF00897B),
            iconBg = Color(0xFFE0F2F1),
            subtitleTh = "คิดเมนูอัตโนมัติจากของในตู้เย็น",
            subtitleEn = "Recipe ideas from ingredients"
        ),
        QuickAction(
            route = Screen.FoodHistory.route,
            labelTh = "ประวัติอาหารทั้งหมด",
            labelEn = "Food History",
            icon = Icons.Filled.History,
            keywords = "food log history ประวัติอาหาร มื้อที่เคยกิน",
            category = QuickActionCategory.NUTRITION,
            iconTint = Color(0xFF5E35B1),
            iconBg = Color(0xFFEDE7F6),
            subtitleTh = "ย้อนดูรายการอาหาร",
            subtitleEn = "All past food logs"
        ),

        // ==================== 4. GROWTH & STATS (การเติบโต & สถิติ) ====================
        QuickAction(
            route = Screen.AddGrowthRecord.route,
            labelTh = "อัปเดตน้ำหนัก & ส่วนสูง",
            labelEn = "Update Weight & Height",
            icon = Icons.Filled.Straighten,
            keywords = "add growth weight height น้ำหนัก ส่วนสูง รอบศีรษะ วัด",
            category = QuickActionCategory.GROWTH,
            iconTint = Color(0xFF00ACC1),
            iconBg = Color(0xFFE0F7FA),
            subtitleTh = "บันทึกค่าน้ำหนัก ส่วนสูง รอบหัว",
            subtitleEn = "Record growth metrics",
            isPrimaryQuickLog = true
        ),
        QuickAction(
            route = Screen.GrowthHistory.route,
            labelTh = "ประวัติการเจริญเติบโต",
            labelEn = "Growth History",
            icon = Icons.Filled.MonitorWeight,
            keywords = "weight height น้ำหนัก ส่วนสูง เจริญเติบโต growth ประวัติ",
            category = QuickActionCategory.GROWTH,
            iconTint = Color(0xFF3949AB),
            iconBg = Color(0xFFE8EAF6),
            subtitleTh = "แนวโน้มการเติบโตย้อนหลัง",
            subtitleEn = "Growth trend charts"
        ),
        QuickAction(
            route = Screen.WhoWeightForAgeDetail.route,
            labelTh = "กราฟน้ำหนักตามเกณฑ์ WHO",
            labelEn = "WHO Weight-for-Age",
            icon = Icons.Filled.ShowChart,
            keywords = "who growth chart weight กราฟ น้ำหนัก มาตรฐาน สากล",
            category = QuickActionCategory.GROWTH,
            iconTint = Color(0xFF1E88E5),
            iconBg = Color(0xFFE3F2FD),
            subtitleTh = "เทียบเกณฑ์มาตรฐานองค์การอนามัยโลก",
            subtitleEn = "Percentiles & standard curve"
        ),
        QuickAction(
            route = Screen.WhoHeightForAgeDetail.route,
            labelTh = "กราฟส่วนสูงตามเกณฑ์ WHO",
            labelEn = "WHO Height-for-Age",
            icon = Icons.Filled.TrendingUp,
            keywords = "who growth chart height กราฟ ส่วนสูง ความสูง มาตรฐาน",
            category = QuickActionCategory.GROWTH,
            iconTint = Color(0xFF43A047),
            iconBg = Color(0xFFE8F5E9),
            subtitleTh = "กราฟความสูงเทียบอายุ",
            subtitleEn = "Height curves by age"
        ),
        QuickAction(
            route = Screen.ChildDevelopment.route,
            labelTh = "พัฒนาการและทักษะ",
            labelEn = "Child Development",
            icon = Icons.Filled.EmojiEvents,
            keywords = "milestones skills ทักษะ กิจกรรม พัฒนาการ ฝึกฝน",
            category = QuickActionCategory.GROWTH,
            iconTint = Color(0xFFFB8C00),
            iconBg = Color(0xFFFFF3E0),
            subtitleTh = "เช็คลิสต์พัฒนาการตามช่วงวัย",
            subtitleEn = "Milestone checklist",
            isPrimaryQuickLog = true
        ),
        QuickAction(
            route = Screen.Exercise.route,
            labelTh = "ออกกำลังกาย & กิจกรรมเสริม",
            labelEn = "Exercise & Activity",
            icon = Icons.Filled.FitnessCenter,
            keywords = "exercise activity ออกกำลัง ออกกำลังกาย เดิน คลาน วิ่ง เล่น",
            category = QuickActionCategory.GROWTH,
            iconTint = Color(0xFFD81B60),
            iconBg = Color(0xFFFCE4EC),
            subtitleTh = "กิจกรรมกระตุ้นกล้ามเนื้อ",
            subtitleEn = "Active play & movement"
        ),
        QuickAction(
            route = Screen.StatisticsInsights.route,
            labelTh = "สถิติและข้อมูลเชิงลึก",
            labelEn = "Statistics & Insights",
            icon = Icons.Filled.BarChart,
            keywords = "stats statistics insights สถิติ วิเคราะห์ ภาพรวม",
            category = QuickActionCategory.GROWTH,
            iconTint = Color(0xFF8E24AA),
            iconBg = Color(0xFFF3E5F5),
            subtitleTh = "สรุปพฤติกรรมรอบด้าน",
            subtitleEn = "Comprehensive behavioral stats"
        ),
        QuickAction(
            route = Screen.SmartTrends.route,
            labelTh = "แนวโน้มอัจฉริยะ (Smart Trends)",
            labelEn = "Smart Trends",
            icon = Icons.Filled.Insights,
            keywords = "trends analysis ai แนวโน้ม ทำนาย อัจฉริยะ",
            category = QuickActionCategory.GROWTH,
            iconTint = Color(0xFF5E35B1),
            iconBg = Color(0xFFEDE7F6),
            subtitleTh = "AI คาดการณ์การเติบโต",
            subtitleEn = "Predictive AI health trends"
        ),

        // ==================== 5. REPORTS & VAULT (เอกสาร & รายงาน) ====================
        QuickAction(
            route = Screen.PdfExport.route,
            labelTh = "ส่งออกรายงานสุขภาพ PDF",
            labelEn = "Export PDF Report",
            icon = Icons.Filled.PictureAsPdf,
            keywords = "export pdf report ส่งออก รายงาน หมอ เอกสาร พิมพ์",
            category = QuickActionCategory.REPORTS,
            iconTint = Color(0xFFE53935),
            iconBg = Color(0xFFFFEBEE),
            subtitleTh = "สร้างเอกสารสรุปให้คุณหมอ",
            subtitleEn = "Doctor-ready health summary"
        ),
        QuickAction(
            route = Screen.MedicalDataVault.route,
            labelTh = "คลังเอกสารการแพทย์ (Vault)",
            labelEn = "Document Vault",
            icon = Icons.Filled.FolderShared,
            keywords = "medical vault documents คลังเอกสาร files เก็บเอกสาร ใบตรวจ",
            category = QuickActionCategory.REPORTS,
            iconTint = Color(0xFF00897B),
            iconBg = Color(0xFFE0F2F1),
            subtitleTh = "เก็บรูปใบรับรองแพทย์ & ผลแล็บ",
            subtitleEn = "Medical certificates & labs"
        ),
        QuickAction(
            route = Screen.WeeklyHealthReport.route,
            labelTh = "รายงานสุขภาพรายสัปดาห์",
            labelEn = "Weekly Health Report",
            icon = Icons.Filled.Assessment,
            keywords = "report weekly รายงาน สัปดาห์ สรุป",
            category = QuickActionCategory.REPORTS,
            iconTint = Color(0xFF3949AB),
            iconBg = Color(0xFFE8EAF6),
            subtitleTh = "สรุปพฤติกรรม 7 วันล่าสุด",
            subtitleEn = "7-day routine recap"
        ),
        QuickAction(
            route = Screen.MonthlyReport.route,
            labelTh = "รายงานสรุปประจำเดือน",
            labelEn = "Monthly Report",
            icon = Icons.Filled.CalendarMonth,
            keywords = "report monthly summary รายงาน เดือน สรุปรายเดือน",
            category = QuickActionCategory.REPORTS,
            iconTint = Color(0xFF1E88E5),
            iconBg = Color(0xFFE3F2FD),
            subtitleTh = "ภาพรวมการเติบโตรายเดือน",
            subtitleEn = "Month-by-month progress"
        ),
        QuickAction(
            route = Screen.PermissionAccess.route,
            labelTh = "จัดการสิทธิ์เข้าถึง & สมาชิก",
            labelEn = "Permission & Access",
            icon = Icons.Filled.Security,
            keywords = "permission access share สิทธิ์ จัดการสมาชิก ผู้ช่วยดูแล ครอบครัว",
            category = QuickActionCategory.REPORTS,
            iconTint = Color(0xFF5E35B1),
            iconBg = Color(0xFFEDE7F6),
            subtitleTh = "เพิ่มผู้ปกครองร่วม / ผู้ช่วยดูแล",
            subtitleEn = "Family sharing & caregivers"
        ),
        QuickAction(
            route = Screen.CareCircle.route,
            labelTh = "แวดวงการดูแล (Care Circle)",
            labelEn = "Care Circle",
            icon = Icons.Filled.Groups,
            keywords = "care circle family แวดวง กลุ่ม แชท ครอบครัว",
            category = QuickActionCategory.REPORTS,
            iconTint = Color(0xFF43A047),
            iconBg = Color(0xFFE8F5E9),
            subtitleTh = "ทีมผู้ดูแลและแพทย์ประจำตัว",
            subtitleEn = "Co-parents & specialists"
        ),
        QuickAction(
            route = Screen.MentalHealthAssessment.route,
            labelTh = "ประเมินสุขภาพจิตพ่อแม่",
            labelEn = "Parent Mental Health",
            icon = Icons.Filled.Psychology,
            keywords = "mental health parent epds สุขภาพจิต ความเครียด ซึมเศร้า",
            category = QuickActionCategory.REPORTS,
            iconTint = Color(0xFF8E24AA),
            iconBg = Color(0xFFF3E5F5),
            subtitleTh = "แบบประเมินความเครียดรายเดือน",
            subtitleEn = "Monthly parent wellness check"
        ),

        // ==================== 6. AI & TOOLS (ผู้ช่วย AI & เครื่องมือ) ====================
        QuickAction(
            route = Screen.AiChat.route,
            labelTh = "คุยกับ AI ผู้ช่วยน้องรัก",
            labelEn = "Chat with AI NongRak",
            icon = Icons.Filled.AutoAwesome,
            keywords = "chatbot assistant น้องรัก nongrak แชท chat ai ถาม ปรึกษา",
            category = QuickActionCategory.TOOLS,
            iconTint = Color(0xFF7C4DFF),
            iconBg = Color(0xFFEDE7F6),
            subtitleTh = "ปรึกษาการเลี้ยงดู 24 ชั่วโมง",
            subtitleEn = "24/7 pediatric AI companion"
        ),
        QuickAction(
            route = Screen.ActivitySchedule.route,
            labelTh = "ปฏิทินกิจกรรม & แนะนำที่เที่ยว",
            labelEn = "Activity Schedule",
            icon = Icons.Filled.Event,
            keywords = "activity schedule event calendar ปฏิทิน กิจกรรม ที่เที่ยว ที่เรียนรู้",
            category = QuickActionCategory.TOOLS,
            iconTint = Color(0xFF0288D1),
            iconBg = Color(0xFFE1F5FE),
            subtitleTh = "กิจกรรมเสริมพัฒนาการ & โปรโมชั่น",
            subtitleEn = "Curated activities & deals"
        ),
        QuickAction(
            route = Screen.ChildDiary.route,
            labelTh = "สมุดความทรงจำ (Diary)",
            labelEn = "Child Diary",
            icon = Icons.Filled.AutoStories,
            keywords = "diary memory book บันทึกความจำ ไดอารี่ รูปภาพ เรื่องเล่า",
            category = QuickActionCategory.TOOLS,
            iconTint = Color(0xFFFB8C00),
            iconBg = Color(0xFFFFF3E0),
            subtitleTh = "บันทึกโมเมนต์น่ารักของลูก",
            subtitleEn = "Photo journal & memories"
        ),
        QuickAction(
            route = Screen.ChildDiaryReadBook.route,
            labelTh = "เปิดอ่าน E-Book & คลิปสรุป AI",
            labelEn = "Read Chronicle & AI Recap",
            icon = Icons.Filled.MenuBook,
            keywords = "read book chronicle recap clip video ตัดต่อ วิดีโอ ไฮไลท์ ig tiktok share",
            category = QuickActionCategory.TOOLS,
            iconTint = Color(0xFF1E88E5),
            iconBg = Color(0xFFE3F2FD),
            subtitleTh = "ไฮไลท์ความทรงจำ ตัดต่อคลิป & แชร์",
            subtitleEn = "Interactive book & video recap"
        ),
        QuickAction(
            route = Screen.Expenses.route,
            labelTh = "บันทึกค่าใช้จ่ายลูกน้อย",
            labelEn = "Child Expenses Log",
            icon = Icons.Filled.AttachMoney,
            keywords = "expenses money cost ค่าใช้จ่าย ค่านม ค่าแพมเพิส การเงิน",
            category = QuickActionCategory.TOOLS,
            iconTint = Color(0xFF43A047),
            iconBg = Color(0xFFE8F5E9),
            subtitleTh = "จัดการงบประมาณค่าเลี้ยงดู",
            subtitleEn = "Childcare expense tracker"
        ),
        QuickAction(
            route = Screen.WidgetsIntro.route,
            labelTh = "วิดเจ็ตหน้าจอหลัก",
            labelEn = "Home Screen Widgets",
            icon = Icons.Filled.Widgets,
            keywords = "widget home screen วิดเจ็ต ทางลัด หน้าจอ",
            category = QuickActionCategory.TOOLS,
            iconTint = Color(0xFF00ACC1),
            iconBg = Color(0xFFE0F7FA),
            subtitleTh = "ติดตั้งวิดเจ็ตบนหน้าจอโทรศัพท์",
            subtitleEn = "Quick-log widget setup"
        ),
        QuickAction(
            route = Screen.Notifications.route,
            labelTh = "ศูนย์การแจ้งเตือน",
            labelEn = "Notifications",
            icon = Icons.Filled.Notifications,
            keywords = "alerts reminders แจ้งเตือน เตือน",
            category = QuickActionCategory.TOOLS,
            iconTint = Color(0xFFD81B60),
            iconBg = Color(0xFFFCE4EC),
            subtitleTh = "การแจ้งเตือนและข้อความสำคัญ",
            subtitleEn = "Alerts & updates"
        ),
        QuickAction(
            route = Screen.Settings.route,
            labelTh = "การตั้งค่าระบบ",
            labelEn = "Settings",
            icon = Icons.Filled.Settings,
            keywords = "settings preferences ตั้งค่า บัญชี ภาษา ธีม",
            category = QuickActionCategory.TOOLS,
            iconTint = Color(0xFF546E7A),
            iconBg = Color(0xFFECEFF1),
            subtitleTh = "ภาษา / ความปลอดภัย / การแจ้งเตือน",
            subtitleEn = "Language & app preferences"
        )
    )

    /** Case-insensitive substring match on Thai/EN labels + keywords. Filterable by category. */
    fun search(rawQuery: String, category: QuickActionCategory? = null): List<QuickAction> {
        val filteredByCategory = if (category == null || category == QuickActionCategory.ALL) all
        else all.filter { it.category == category }

        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return filteredByCategory

        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return filteredByCategory.filter { action ->
            val haystack = "${action.labelTh} ${action.labelEn} ${action.keywords} ${action.subtitleTh.orEmpty()}".lowercase()
            haystack.contains(q) || tokens.any { haystack.contains(it) }
        }
    }

    /**
     * Intelligent context-aware AI recommendation generator based on time of day & child state.
     */
    fun getAiRecommended(
        hourOfDay: Int = LocalTime.now().hour,
        isIll: Boolean = false,
        activeSleepSession: Boolean = false
    ): List<QuickAction> {
        val routes = when {
            isIll -> listOf(
                Screen.AddHealthRecord.route,
                Screen.MedicationHistory.route,
                Screen.MilkRecord.route,
                Screen.ChildAiSummary.route
            )
            activeSleepSession -> listOf(
                Screen.SleepTracker.route,
                Screen.MilkRecord.route,
                Screen.WasteRecord.route,
                Screen.SleepGuidance.route
            )
            hourOfDay in 5..10 -> listOf(
                Screen.MilkRecord.route,
                Screen.CalorieCalculator.route,
                Screen.AddGrowthRecord.route,
                Screen.OralHealth.route
            )
            hourOfDay in 11..16 -> listOf(
                Screen.CalorieCalculator.route,
                Screen.SleepTracker.route,
                Screen.MilkRecord.route,
                Screen.ChildDevelopment.route
            )
            hourOfDay in 17..21 -> listOf(
                Screen.CalorieCalculator.route,
                Screen.OralHealth.route,
                Screen.ChildDiary.route,
                Screen.SleepTracker.route
            )
            else -> listOf(
                Screen.SleepTracker.route,
                Screen.MilkRecord.route,
                Screen.WasteRecord.route,
                Screen.SleepGuidance.route
            )
        }

        return routes.mapNotNull { r -> all.find { it.route == r } }.distinctBy { it.route }
    }

    // ---------------------------------------------------------------------------------------------
    // Home page "บริการแนะนำ" (recommended services)
    // ---------------------------------------------------------------------------------------------

    /** Utilities rather than services - nobody needs Settings "recommended" to them. */
    private val NOT_RECOMMENDABLE: Set<String> = setOf(
        Screen.Settings.route,
        Screen.Notifications.route,
        Screen.WidgetsIntro.route,
        Screen.PermissionAccess.route
    )

    /**
     * Child ages (months, inclusive) where a destination is worth suggesting. A route absent from
     * this map suits any age. Only destinations whose usefulness genuinely depends on age are here.
     */
    private val AGE_WINDOWS: Map<String, IntRange> = mapOf(
        Screen.MilkRecord.route to 0..36,
        Screen.WasteRecord.route to 0..48,
        // Solid food starts around six months.
        Screen.CalorieCalculator.route to 6..Int.MAX_VALUE,
        Screen.CalorieCheck.route to 6..Int.MAX_VALUE,
        Screen.CalorieSummary.route to 6..Int.MAX_VALUE,
        Screen.RecommendedFood.route to 6..Int.MAX_VALUE,
        Screen.RefrigeratorScanner.route to 6..Int.MAX_VALUE,
        Screen.FoodHistory.route to 6..Int.MAX_VALUE,
        // The first tooth typically erupts around six months.
        Screen.OralHealth.route to 6..Int.MAX_VALUE,
        Screen.RecordOralHealthEvent.route to 6..Int.MAX_VALUE,
        Screen.Exercise.route to 12..Int.MAX_VALUE,
        // EPDS check-in for the mother: the postpartum year.
        Screen.MentalHealthAssessment.route to 0..12
    )

    /**
     * Picks [count] destinations for the home page's recommended-services row.
     *
     * - [ageMonths] null means the birthdate is unknown, so nothing is filtered by age. It is NOT
     *   treated as a newborn: VaccineSetupCatalog.ageMonthsFromBirthdate returns 0 for a blank date,
     *   which would push newborn-only suggestions such as the postpartum check-in at a toddler's parent.
     * - [excludeRoutes] are already visible on the page. They are skipped while enough alternatives
     *   exist and used to back-fill otherwise, so the row is never short.
     * - [seed] makes the pick stable for a given value. Recomposition must not reshuffle the cards
     *   under the user's finger; the caller changes the seed to re-roll on purpose.
     * - Routes with `{placeholders}` are skipped: opening one without arguments would crash.
     */
    fun recommendForHome(
        ageMonths: Int?,
        excludeRoutes: Set<String>,
        seed: Long,
        count: Int = 4
    ): List<QuickAction> {
        val eligible = all
            .filter { '{' !in it.route && it.route !in NOT_RECOMMENDABLE }
            .filter { a -> ageMonths == null || AGE_WINDOWS[a.route]?.contains(ageMonths) ?: true }
            .distinctBy { it.route }
        val rng = kotlin.random.Random(seed)
        val fresh = eligible.filter { it.route !in excludeRoutes }.shuffled(rng)
        if (fresh.size >= count) return fresh.take(count)
        val backfill = eligible.filter { it.route in excludeRoutes }.shuffled(rng)
        return (fresh + backfill).take(count)
    }
}

