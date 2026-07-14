package com.lullabyai.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lullabyai.app.ui.screens.auth.LoginScreen
import com.lullabyai.app.ui.screens.auth.SignUpScreen
import com.lullabyai.app.ui.screens.chat.AIChatScreen
import com.lullabyai.app.ui.screens.child.ChildDevelopmentScreen
import com.lullabyai.app.ui.screens.child.ChildHealthScreen
import com.lullabyai.app.ui.screens.child.ChildSummaryScreen
import com.lullabyai.app.ui.screens.child.DiseaseRiskScreen
import com.lullabyai.app.ui.screens.child.VaccineBookScreen
import com.lullabyai.app.ui.screens.child.MedicationHistoryScreen
import com.lullabyai.app.ui.screens.child.AddMedicineScheduleScreen
import com.lullabyai.app.ui.screens.child.MedicalDocumentHistoryScreen
import com.lullabyai.app.ui.screens.sleep.SleepTrackerScreen
import com.lullabyai.app.ui.screens.sleep.AddSleepTimeScreen
import com.lullabyai.app.ui.screens.sleep.SleepScheduleScreen
import com.lullabyai.app.ui.screens.sleep.SleepGuidanceScreen
import com.lullabyai.app.ui.screens.common.MaintenanceScreen
import com.lullabyai.app.ui.screens.dashboard.DashboardScreen
import com.lullabyai.app.ui.screens.growth.GrowthMetricsScreen
import com.lullabyai.app.ui.screens.help.HelpFaqScreen
import com.lullabyai.app.ui.screens.onboarding.AddChildChoiceScreen
import com.lullabyai.app.ui.screens.onboarding.SyncChildScreen
import com.lullabyai.app.ui.screens.onboarding.LastSetupScreen
import com.lullabyai.app.ui.screens.onboarding.OnboardingStep1Screen
import com.lullabyai.app.ui.screens.onboarding.OnboardingStep2Screen
import com.lullabyai.app.ui.screens.onboarding.OnboardingStep3Screen
import com.lullabyai.app.ui.screens.profile.EditProfileScreen
import com.lullabyai.app.ui.screens.settings.AboutUsScreen
import com.lullabyai.app.ui.screens.settings.ChangePasswordScreen
import com.lullabyai.app.ui.screens.settings.NotificationsScreen
import com.lullabyai.app.ui.screens.settings.PrivacyPolicyScreen
import com.lullabyai.app.ui.screens.settings.SettingsScreen
import com.lullabyai.app.ui.screens.settings.TermsOfUseScreen
import com.lullabyai.app.ui.screens.welcome.WelcomeScreen
import com.lullabyai.app.ui.screens.nutrition.NutritionScreen
import com.lullabyai.app.ui.screens.nutrition.CalorieCalculatorScreen
import com.lullabyai.app.ui.screens.nutrition.CalorieSummaryScreen
import com.lullabyai.app.ui.screens.nutrition.EditFoodEntriesScreen
import com.lullabyai.app.ui.screens.exercise.ExerciseScreen
import com.lullabyai.app.ui.theme.NongRakTheme
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.OnboardingViewModel
import com.lullabyai.app.viewmodel.NutritionViewModel
import com.lullabyai.app.viewmodel.SettingsViewModel
import com.lullabyai.app.data.settings.AppLanguage

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel = viewModel(),
    childViewModel: ChildViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    onboardingViewModel: OnboardingViewModel = viewModel(),
    nutritionViewModel: NutritionViewModel = viewModel()
) {
    val appSettings by settingsViewModel.settings.collectAsState()
    val lang = if (appSettings.language == AppLanguage.TH) "th" else "en"
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    // Onboarding theme: forced light during signup; the user's chosen theme when adding a child.
    val onboardingDark = onboardingState.addMode && appSettings.darkMode

    // Guided tour overlay state (see TourOverlay.kt) — drawn on top of the NavHost below, rather than
    // being its own destination screen, so it can walk through the real Dashboard/ChildInfo/Chat/
    // FAQ/Settings pages instead of static slides.
    var tourActive by remember { mutableStateOf(false) }
    var tourStep by remember { mutableIntStateOf(0) }
    val finishTour = {
        tourActive = false
        settingsViewModel.setHasSeenTour(true)
        navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // No cross-fade flash: instant transitions on a themed background
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Screen.Welcome.route) {
            NongRakTheme(darkTheme = false) {
                WelcomeScreen(
                    onSignUp = { navController.navigate(Screen.SignUp.route) },
                    onLogin = { navController.navigate(Screen.Login.route) },
                    settingsViewModel = settingsViewModel
                )
            }
        }
        composable(Screen.Login.route) {
            NongRakTheme(darkTheme = false) {
                LoginScreen(
                    authViewModel = authViewModel,
                    settingsViewModel = settingsViewModel,
                    onLoginSuccess = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } },
                    onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) },
                    onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
                    onNeedChangePassword = { navController.navigate(Screen.ForceChangePassword.route) { popUpTo(0) } },
                    onBack = { navController.navigate(Screen.Welcome.route) { popUpTo(0) } }
                )
            }
        }
        composable(Screen.ForgotPassword.route) {
            NongRakTheme(darkTheme = false) {
                com.lullabyai.app.ui.screens.auth.ForgotPasswordScreen(
                    viewModel = viewModel(),
                    onBackToLogin = { navController.navigate(Screen.Login.route) { popUpTo(0) } }
                )
            }
        }
        composable(Screen.ForceChangePassword.route) {
            NongRakTheme(darkTheme = false) {
                com.lullabyai.app.ui.screens.auth.ForceChangePasswordScreen(
                    authViewModel = authViewModel,
                    onDone = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } }
                )
            }
        }
        composable(Screen.SignUp.route) {
            NongRakTheme(darkTheme = false) {
                SignUpScreen(
                    authViewModel = authViewModel,
                    settingsViewModel = settingsViewModel,
                    // A child is no longer required at signup — land in the app; Dashboard nudges
                    // the (still childless) account to add one via Screen.AddChildChoice.
                    onSignUpSuccess = { onboardingViewModel.reset(); navController.navigate(Screen.FirstRunTour.route) { popUpTo(0) } },
                    onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                    onBack = { navController.navigate(Screen.Welcome.route) { popUpTo(0) } }
                )
            }
        }
        composable(Screen.MedicalDisclaimer.route) {
            NongRakTheme(darkTheme = false) {
                com.lullabyai.app.ui.screens.onboarding.MedicalDisclaimerScreen(
                    onAccept = { navController.navigate(Screen.OnboardingStep1.route) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.AddChildChoice.route) {
            val authState by authViewModel.uiState.collectAsState()
            NongRakTheme(darkTheme = false) {
                AddChildChoiceScreen(
                    onAddNew = {
                        // First-ever child (relationship not set yet) still needs the medical
                        // disclaimer + Last Setup; a subsequent child (addMode) skips both.
                        if (authState.profile?.relationship.isNullOrBlank()) {
                            onboardingViewModel.reset()
                            navController.navigate(Screen.MedicalDisclaimer.route)
                        } else {
                            onboardingViewModel.startAddChild()
                            navController.navigate(Screen.OnboardingStep1.route)
                        }
                    },
                    onSync = { navController.navigate(Screen.SyncChild.route) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.SyncChild.route) {
            NongRakTheme(darkTheme = false) {
                SyncChildScreen(
                    childViewModel = childViewModel,
                    onDone = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.OnboardingStep1.route) {
            NongRakTheme(darkTheme = onboardingDark) {
                OnboardingStep1Screen(
                    onboardingViewModel = onboardingViewModel,
                    onNext = { navController.navigate(Screen.OnboardingStep2.route) },
                    onBack = {
                        if (onboardingState.addMode) {
                            // Adding a child: exit the setup back to the dashboard — never delete anything.
                            navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                        } else {
                            // Cancel signup: sign out and return to Welcome (no data deletion).
                            authViewModel.cancelSignup {
                                navController.navigate(Screen.Welcome.route) { popUpTo(0) }
                            }
                        }
                    }
                )
            }
        }
        composable(Screen.OnboardingStep2.route) {
            NongRakTheme(darkTheme = onboardingDark) {
                OnboardingStep2Screen(
                    onboardingViewModel = onboardingViewModel,
                    onNext = { navController.navigate(Screen.OnboardingStep3.route) },
                    onBack = {
                        if (onboardingState.addMode) navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                        else navController.popBackStack()
                    }
                )
            }
        }
        composable(Screen.OnboardingStep3.route) {
            NongRakTheme(darkTheme = onboardingDark) {
                OnboardingStep3Screen(
                    onboardingViewModel = onboardingViewModel,
                    onNext = {
                        if (onboardingState.addMode) {
                            // Add-child: no username step — save now and go to the dashboard.
                            onboardingViewModel.completeOnboarding {
                                navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                            }
                        } else {
                            navController.navigate(Screen.LastSetup.route)
                        }
                    },
                    onBack = {
                        if (onboardingState.addMode) navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                        else navController.popBackStack()
                    }
                )
            }
        }
        composable(Screen.LastSetup.route) {
            NongRakTheme(darkTheme = onboardingDark) {
                LastSetupScreen(
                    onboardingViewModel = onboardingViewModel,
                    authViewModel = authViewModel,
                    onComplete = { navController.navigate(Screen.FirstRunTour.route) { popUpTo(0) } },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.FirstRunTour.route) {
            // Not its own screen anymore — bounces straight to Dashboard and activates the
            // TourOverlay (drawn below, on top of the NavHost), which walks the real app instead
            // of a separate static-slide destination.
            NongRakTheme(darkTheme = false) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    tourStep = 0
                    tourActive = true
                    navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                }
            }
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                childViewModel = childViewModel,
                chatViewModel = chatViewModel,
                authViewModel = authViewModel,
                onNavigateToChat = { navController.navigate(Screen.AiChat.route) },
                onNavigateToChild = { navController.navigate(Screen.ChildSummary.route) },
                onNavigateToVaccine = { navController.navigate(Screen.VaccineBook.route) },
                onNavigateToDevelopment = { navController.navigate(Screen.ChildDevelopment.route) },
                onNavigateToHealth = { navController.navigate(Screen.ChildHealth.route) },
                onNavigateToGrowth = { navController.navigate(Screen.GrowthMetrics.route) },
                onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToHelp = { navController.navigate(Screen.HelpFaq.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToStats = { navController.navigate(Screen.StatisticsInsights.route) },
                onNavigateToSleep = { navController.navigate(Screen.SleepTracker.route) },
                onNavigateToAddChild = { navController.navigate(Screen.AddChildChoice.route) },
                onNavigateToMentalHealth = { navController.navigate(Screen.MentalHealthAssessment.route) },
                onNavigateToQuickLog = { navController.navigate(Screen.QuickLogHub.route) },
                onNavigateToWidgets = { navController.navigate(Screen.WidgetsIntro.route) },
                isTourActive = tourActive
            )
        }
        composable(Screen.AiChat.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("chat") }
            AIChatScreen(
                chatViewModel = chatViewModel,
                childViewModel = childViewModel,
                onBack = { navController.popBackStack() },
                onNavHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onNavChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onNavSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onNavStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } }
            )
        }
        composable(Screen.ChildSummary.route) {
            ChildSummaryScreen(childViewModel, chatViewModel, navController, onboardingViewModel) { navController.popBackStack() }
        }
        composable(Screen.ChildAiSummary.route) {
            com.lullabyai.app.ui.screens.child.ChildAiSummaryScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.WasteRecord.route) {
            com.lullabyai.app.ui.screens.child.WasteRecordScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.ProfileHistory.route) {
            com.lullabyai.app.ui.screens.child.ProfileHistoryScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.ChildDevelopment.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("development") }
            ChildDevelopmentScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.ChildHealth.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("health") }
            ChildHealthScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.VaccineBook.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("vaccine") }
            VaccineBookScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.DiseaseRisk.route) {
            DiseaseRiskScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.WeeklyHealthReport.route) {
            com.lullabyai.app.ui.screens.child.WeeklyHealthReportScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.Maintenance.route) {
            MaintenanceScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.QuickLogHub.route) {
            com.lullabyai.app.ui.screens.stats.QuickLogHubScreen(childViewModel, chatViewModel, navController)
        }
        composable(Screen.SmartTrends.route) {
            com.lullabyai.app.ui.screens.stats.SmartTrendsScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.StatisticsInsights.route) {
            com.lullabyai.app.ui.screens.stats.StatisticsInsightsScreen(childViewModel, chatViewModel, onboardingViewModel, navController)
        }
        composable(Screen.FoodHistory.route) {
            com.lullabyai.app.ui.screens.nutrition.FoodHistoryScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.ExerciseHistory.route) {
            com.lullabyai.app.ui.screens.exercise.ExerciseHistoryScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.MedicationHistory.route) {
            MedicationHistoryScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.FullMedicationHistory.route) {
            com.lullabyai.app.ui.screens.child.FullMedicationHistoryScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.HealthHistory.route) {
            com.lullabyai.app.ui.screens.child.HealthHistoryScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.AddMedicineSchedule.route) {
            AddMedicineScheduleScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.MedicalDocumentHistory.route) {
            MedicalDocumentHistoryScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.SleepTracker.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("sleep") }
            SleepTrackerScreen(childViewModel, chatViewModel, navController)
        }
        composable(Screen.AddSleepTime.route) {
            AddSleepTimeScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.SleepSchedule.route) {
            SleepScheduleScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.SleepGuidance.route) {
            SleepGuidanceScreen(navController = navController, onBack = { navController.popBackStack() }, onCreateSchedule = { navController.navigate(Screen.SleepSchedule.route) })
        }
        composable(Screen.GrowthMetrics.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("growth") }
            GrowthMetricsScreen(
                childViewModel, chatViewModel, navController,
                onBack = { navController.popBackStack() },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) }
            )
        }
        composable(Screen.GrowthHistory.route) {
            com.lullabyai.app.ui.screens.growth.GrowthHistoryScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.WhoWeightForAgeDetail.route) {
            com.lullabyai.app.ui.screens.growth.WhoWeightForAgeDetailScreen(childViewModel, navController)
        }
        composable(Screen.WhoHeightForAgeDetail.route) {
            com.lullabyai.app.ui.screens.growth.WhoHeightForAgeDetailScreen(childViewModel, navController)
        }
        composable(Screen.NutritionInsights.route) {
            com.lullabyai.app.ui.screens.stats.NutritionInsightsScreen(childViewModel, chatViewModel, navController)
        }
        composable(Screen.SleepActivityInsights.route) {
            com.lullabyai.app.ui.screens.stats.SleepActivityInsightsScreen(childViewModel, chatViewModel, navController)
        }
        composable(Screen.HealthEliminationInsights.route) {
            com.lullabyai.app.ui.screens.stats.HealthEliminationInsightsScreen(childViewModel, chatViewModel, navController)
        }
        composable(Screen.MonthlyReport.route) {
            com.lullabyai.app.ui.screens.stats.MonthlyReportScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.MentalHealthAssessment.route) {
            com.lullabyai.app.ui.screens.mentalhealth.MentalHealthAssessmentScreen(
                authViewModel,
                onDone = { navController.navigate(Screen.MentalHealthResult.route) { popUpTo(Screen.Dashboard.route) } },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.MentalHealthResult.route) {
            com.lullabyai.app.ui.screens.mentalhealth.MentalHealthResultScreen(authViewModel, navController) {
                navController.navigate(Screen.Dashboard.route) { popUpTo(Screen.Dashboard.route) { inclusive = true } }
            }
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                authViewModel = authViewModel,
                settingsViewModel = settingsViewModel,
                navController = navController,
                onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) },
                onLogout = {
                    chatViewModel.clearForSignOut()
                    navController.navigate(Screen.Welcome.route) { popUpTo(0) }
                }
            )
        }
        composable(Screen.EditProfile.route) {
            EditProfileScreen(authViewModel = authViewModel, navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.MedicalDataVault.route) {
            com.lullabyai.app.ui.screens.settings.MedicalDataVaultScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.MilkRecord.route) {
            com.lullabyai.app.ui.screens.nutrition.MilkRecordScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.HelpFaq.route) {
            HelpFaqScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.ChangePassword.route) {
            ChangePasswordScreen(authViewModel = authViewModel, navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.Notifications.route) {
            NotificationsScreen(navController = navController, childViewModel = childViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.AboutUs.route) {
            AboutUsScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.WidgetsIntro.route) {
            com.lullabyai.app.ui.screens.settings.WidgetsIntroScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.TermsOfUse.route) {
            TermsOfUseScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.EditChild.route) {
            com.lullabyai.app.ui.screens.child.EditChildScreen(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AddHealthRecord.route) {
            com.lullabyai.app.ui.screens.child.AddHealthRecordScreen(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.HealthDetail.route) {
            com.lullabyai.app.ui.screens.child.HealthDetailScreen(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.TaskHistory.route) {
            com.lullabyai.app.ui.screens.child.TaskHistoryScreen(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.DevelopmentHistory.route) {
            com.lullabyai.app.ui.screens.child.DevelopmentHistoryScreen(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AllergyHistory.route) {
            com.lullabyai.app.ui.screens.child.AllergyHistoryScreen(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }

        // ---- Nutrition / Calorie / Exercise feature ----
        composable(Screen.Nutrition.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("nutrition") }
            NutritionScreen(nutritionViewModel, lang, navController)
        }
        composable(Screen.CalorieCalculator.route) {
            CalorieCalculatorScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
        }
        composable(Screen.CalorieSummary.route) {
            CalorieSummaryScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
        }
        composable(Screen.CalorieCheck.route) {
            com.lullabyai.app.ui.screens.nutrition.CalorieCheckScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
        }
        composable(Screen.EditFoodEntries.route) {
            EditFoodEntriesScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
        }
        composable(Screen.Exercise.route) {
            ExerciseScreen(nutritionViewModel, lang, navController)
        }
        composable(Screen.ExerciseInstruction.route) {
            com.lullabyai.app.ui.screens.exercise.ExerciseInstructionScreen(nutritionViewModel, lang) { navController.popBackStack() }
        }
        composable(Screen.RecipeDetail.route) {
            com.lullabyai.app.ui.screens.nutrition.RecipeDetailScreen(nutritionViewModel, lang) { navController.popBackStack() }
        }
    }
    if (tourActive) {
        com.lullabyai.app.ui.screens.onboarding.TourOverlay(
            step = tourStep,
            navController = navController,
            isThai = lang == "th",
            onNext = { tourStep++ },
            onBack = { if (tourStep > 0) tourStep-- },
            onSkip = { finishTour() },
            onFinish = { finishTour() }
        )
    }
    }
}
