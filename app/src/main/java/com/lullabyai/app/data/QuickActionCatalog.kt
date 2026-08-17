package com.lullabyai.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.lullabyai.app.ui.navigation.Screen

/**
 * Every real in-app destination, searchable by keyword ("Quick Action" search). Deliberately
 * excludes pre-login/onboarding-flow-only screens (Welcome, Login, SignUp, ForgotPassword,
 * ForceChangePassword, MedicalDisclaimer, SyncChild, OnboardingStep1-3, LastSetup, FirstRunTour,
 * Maintenance) — those aren't independently-reachable "actions" a logged-in user would search
 * for, they're steps in a fixed flow. Every other Screen.kt route is represented once.
 *
 * Route strings only, all flat (no path args) — matches how every screen in this app is
 * registered in AppNavigation.kt, so `navController.navigate(action.route)` always works.
 */
data class QuickAction(
    val route: String,
    val labelTh: String,
    val labelEn: String,
    val icon: ImageVector,
    val keywords: String   // extra searchable synonyms, space-separated, not shown in UI
)

object QuickActionCatalog {
    val all: List<QuickAction> = listOf(
        QuickAction(Screen.Dashboard.route, "หน้าหลัก", "Home", Icons.Filled.Home, "dashboard home โฮม หน้าแรก"),
        QuickAction(Screen.AiChat.route, "คุยกับ AI", "Chat with AI", Icons.Filled.AutoAwesome, "chatbot assistant น้องรัก nongrak แชท chat ai ถาม"),
        QuickAction(Screen.ChildSummary.route, "ข้อมูลลูก", "Child Info", Icons.Filled.ChildCare, "profile overview ภาพรวม ลูก child"),
        QuickAction(Screen.ChildDevelopment.route, "พัฒนาการ", "Development", Icons.Filled.TrendingUp, "milestones skills ทักษะ กิจกรรม"),
        QuickAction(Screen.ChildHealth.route, "สุขภาพ", "Health", Icons.Filled.HealthAndSafety, "health allergy โรคประจำตัว ภูมิแพ้ health เจ็บป่วย"),
        QuickAction(Screen.VaccineBook.route, "สมุดวัคซีน", "Vaccine Book", Icons.Filled.Vaccines, "vaccine immunization วัคซีน ฉีด vaccine"),
        QuickAction(Screen.ChronicRegistry.route, "โรคประจำตัวและการป้องกัน", "Chronic Conditions", Icons.Filled.HealthAndSafety, "chronic disease prevention registry โรคประจำตัว ป้องกัน เสี่ยง risk"),
        QuickAction(Screen.WeeklyHealthReport.route, "รายงานสุขภาพรายสัปดาห์", "Weekly Health Report", Icons.Filled.Assessment, "report weekly รายงาน สัปดาห์"),
        QuickAction(Screen.MonthlyReport.route, "รายงานประจำเดือน", "Monthly Report", Icons.Filled.CalendarMonth, "report monthly summary รายงาน เดือน"),
        QuickAction(Screen.Settings.route, "การตั้งค่า", "Settings", Icons.Filled.Settings, "settings preferences ตั้งค่า"),
        QuickAction(Screen.EditProfile.route, "แก้ไขโปรไฟล์", "Edit Profile", Icons.Filled.Person, "profile account edit โปรไฟล์"),
        QuickAction(Screen.HelpFaq.route, "ช่วยเหลือ", "Help & FAQ", Icons.Filled.HelpOutline, "faq support help ช่วย คำถาม"),
        QuickAction(Screen.ChangePassword.route, "เปลี่ยนรหัสผ่าน", "Change Password", Icons.Filled.Lock, "password security รหัสผ่าน"),
        QuickAction(Screen.Notifications.route, "การแจ้งเตือน", "Notifications", Icons.Filled.Notifications, "alerts reminders แจ้งเตือน"),
        QuickAction(Screen.WidgetsIntro.route, "วิดเจ็ตหน้าจอหลัก", "Home Screen Widgets", Icons.Filled.Widgets, "widget home screen วิดเจ็ต"),
        QuickAction(Screen.AboutUs.route, "เกี่ยวกับเรา", "About Us", Icons.Filled.Info, "about app info เกี่ยวกับ"),
        QuickAction(Screen.PrivacyPolicy.route, "นโยบายความเป็นส่วนตัว", "Privacy Policy", Icons.Filled.PrivacyTip, "privacy pdpa ความเป็นส่วนตัว"),
        QuickAction(Screen.TermsOfUse.route, "ข้อกำหนดการใช้งาน", "Terms of Use", Icons.Filled.Description, "terms legal ข้อกำหนด"),
        QuickAction(Screen.EditChild.route, "แก้ไขข้อมูลเด็ก", "Edit Child", Icons.Filled.Edit, "edit child profile update แก้ไขลูก"),
        QuickAction(Screen.AddHealthRecord.route, "เพิ่มบันทึกสุขภาพ", "Add Health Record", Icons.Filled.AddCircle, "log health fever ไข้ อาการ บันทึกสุขภาพ"),
        QuickAction(Screen.TaskHistory.route, "ประวัติงาน AI", "AI Task History", Icons.Filled.Checklist, "tasks ai history งาน"),
        QuickAction(Screen.DevelopmentHistory.route, "ประวัติพัฒนาการ", "Development History", Icons.Filled.History, "milestones history ประวัติพัฒนาการ"),
        QuickAction(Screen.AllergyHistory.route, "ประวัติภูมิแพ้", "Allergy History", Icons.Filled.Warning, "allergy แพ้ ภูมิแพ้"),
        QuickAction(Screen.Nutrition.route, "โภชนาการ", "Nutrition", Icons.Filled.Restaurant, "food nutrient สารอาหาร อาหาร เมนู กิน"),
        QuickAction(Screen.CalorieCalculator.route, "คำนวณแคลอรี่", "Calorie Calculator", Icons.Filled.Calculate, "calorie calculator แคลอรี่ คำนวณ"),
        QuickAction(Screen.CalorieSummary.route, "สรุปแคลอรี่", "Calorie Summary", Icons.Filled.LocalFireDepartment, "calorie summary today สรุปแคลอรี่"),
        QuickAction(Screen.CalorieCheck.route, "ตรวจสอบแคลอรี่", "Calorie Check", Icons.Filled.Bolt, "calorie check dish food ตรวจแคลอรี่"),
        QuickAction(Screen.EditFoodEntries.route, "แก้ไขรายการอาหาร", "Edit Food Entries", Icons.Filled.EditNote, "edit food log แก้ไขอาหาร"),
        QuickAction(Screen.RecommendedFood.route, "อาหารที่แนะนำ", "Recommended Food", Icons.Filled.Restaurant, "recommended food menu เมนูแนะนำ"),
        QuickAction(Screen.ProfileHistory.route, "ประวัติข้อมูลส่วนตัว", "Profile History", Icons.Filled.History, "profile history monthly"),
        QuickAction(Screen.Exercise.route, "ออกกำลังกาย", "Exercise", Icons.Filled.FitnessCenter, "exercise activity ออกกำลัง ออกกำลังกาย"),
        QuickAction(Screen.MedicationHistory.route, "ประวัติการใช้ยา", "Medication History", Icons.Filled.Medication, "medicine dose history ยา"),
        QuickAction(Screen.AddMedicineSchedule.route, "เพิ่มตารางยา", "Add Medicine Schedule", Icons.Filled.AddAlarm, "add medicine schedule dose ตารางยา"),
        QuickAction(Screen.MedicalDocumentHistory.route, "เอกสารทางการแพทย์", "Medical Documents", Icons.Filled.FolderShared, "documents upload files เอกสาร"),
        QuickAction(Screen.SleepTracker.route, "ติดตามการนอน", "Sleep Tracker", Icons.Filled.Bedtime, "sleep การนอน นอน หลับ"),
        // "log sleep" = an actual night, so this points at Record Sleep History. Add Sleep Time
        // writes a recurring schedule and is reached from the schedule screen.
        QuickAction(com.lullabyai.app.ui.screens.sleep.RECORD_SLEEP_HISTORY_ROUTE, "บันทึกการนอนย้อนหลัง", "Record Sleep History", Icons.Filled.Alarm, "log sleep add บันทึกนอน"),
        QuickAction(Screen.SleepSchedule.route, "ตารางการนอน", "Sleep Schedule", Icons.Filled.Schedule, "sleep schedule nap ตารางนอน"),
        QuickAction(Screen.SleepGuidance.route, "คำแนะนำการนอน", "Sleep Guidance", Icons.Filled.NightsStay, "sleep tips guidance คำแนะนำนอน"),
        QuickAction(Screen.QuickLogHub.route, "บันทึกด่วน", "Quick Log", Icons.Filled.FlashOn, "quick log shortcut บันทึกด่วน"),
        QuickAction(Screen.SmartTrends.route, "แนวโน้มอัจฉริยะ", "Smart Trends", Icons.Filled.Insights, "trends analysis ai แนวโน้ม"),
        QuickAction(Screen.StatisticsInsights.route, "สถิติและข้อมูลเชิงลึก", "Statistics & Insights", Icons.Filled.BarChart, "stats statistics insights สถิติ"),
        QuickAction(Screen.FoodHistory.route, "ประวัติอาหาร", "Food History", Icons.Filled.History, "food log history ประวัติอาหาร"),
        QuickAction(Screen.ExerciseHistory.route, "ประวัติการออกกำลังกาย", "Exercise History", Icons.Filled.History, "exercise log history"),
        QuickAction(Screen.FullMedicationHistory.route, "ประวัติยาทั้งหมด", "Full Medication History", Icons.Filled.History, "medicine full history ประวัติยา"),
        QuickAction(Screen.HealthHistory.route, "ประวัติสุขภาพ", "Health History", Icons.Filled.History, "health log history ประวัติสุขภาพ"),
        QuickAction(Screen.GrowthHistory.route, "ประวัติการเจริญเติบโต", "Growth History", Icons.Filled.MonitorWeight, "weight height น้ำหนัก ส่วนสูง เจริญเติบโต growth"),
        QuickAction(Screen.AddGrowthRecord.route, "อัปเดตน้ำหนักส่วนสูง", "Update Weight & Height", Icons.Filled.Straighten, "add growth weight height น้ำหนัก ส่วนสูง"),
        QuickAction(Screen.MentalHealthAssessment.route, "แบบประเมินสุขภาพจิต", "Mental Health Assessment", Icons.Filled.Psychology, "mental health parent epds สุขภาพจิต"),
        QuickAction(Screen.MentalHealthResult.route, "ผลประเมินสุขภาพจิต", "Mental Health Result", Icons.Filled.Psychology, "mental health result"),
        QuickAction(Screen.ChildAiSummary.route, "ตรวจสอบข้อมูลสุขภาพ", "AI Child Summary", Icons.Filled.Summarize, "ai summary check health สรุป"),
        QuickAction(Screen.WasteRecord.route, "บันทึกขับถ่าย", "Waste Record", Icons.Filled.WaterDrop, "diaper urine stool ขับถ่าย ผ้าอ้อม"),
        QuickAction(Screen.MedicalDataVault.route, "คลังเอกสาร (Vault)", "Document Vault", Icons.Filled.Folder, "medical vault documents คลังเอกสาร files"),
        QuickAction(Screen.AiDataRetention.route, "ข้อมูลที่ AI เก็บไว้", "Data the AI keeps", Icons.Filled.Storage, "ai retention privacy pdpa ลบข้อมูล"),
        QuickAction(Screen.MilkRecord.route, "บันทึกการดื่มนม", "Milk Record", Icons.Filled.BabyChangingStation, "milk feeding log นม ให้นม"),
        QuickAction(Screen.NutritionInsights.route, "ข้อมูลเชิงลึกโภชนาการ", "Nutrition Insights", Icons.Filled.Insights, "nutrition ai insight"),
        QuickAction(Screen.SleepActivityInsights.route, "ข้อมูลเชิงลึกการนอน", "Sleep & Activity Insights", Icons.Filled.Insights, "sleep activity ai insight"),
        QuickAction(Screen.HealthEliminationInsights.route, "ข้อมูลเชิงลึกการขับถ่าย", "Elimination Insights", Icons.Filled.Insights, "elimination ai insight"),
        QuickAction(Screen.WhoWeightForAgeDetail.route, "กราฟน้ำหนักตามอายุ", "WHO Weight-for-Age", Icons.Filled.ShowChart, "who growth chart weight กราฟ น้ำหนัก"),
        QuickAction(Screen.WhoHeightForAgeDetail.route, "กราฟส่วนสูงตามอายุ", "WHO Height-for-Age", Icons.Filled.ShowChart, "who growth chart height กราฟ ส่วนสูง"),
        QuickAction(Screen.AddChildChoice.route, "เพิ่มลูก", "Add Child", Icons.Filled.PersonAdd, "add new child เพิ่มลูก sync ซิงค์"),
        QuickAction(Screen.OralHealth.route, "สุขภาพช่องปาก", "Oral Health", Icons.Filled.HealthAndSafety, "oral health teeth dentistry ฟัน ช่องปาก แปรงฟัน แผนผังฟัน หมอฟัน"),
        QuickAction(Screen.RecordOralHealthEvent.route, "บันทึกเหตุการณ์ช่องปาก", "Record Oral Event", Icons.Filled.AddCircle, "oral event teeth log บันทึกฟัน ฟันซี่แรก เคลือบฟลูออไรด์")
    )

    /** Case-insensitive substring match on Thai/EN labels + keywords. Blank query → full catalog. */
    fun search(rawQuery: String): List<QuickAction> {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return all
        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return all.filter { action ->
            val haystack = "${action.labelTh} ${action.labelEn} ${action.keywords}".lowercase()
            haystack.contains(q) || tokens.any { haystack.contains(it) }
        }
    }
}
