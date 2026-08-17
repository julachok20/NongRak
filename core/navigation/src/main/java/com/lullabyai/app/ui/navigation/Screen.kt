package com.lullabyai.app.ui.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    object ForceChangePassword : Screen("force_change_password")
    object MedicalDisclaimer : Screen("medical_disclaimer")
    object AddChildChoice : Screen("add_child_choice")
    object SyncChild : Screen("sync_child")
    object UploadBirthCertificate : Screen("upload_birth_certificate")
    object OnboardingStep1 : Screen("onboarding_step1")
    object OnboardingStep2 : Screen("onboarding_step2")
    object OnboardingStep3 : Screen("onboarding_step3")
    object LastSetup : Screen("last_setup")
    object MascotSelect : Screen("mascot_select")

    /**
     * The same picker, reached from chat rather than onboarding.
     *
     * A separate route because the destination is what differs: onboarding continues into the
     * first-run tour and clears the stack, while switching mid-conversation must return to the
     * conversation it was opened from.
     */
    object MascotSwitch : Screen("mascot_switch")
    object FirstRunTour : Screen("first_run_tour")
    object DoctorFirstRunTour : Screen("doctor_first_run_tour")
    object Dashboard : Screen("dashboard")
    object AiChat : Screen("ai_chat")
    object ChildSummary : Screen("child_summary")
    object ChildDevelopment : Screen("child_development")
    object ChildHealth : Screen("child_health")
    object VaccineBook : Screen("vaccine_book")
    /** @deprecated Retire Disease Risk AI % UI — use [ChronicRegistry]. Kept only so old deep-links redirect. */
    @Deprecated("Replaced by ChronicRegistry (#14)", ReplaceWith("ChronicRegistry"))
    object DiseaseRisk : Screen("disease_risk")
    /** Stitch Chronic Disease & Prevention registry — named conditions + prevention + emergency call. */
    object ChronicRegistry : Screen("chronic_registry")
    object WeeklyHealthReport : Screen("weekly_health_report")
    object Settings : Screen("settings")
    object EditProfile : Screen("edit_profile")
    object HelpFaq : Screen("help_faq")
    object ChangePassword : Screen("change_password")
    object Notifications : Screen("notifications")
    object WidgetsIntro : Screen("widgets_intro")
    object AboutUs : Screen("about_us")
    object PrivacyPolicy : Screen("privacy_policy")
    object TermsOfUse : Screen("terms_of_use")
    object EditChild : Screen("edit_child")
    object AddHealthRecord : Screen("add_health_record")
    object HealthDetail : Screen("health_detail")
    object TaskHistory : Screen("task_history")
    object DevelopmentHistory : Screen("development_history")
    object AllergyHistory : Screen("allergy_history")
    // Nutrition / Calorie / Exercise feature
    object Nutrition : Screen("nutrition")
    object CalorieCalculator : Screen("calorie_calculator")
    object CalorieSummary : Screen("calorie_summary")
    object CalorieCheck : Screen("calorie_check")
    object EditFoodEntries : Screen("edit_food_entries")
    object ProfileHistory : Screen("profile_history")
    object Exercise : Screen("exercise")
    object LogExercise : Screen("log_exercise")
    object ExerciseInstruction : Screen("exercise_instruction")
    object RecipeDetail : Screen("recipe_detail")
    object Maintenance : Screen("maintenance")
    object MedicationHistory : Screen("medication_history")
    object AddMedicineSchedule : Screen("add_medicine_schedule")
    object MedicalDocumentHistory : Screen("medical_document_history")
    object SleepTracker : Screen("sleep_tracker")
    /** Schedule editor. Optional `scheduleId` prefills an existing row for edit; omitted = create. */
    object AddSleepTime : Screen("add_sleep_time?scheduleId={scheduleId}") {
        fun create() = "add_sleep_time"
        fun edit(scheduleId: String) = "add_sleep_time?scheduleId=$scheduleId"
    }
    object SleepSchedule : Screen("sleep_schedule")
    object SleepGuidance : Screen("sleep_guidance")
    object QuickLogHub : Screen("quick_log_hub")
    object SmartTrends : Screen("smart_trends")
    object StatisticsInsights : Screen("statistics_insights")
    object FoodHistory : Screen("food_history")
    object ExerciseHistory : Screen("exercise_history")
    object FullMedicationHistory : Screen("full_medication_history")
    object HealthHistory : Screen("health_history")
    object PdfExport : Screen("pdf_export")
    object SicknessFollowUp : Screen("sickness_follow_up")
    object LogSicknessSymptoms : Screen("log_sickness_symptoms")
    object GrowthHistory : Screen("growth_history")
    object SiblingCompare : Screen("sibling_compare")
    object CameraScan : Screen("camera_scan")
    object VoiceCall : Screen("voice_call")
    object HealthLog : Screen("health_log")
    object AddGrowthRecord : Screen("add_growth_record")
    object MentalHealthAssessment : Screen("mental_health_assessment")
    object MentalHealthResult : Screen("mental_health_result")
    object PostpartumCheckIn : Screen("postpartum_checkin")
    object PostpartumResult : Screen("postpartum_result")
    object ChildAiSummary : Screen("child_ai_summary")
    object WasteRecord : Screen("waste_record")
    object MedicalDataVault : Screen("medical_data_vault")
    /** AI retention / transparency (former MedicalDataVault contents). Append-only 2026-07-25. */
    object AiDataRetention : Screen("ai_data_retention")
    object MilkRecord : Screen("milk_record")
    object NutritionInsights : Screen("nutrition_insights")
    object SleepActivityInsights : Screen("sleep_activity_insights")
    object HealthEliminationInsights : Screen("health_elimination_insights")
    object WhoWeightForAgeDetail : Screen("who_weight_for_age_detail")
    object WhoHeightForAgeDetail : Screen("who_height_for_age_detail")
    object MonthlyReport : Screen("monthly_report")
    object QuickActionSearch : Screen("quick_action_search")
    object RecommendedFood : Screen("recommended_food")
    // Doctor Appointment Record ("บันทึกการหาหมอ")
    object DoctorVisitHistory : Screen("doctor_visit_history")
    object RecordAppointment : Screen("record_appointment")
    object RecordDoctorVisit : Screen("record_doctor_visit")
    object VisitDetails : Screen("visit_details")

    // --- Doctor role flow (net-new, 2026-07-25) ---
    // Doctor-side auth/onboarding
    object DoctorWelcome : Screen("doctor_welcome")
    object DoctorSignUp : Screen("doctor_signup")
    /** Post-Google profile completion (Stitch 44.Regis-Google). */
    object DoctorGoogleCompleteProfile : Screen("doctor_google_complete_profile")
    /** Real email/password form — see DoctorSignInScreen. */
    object DoctorSignIn : Screen("doctor_signin")
    object DoctorForgotPassword : Screen("doctor_forgot_password")
    /** Stitch "Signing In" loading/transition only — not a credentials form. */
    object DoctorLogin : Screen("doctor_login")
    object DoctorSettings : Screen("doctor_settings")
    object DoctorEditProfile : Screen("doctor_edit_profile")
    object DoctorWeeklyHours : Screen("doctor_weekly_hours")
    object DoctorHelpFaq : Screen("doctor_help_faq")
    object DoctorAboutNongRak : Screen("doctor_about_nongrak")
    // Doctor-owned routes onto pages whose CONTENT is shared with the parent side. Separate routes
    // rather than reusing the parent ones: the parent screens draw AppBottomNav, so a doctor who
    // opened them from Doctor Settings was dropped onto the parent footer with no way back.
    object DoctorChangePassword : Screen("doctor_change_password")
    object DoctorNotifications : Screen("doctor_notifications")
    object DoctorPrivacyPolicy : Screen("doctor_privacy_policy")
    // Doctor-side operational screens
    object DoctorDashboard : Screen("doctor_dashboard")
    object DoctorPatientDirectory : Screen("doctor_patient_directory")
    object DoctorPatientInfo : Screen("doctor_patient_info/{childId}") {
        fun route(childId: String) = "doctor_patient_info/$childId"
    }
    object DoctorPatientVaccineHistory : Screen("doctor_patient_vaccine_history/{childId}") {
        fun route(childId: String) = "doctor_patient_vaccine_history/$childId"
    }
    object DoctorClinicalRecordEntry : Screen("doctor_clinical_record_entry/{appointmentId}") {
        fun route(appointmentId: String) = "doctor_clinical_record_entry/$appointmentId"
    }
    object DoctorPatientSchedule : Screen("doctor_patient_schedule")
    object DoctorManageSlots : Screen("doctor_manage_slots")
    object DoctorAddAppointment : Screen("doctor_add_appointment")
    object DoctorPatientRegistrationApproval : Screen("doctor_patient_registration_approval")
    object DoctorPatientRegistrationDetail : Screen("doctor_patient_registration_detail/{appointmentId}") {
        fun route(appointmentId: String) = "doctor_patient_registration_detail/$appointmentId"
    }
    object DoctorVaccineVerificationHub : Screen("doctor_vaccine_verification_hub")
    object DoctorVaccineCertificatePreview : Screen("doctor_vaccine_certificate_preview")
    object DoctorVaccineVerificationSent : Screen("doctor_vaccine_verification_sent")
    object DoctorPatientManagement : Screen("doctor_patient_management")
    object DoctorManualPatientRegister : Screen("doctor_manual_patient_register")

    /** Doctor-authored walk-in registration form (Stitch "Patient Registration with Appointment Details"). */
    object DoctorPatientRegistrationForm : Screen("doctor_patient_registration_form")
    object DoctorTimeAppointmentHub : Screen("doctor_time_appointment_hub")
    object DoctorSpecialDates : Screen("doctor_special_dates")

    // Parent-side doctor directory/booking (net-new, same batch — was routed to Maintenance before)
    object SearchDoctor : Screen("search_doctor")
    object MyDoctorsHub : Screen("my_doctors_hub")
    object DoctorReservation : Screen("doctor_reservation/{doctorId}") {
        fun route(doctorId: String) = "doctor_reservation/$doctorId"
    }
    object ConnectToDoctorWelcome : Screen("connect_to_doctor_welcome")
    object ConnectServicesSelection : Screen("connect_services_selection")
    object LabResults : Screen("lab_results")
    object PatientRegistration : Screen("patient_registration/{doctorId}") {
        fun route(doctorId: String) = "patient_registration/$doctorId"
    }
    object PediaConnectDashboard : Screen("pedia_connect_dashboard")
    object PediaConnectChooseChild : Screen("pedia_connect_choose_child")
    object AppointmentDetails : Screen("appointment_details/{appointmentId}") {
        fun route(appointmentId: String) = "appointment_details/$appointmentId"
    }

    // --- Wearables / App Connect (#11, 2026-07-25) ---
    object AppConnectSetup : Screen("app_connect_setup")
    object ChildDeviceHub : Screen("child_device_hub")

    // --- Diary / Special Memories (#10, 2026-07-25) — append-only ---
    object ChildDiary : Screen("child_diary")
    object RewindMemory : Screen("rewind_memory")
    object SpecialMemoriesGallery : Screen("special_memories_gallery")
    object AddMemory : Screen("add_memory")

    // --- Access / permissions suite (#5 C, 2026-07-25) — append-only unique names ---
    object PermissionAccess : Screen("permission_access")
    object AddUserAccessActivity : Screen("add_user_access_activity")
    object PermissionSettingsDetail : Screen("permission_settings_detail/{userId}") {
        fun route(userId: String) = "permission_settings_detail/$userId"
    }
    object ShareAccessQr : Screen("share_access_qr")

    /** Custom capability bundles (#5). Owner-only writes, enforced in Postgres. */
    object RoleManagement : Screen("role_management")

    // --- Child sync + QR approval (#4). A scan raises a PENDING request; the owner approves or
    // denies it via a Realtime popup, or from PendingAccessRequests if the app was closed. ---
    object SyncChildProfile : Screen("sync_child_profile")
    object ActivityHistory : Screen("activity_history")
    object CaregiverUpdate : Screen("caregiver_update")
    object Expenses : Screen("expenses")
    /** Always records against the child whose Cost Record was opened — no child argument. */
    object AddExpense : Screen("add_expense")
    /** The full searchable list behind the summary's "ดูทั้งหมด". */
    object ExpenseHistory : Screen("expense_history")

    /** ตารางนัดหมายและวันสำคัญ — the Day/Week/Month calendar, reached from Child info's ปฏิทิน tile. */
    object ActivitySchedule : Screen("activity_schedule")
    object AddActivity : Screen("add_activity")
    object CareCircle : Screen("care_circle")
    /**
     * `quote` is optional context carried in from an activity entry, so a reply arrives saying
     * which update it is about instead of a bare "?".
     */
    object CareChat : Screen("care_chat/{peerId}?quote={quote}") {
        fun route(peerId: String) = "care_chat/$peerId?quote="
        fun route(peerId: String, quote: String) =
            "care_chat/$peerId?quote=" + java.net.URLEncoder.encode(quote, "UTF-8")
    }
    object PendingAccessRequests : Screen("pending_access_requests")

    // --- Oral Health Pro System ---
    object OralHealth : Screen("child_oral_health")
    object RecordOralHealthEvent : Screen("child_oral_health_record")
}
