package com.lullabyai.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.remote.ensureRealtimeConnected
import com.lullabyai.app.data.settings.SettingsManager
import kotlinx.coroutines.delay
import com.lullabyai.app.NongRakApp
import com.lullabyai.app.ui.components.LocalAvatarUrl
import com.lullabyai.app.ui.components.clearSavedTabStacks
import com.lullabyai.app.ui.screens.auth.LoginScreen
import com.lullabyai.app.ui.screens.auth.SignUpScreen
import com.lullabyai.app.ui.screens.chat.AIChatScreen
import com.lullabyai.app.ui.screens.child.ChildDevelopmentScreen
import com.lullabyai.app.ui.screens.child.ChildHealthScreen
import com.lullabyai.app.ui.screens.child.ChildSummaryScreen
import com.lullabyai.app.ui.screens.child.ChronicRegistryScreen
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
import com.lullabyai.app.ui.screens.help.HelpFaqScreen
import com.lullabyai.app.ui.screens.onboarding.AddChildChoiceScreen
import com.lullabyai.app.ui.screens.onboarding.UploadBirthCertificateScreen
import com.lullabyai.app.ui.screens.onboarding.LastSetupScreen
import com.lullabyai.app.ui.screens.onboarding.MascotSelectScreen
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
import com.lullabyai.app.ui.screens.child.OralHealthScreen
import com.lullabyai.app.ui.screens.child.RecordOralHealthEventScreen
import com.lullabyai.app.ui.theme.NongRakTheme
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.OnboardingViewModel
import com.lullabyai.app.viewmodel.NutritionViewModel
import com.lullabyai.app.viewmodel.OralHealthViewModel
import com.lullabyai.app.viewmodel.AccessCapabilities
import com.lullabyai.app.viewmodel.SettingsViewModel
import com.lullabyai.app.data.settings.AppLanguage

@Composable
private fun OnboardingFlowTheme(
    onboardingViewModel: OnboardingViewModel,
    settingsViewModel: SettingsViewModel,
    content: @Composable () -> Unit
) {
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    val appSettings by settingsViewModel.settings.collectAsState()
    NongRakTheme(darkTheme = onboardingState.addMode && appSettings.darkMode) {
        content()
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel = viewModel(),
    childViewModel: ChildViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    onboardingViewModel: OnboardingViewModel = viewModel(),
    nutritionViewModel: NutritionViewModel = viewModel(),
    oralHealthViewModel: OralHealthViewModel = viewModel()
) {
    val appSettings by settingsViewModel.settings.collectAsState()
    val lang = if (appSettings.language == AppLanguage.TH) "th" else "en"
    val avatarUrl: String? by authViewModel.uiState
        .map { it.avatarSignedUrl }
        .distinctUntilChanged()
        .collectAsState(initial = null)
    // Loads once per process; the header's profile action (LullabyTopBar/ChildScreenScaffold)
    // reads the resulting signed URL via LocalAvatarUrl. No-op (stays null) when signed out.
    LaunchedEffect(Unit) { authViewModel.loadProfile() }

    // Persist doctor vs parent portal whenever the visible route changes (not only on dashboard entry).
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route ?: return@collect
            val portalSettings = SettingsManager(NongRakApp.instance)
            when {
                route.startsWith("doctor_") -> {
                    portalSettings.setLastAuthPortal(SettingsManager.PORTAL_DOCTOR)
                }
                route == Screen.Dashboard.route ||
                    route == Screen.MyDoctorsHub.route ||
                    route == Screen.PediaConnectDashboard.route ||
                    route == Screen.PediaConnectChooseChild.route ||
                    route.startsWith("child_") -> {
                    portalSettings.setLastAuthPortal(SettingsManager.PORTAL_PARENT)
                }
            }
        }
    }

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

    var doctorTourActive by remember { mutableStateOf(false) }
    var doctorTourStep by remember { mutableIntStateOf(0) }
    val finishDoctorTour = {
        doctorTourActive = false
        settingsViewModel.setHasSeenDoctorTour(true)
        navController.navigate(Screen.DoctorDashboard.route) { popUpTo(0) }
    }

    // LocalNavController lets the shared footer own tab switching (per-tab back stack save/restore,
    // re-tap returns to that tab's root) without threading nav lambdas through all 56 footer screens.
    // Unread important ("warning") notifications, counted once here so every header's bell can
    // show a red count badge without each of the 106 screens loading notifications itself (#8).
    val notificationViewModel: com.lullabyai.app.viewmodel.NotificationViewModel = viewModel()
    val importantCount by notificationViewModel.uiState
        .map { state -> state.all.count { com.lullabyai.app.viewmodel.NotificationViewModel.countsOnBell(it) } }
        .distinctUntilChanged()
        .collectAsState(initial = 0)
    LaunchedEffect(Unit) { notificationViewModel.startWatching() }

    // Connect Realtime WebSocket once the parent shell is up (required before channel.subscribe).
    LaunchedEffect(Unit) { ensureRealtimeConnected() }

    // Owner-side: live popup when someone scans a sync QR (any screen, not only Sync Child).
    val accessRequestViewModel: com.lullabyai.app.viewmodel.AccessRequestViewModel = viewModel()
    val accessRequestState by accessRequestViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { accessRequestViewModel.startWatching() }

    // Co-parent realtime: reload children after approval + refresh shared data when another device edits.
    val childState by childViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { childViewModel.startMembershipWatch() }
    LaunchedEffect(childState.child?.id) {
        childState.child?.id?.let { childViewModel.startChildDataWatch(it) }
    }

    // Requester: after QR/code sync sent, poll until a new child appears (Realtime may be off).
    LaunchedEffect(accessRequestState.requestSent, accessRequestState.submitting) {
        if (!accessRequestState.requestSent || accessRequestState.submitting) return@LaunchedEffect
        val baseline = childState.children.size
        repeat(30) {
            delay(4_000)
            childViewModel.loadChild()
            if (childViewModel.uiState.value.children.size > baseline) {
                accessRequestViewModel.clearRequestSent()
                return@LaunchedEffect
            }
        }
    }

    val slideSpec = remember { tween<IntOffset>(durationMillis = 180) }
    val fadeSpec = remember { tween<Float>(durationMillis = 180) }
    val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        remember(slideSpec, fadeSpec) {
            { slideInHorizontally(slideSpec, initialOffsetX = { it / 4 }) + fadeIn(fadeSpec) }
        }
    val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        remember(slideSpec, fadeSpec) {
            { slideOutHorizontally(slideSpec, targetOffsetX = { -it / 4 }) + fadeOut(fadeSpec) }
        }
    val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        remember(slideSpec, fadeSpec) {
            { slideInHorizontally(slideSpec, initialOffsetX = { -it / 4 }) + fadeIn(fadeSpec) }
        }
    val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        remember(slideSpec, fadeSpec) {
            { slideOutHorizontally(slideSpec, targetOffsetX = { it / 4 }) + fadeOut(fadeSpec) }
        }

    CompositionLocalProvider(
        LocalAvatarUrl provides avatarUrl,
        com.lullabyai.app.ui.components.LocalNavController provides navController,
        com.lullabyai.app.ui.components.LocalImportantNotificationCount provides importantCount
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        val currentChildState by childViewModel.uiState.collectAsState()
        if (currentChildState.requireRegistrationPrompt != null) {
            com.lullabyai.app.ui.components.RequireRegistrationDialog(
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนใช้งาน" else "Registration Required",
                message = currentChildState.requireRegistrationPrompt ?: "",
                isThai = lang == "th",
                onDismiss = { childViewModel.dismissRegistrationPrompt() },
                onSignUp = {
                    childViewModel.dismissRegistrationPrompt()
                    navController.navigate(Screen.SignUp.route)
                },
                onSignIn = {
                    childViewModel.dismissRegistrationPrompt()
                    navController.navigate(Screen.Login.route)
                }
            )
        }
    // Slide (not crossfade) so two differently-themed screen backgrounds (e.g. onboarding's forced
    // light theme vs. the rest of the app in dark mode) never alpha-blend into a visible flash —
    // each screen stays fully opaque and simply slides, only briefly exposing the themed Box behind.
    com.lullabyai.app.ui.components.LineOaBanner(
        onOpenLink = { navController.navigate(Screen.LineLink.route) }
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        composable(Screen.Welcome.route) {
            NongRakTheme(darkTheme = false) {
                WelcomeScreen(
                    onSignUp = { navController.navigate(Screen.SignUp.route) },
                    onLogin = { navController.navigate(Screen.Login.route) },
                    settingsViewModel = settingsViewModel,
                    onGuestMode = {
                        childViewModel.initGuestChild()
                        navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                    }
                )
            }
        }
        composable(Screen.Login.route) {
            NongRakTheme(darkTheme = false) {
                LoginScreen(
                    authViewModel = authViewModel,
                    settingsViewModel = settingsViewModel,
                    onLoginSuccess = {
                        authViewModel.resolveHomeForPortal(
                            portal = AuthRepository.AuthPortal.PARENT,
                            isThai = lang == "th"
                        ) {
                            navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                        }
                    },
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
        // Same reset-flow screen as the parent route above (email-OTP reset is identical for both
        // account types) — only the "back to login" destination differs, so doctors never get
        // dumped into parent mode after resetting their password.
        composable(Screen.DoctorForgotPassword.route) {
            NongRakTheme(darkTheme = false) {
                com.lullabyai.app.ui.screens.auth.ForgotPasswordScreen(
                    viewModel = viewModel(),
                    onBackToLogin = { navController.navigate(Screen.DoctorSignIn.route) { popUpTo(0) } }
                )
            }
        }
        composable(Screen.ForceChangePassword.route) {
            NongRakTheme(darkTheme = false) {
                com.lullabyai.app.ui.screens.auth.ForceChangePasswordScreen(
                    authViewModel = authViewModel,
                    onDone = {
                        authViewModel.resolveHomeForPortal(
                            portal = AuthRepository.AuthPortal.PARENT,
                            isThai = lang == "th"
                        ) {
                            navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                        }
                    }
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
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนเพิ่มข้อมูลลูก" else "Registration Required to Add Child",
                message = if (lang == "th") "บัญชีทดลอง (Test) ไม่สามารถเพิ่มหรือซิงค์ข้อมูลลูกได้ กรุณาสมัครสมาชิกก่อนดำเนินการ" else "Test accounts cannot add or sync children. Please sign up to manage your children."
            ) {
                NongRakTheme(darkTheme = false) {
                    AddChildChoiceScreen(
                        onAddNew = {
                            if (authState.profile?.relationship.isNullOrBlank()) {
                                onboardingViewModel.reset()
                                navController.navigate(Screen.MedicalDisclaimer.route)
                            } else {
                                onboardingViewModel.startAddChild()
                                navController.navigate(Screen.OnboardingStep1.route)
                            }
                        },
                        onSync = { navController.navigate(Screen.SyncChild.route) },
                        onUploadCertificate = { navController.navigate(Screen.UploadBirthCertificate.route) },
                        onBack = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } }
                    )
                }
            }
        }
        composable(Screen.SyncChild.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนซิงค์ข้อมูลลูก" else "Registration Required to Sync Child",
                message = if (lang == "th") "บัญชีทดลอง (Test) ไม่สามารถซิงค์ข้อมูลลูกได้ กรุณาสมัครสมาชิกก่อนดำเนินการ" else "Test accounts cannot sync child profiles. Please sign up."
            ) {
                NongRakTheme(darkTheme = false) {
                    com.lullabyai.app.ui.screens.access.SyncChildProfileScreen(
                        viewModel = viewModel(),
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        onDone = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } },
                        onSeePendingRequests = { navController.navigate(Screen.PendingAccessRequests.route) },
                        footerTab = com.lullabyai.app.ui.components.BottomTab.HOME
                    )
                }
            }
        }
        composable(Screen.UploadBirthCertificate.route) {
            val authState by authViewModel.uiState.collectAsState()
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนเพิ่มข้อมูลลูก" else "Registration Required to Add Child"
            ) {
                NongRakTheme(darkTheme = false) {
                    UploadBirthCertificateScreen(
                        onExtracted = { fullName, nickname, birthdate, gender ->
                            if (authState.profile?.relationship.isNullOrBlank()) {
                                onboardingViewModel.reset()
                                onboardingViewModel.updateStep1(nickname, fullName, birthdate, gender, "", "")
                                navController.navigate(Screen.MedicalDisclaimer.route)
                            } else {
                                onboardingViewModel.startAddChild()
                                onboardingViewModel.updateStep1(nickname, fullName, birthdate, gender, "", "")
                                navController.navigate(Screen.OnboardingStep1.route)
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
        composable(Screen.OnboardingStep1.route) {
            val onboardingState by onboardingViewModel.uiState.collectAsState()
            OnboardingFlowTheme(onboardingViewModel, settingsViewModel) {
                OnboardingStep1Screen(
                    onboardingViewModel = onboardingViewModel,
                    onNext = { navController.navigate(Screen.OnboardingStep2.route) },
                    onBack = {
                        // Add-child (incl. first child from Dashboard): home — never sign out to Welcome.
                        val loggedIn = authViewModel.getCurrentUserId() != null
                        if (onboardingState.addMode || loggedIn) {
                            navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                        } else {
                            authViewModel.cancelSignup {
                                navController.navigate(Screen.Welcome.route) { popUpTo(0) }
                            }
                        }
                    }
                )
            }
        }
        composable(Screen.OnboardingStep2.route) {
            val onboardingState by onboardingViewModel.uiState.collectAsState()
            OnboardingFlowTheme(onboardingViewModel, settingsViewModel) {
                OnboardingStep2Screen(
                    onboardingViewModel = onboardingViewModel,
                    onComplete = {
                        onboardingViewModel.completeOnboarding {
                            navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                        }
                    },
                    onFillMore = {
                        navController.navigate(Screen.OnboardingStep3.route)
                    },
                    onBack = {
                        if (onboardingState.addMode) navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                        else navController.popBackStack()
                    }
                )
            }
        }
        composable(Screen.OnboardingStep3.route) {
            val onboardingState by onboardingViewModel.uiState.collectAsState()
            OnboardingFlowTheme(onboardingViewModel, settingsViewModel) {
                OnboardingStep3Screen(
                    onboardingViewModel = onboardingViewModel,
                    onNext = {
                        navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                    },
                    onBack = {
                        if (onboardingState.existingChildId != null) {
                            navController.navigate(Screen.Dashboard.route) { popUpTo(0) }
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
        composable(Screen.LastSetup.route) {
            OnboardingFlowTheme(onboardingViewModel, settingsViewModel) {
                LastSetupScreen(
                    onboardingViewModel = onboardingViewModel,
                    authViewModel = authViewModel,
                    // Straight to the tour: the companion is now chosen and switched from chat,
                    // where it lives, rather than as a one-off decision buried in onboarding.
                    onComplete = { navController.navigate(Screen.FirstRunTour.route) { popUpTo(0) } },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.MascotSelect.route) {
            OnboardingFlowTheme(onboardingViewModel, settingsViewModel) {
                MascotSelectScreen(
                    onSelected = { navController.navigate(Screen.FirstRunTour.route) { popUpTo(0) } },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        // Same picker, opened from an ongoing conversation: it returns to that conversation
        // instead of continuing the onboarding chain.
        composable(Screen.MascotSwitch.route) {
            NongRakTheme(darkTheme = false) {
                MascotSelectScreen(
                    onSelected = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onOpenCloset = { navController.navigate(Screen.MascotCloset.route) }
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
            // Which child is this about? Only worth asking when there is more than one. A parent
            // with a single child should never meet a chooser with one row in it.
            fun pickChildThen(route: String): String =
                if (childViewModel.uiState.value.children.size > 1) Screen.ChildPicker.route(route)
                else route

            DashboardScreen(
                childViewModel = childViewModel,
                chatViewModel = chatViewModel,
                authViewModel = authViewModel,
                onNavigateToChat = {
                    navController.navigate(Screen.AiChat.route) {
                        popUpTo(Screen.Dashboard.route) {
                            saveState = true
                            inclusive = false
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToChild = { navController.navigate(Screen.ChildSummary.route) },
                onNavigateToVaccine = { navController.navigate(Screen.VaccineBook.route) },
                onNavigateToDevelopment = { navController.navigate(Screen.ChildDevelopment.route) },
                onNavigateToHealth = { navController.navigate(Screen.ChildHealth.route) },
                onNavigateToGrowth = { navController.navigate(Screen.GrowthHistory.route) },
                onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToHelp = { navController.navigate(Screen.HelpFaq.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToQuickActionSearch = { navController.navigate(Screen.QuickActionSearch.route) },
                onNavigateToStats = { navController.navigate(Screen.StatisticsInsights.route) },
                onNavigateToSleep = { navController.navigate(Screen.SleepTracker.route) },
                onNavigateToAddChild = { navController.navigate(Screen.AddChildChoice.route) },
                onNavigateToMentalHealth = { navController.navigate(Screen.MentalHealthAssessment.route) },
                onNavigateToQuickLog = { navController.navigate(Screen.QuickLogHub.route) },
                // The บันทึกด่วน tiles go to the LOG each one is named after, not to a hub above it.
                onNavigateToMilkLog = { navController.navigate(pickChildThen(Screen.MilkRecord.route)) },
                onNavigateToWasteLog = { navController.navigate(pickChildThen(Screen.WasteRecord.route)) },
                onNavigateToAddGrowth = { navController.navigate(pickChildThen(Screen.AddGrowthRecord.route)) },
                // AddHealthRecord creates the illness record; LogSicknessSymptoms needs one to exist.
                onNavigateToSickness = { navController.navigate(pickChildThen(Screen.AddHealthRecord.route)) },
                // การทำงานด่วน, plus the three buttons on ครอบครัวของเรา. Every destination already
                // existed - ActivitySchedule in particular was reachable only from a tile buried
                // inside ข้อมูลลูก, despite being exactly the calendar this card asks for.
                onNavigateToAppointments = { navController.navigate(Screen.RecordAppointment.route) },
                onNavigateToHealthBook = { navController.navigate(Screen.HealthHistory.route) },
                onNavigateToMedicationLog = { navController.navigate(pickChildThen(Screen.MedicationHistory.route)) },
                onNavigateToCalendar = { navController.navigate(Screen.ActivitySchedule.route) },
                onNavigateToFamilySummary = { navController.navigate(Screen.ChildSummary.route) },
                onNavigateToTips = { navController.navigate(Screen.ParentTips.route) },
                // สมาชิกในครอบครัว goes to the permission screen, which is where who-can-see-what
                // actually lives; Connect is about linking a doctor, a different question.
                onNavigateToFamilyMembers = { navController.navigate(Screen.PermissionAccess.route) },
                onNavigateToWidgets = { navController.navigate(Screen.WidgetsIntro.route) },
                onNavigateHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onNavigateToConnect = { navController.navigate(Screen.MyDoctorsHub.route) { launchSingleTop = true } },
                onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) },
                onNavigateToHealthSurvey = {
                    val activeChild = childViewModel.uiState.value.child
                    if (activeChild != null) {
                        onboardingViewModel.initFromChild(activeChild)
                    }
                    navController.navigate(Screen.OnboardingStep3.route)
                },
                onNavigateToRoute = { route -> navController.navigate(route) },
                isTourActive = tourActive
            )
        }
        composable(Screen.AiChat.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("chat") }
            AIChatScreen(
                chatViewModel = chatViewModel,
                childViewModel = childViewModel,
                onBack = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                onNavHome = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                onNavChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onNavSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onNavStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } }
            )
        }
        composable(Screen.ChildSummary.route) {
            // Child tab = the Stitch "NongRak Child Hub - Pro UX Redesign" screen (ChildHubScreen).
            // The previous ChildSummaryScreen is kept in the codebase but no longer routed.
            com.lullabyai.app.ui.screens.child.ChildHubScreen(
                childViewModel, chatViewModel, navController,
                isTourMode = tourActive
            )
        }
        composable(Screen.ChildAiSummary.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.child.ChildAiSummaryScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.WasteRecord.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_ACTIVITIES) {
                com.lullabyai.app.ui.screens.child.WasteRecordScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.ProfileHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.child.ProfileHistoryScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.ChildDevelopment.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("development") }
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                ChildDevelopmentScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.ChildHealth.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("health") }
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                ChildHealthScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.VaccineBook.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("vaccine") }
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                VaccineBookScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.ChronicRegistry.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                ChronicRegistryScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        // Legacy disease_risk deep-link → Chronic Registry (#14)
        composable(Screen.DiseaseRisk.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(Screen.ChronicRegistry.route) {
                    popUpTo(Screen.DiseaseRisk.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        composable(Screen.WeeklyHealthReport.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.child.WeeklyHealthReportScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.Maintenance.route) {
            MaintenanceScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.QuickLogHub.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_ACTIVITIES) {
                com.lullabyai.app.ui.screens.stats.QuickLogHubScreen(childViewModel, chatViewModel, navController)
            }
        }
        composable(Screen.SmartTrends.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.stats.SmartTrendsScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.StatisticsInsights.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.stats.StatisticsInsightsScreen(childViewModel, chatViewModel, onboardingViewModel, navController)
            }
        }
        composable(Screen.FoodHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_NUTRITION) {
                com.lullabyai.app.ui.screens.nutrition.FoodHistoryScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.ExerciseHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_ACTIVITIES) {
                com.lullabyai.app.ui.screens.exercise.ExerciseHistoryScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.MedicationHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_MEDICATIONS) {
                MedicationHistoryScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.FullMedicationHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_MEDICATIONS) {
                com.lullabyai.app.ui.screens.child.FullMedicationHistoryScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.HealthHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.child.HealthHistoryScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.PdfExport.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_DOCUMENTS) {
                com.lullabyai.app.ui.screens.child.PdfExportScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.SicknessFollowUp.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SYMPTOMS) {
                com.lullabyai.app.ui.screens.child.SicknessFollowUpScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.LogSicknessSymptoms.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SYMPTOMS) {
                com.lullabyai.app.ui.screens.child.LogSicknessSymptomsScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.AddMedicineSchedule.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_MEDICATIONS) {
                AddMedicineScheduleScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.MedicalDocumentHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_DOCUMENTS) {
                MedicalDocumentHistoryScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.SleepTracker.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("sleep") }
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SLEEP) {
                SleepTrackerScreen(childViewModel, chatViewModel, navController)
            }
        }
        composable(
            route = Screen.AddSleepTime.route,
            arguments = listOf(navArgument("scheduleId") { nullable = true; defaultValue = null })
        ) { entry ->
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SLEEP) {
                AddSleepTimeScreen(
                    childViewModel, chatViewModel, navController,
                    scheduleId = entry.arguments?.getString("scheduleId")
                ) { navController.popBackStack() }
            }
        }
        composable(Screen.SleepSchedule.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SLEEP) {
                SleepScheduleScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.SleepGuidance.route) {
            SleepGuidanceScreen(childViewModel = childViewModel, navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.GrowthHistory.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("growth") }
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.growth.GrowthHistoryScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.AddGrowthRecord.route) {
            CapGate(childViewModel, navController, AccessCapabilities.EDIT_GROWTH) {
                com.lullabyai.app.ui.screens.growth.AddGrowthRecordScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.SiblingCompare.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.growth.SiblingCompareScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.CameraScan.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนใช้งาน AI สแกน" else "Registration Required for AI Scan"
            ) {
                com.lullabyai.app.ui.screens.chat.CameraScanScreen(isThai = lang == "th") { navController.popBackStack() }
            }
        }
        composable(Screen.VoiceCall.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนโทรหา AI" else "Registration Required for AI Call"
            ) {
                com.lullabyai.app.ui.screens.chat.CallScreen(chatViewModel, childViewModel, isThai = lang == "th") { navController.popBackStack() }
            }
        }
        composable(Screen.HealthLog.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SYMPTOMS) {
                com.lullabyai.app.ui.screens.child.HealthLogScreen(navController, isThai = lang == "th")
            }
        }
        composable(Screen.QuickActionSearch.route) {
            com.lullabyai.app.ui.screens.search.QuickActionSearchScreen(navController, isThai = lang == "th") { navController.popBackStack() }
        }
        composable(Screen.WhoWeightForAgeDetail.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.growth.WhoWeightForAgeDetailScreen(childViewModel, chatViewModel, navController)
            }
        }
        composable(Screen.WhoHeightForAgeDetail.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.growth.WhoHeightForAgeDetailScreen(childViewModel, chatViewModel, navController)
            }
        }
        composable(Screen.NutritionInsights.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_NUTRITION) {
                com.lullabyai.app.ui.screens.stats.NutritionInsightsScreen(nutritionViewModel, childViewModel, chatViewModel, navController)
            }
        }
        composable(Screen.SleepActivityInsights.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SLEEP) {
                com.lullabyai.app.ui.screens.stats.SleepActivityInsightsScreen(childViewModel, chatViewModel, navController)
            }
        }
        composable(Screen.HealthEliminationInsights.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.stats.HealthEliminationInsightsScreen(childViewModel, chatViewModel, navController)
            }
        }
        composable(Screen.MonthlyReport.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.stats.MonthlyReportScreen(childViewModel, chatViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.MentalHealthAssessment.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนทำแบบประเมิน" else "Registration Required",
                message = if (lang == "th") "แบบประเมินสุขภาพจิตและรายงานวิเคราะห์เปิดให้ใช้งานสำหรับสมาชิกที่ลงทะเบียนเท่านั้น" else "Mental health screening is available for registered members only."
            ) {
                com.lullabyai.app.ui.screens.mentalhealth.MentalHealthAssessmentScreen(
                    authViewModel,
                    onDone = { navController.navigate(Screen.MentalHealthResult.route) { popUpTo(Screen.Dashboard.route) } },
                    onBack = { navController.popBackStack() }
                )
            }
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
                onLogout = {
                    chatViewModel.clearForSignOut()
                    // Device-level singleton: without this the next account inherits the previous
                    // user's selected child, so "switch account" lands somewhere it should not.
                    com.lullabyai.app.data.settings.ChildSelection.clearForSignOut()
                    // popUpTo(0) below only empties the live stack; the footer's saved per-tab
                    // stacks survive it and would resurface under the next account.
                    navController.clearSavedTabStacks()
                    navController.navigate(Screen.Welcome.route) { popUpTo(0) }
                }
            )
        }
        composable(Screen.EditProfile.route) {
            EditProfileScreen(authViewModel = authViewModel, navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.MedicalDataVault.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_DOCUMENTS) {
                com.lullabyai.app.ui.screens.settings.MedicalDataVaultScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.AiDataRetention.route) {
            com.lullabyai.app.ui.screens.settings.AiDataRetentionScreen(childViewModel, navController) { navController.popBackStack() }
        }
        composable(Screen.AiPreferences.route) {
            com.lullabyai.app.ui.screens.settings.AiPreferencesScreen(navController = navController)
        }
        composable(Screen.TwoFactor.route) {
            com.lullabyai.app.ui.screens.settings.TwoFactorScreen(navController) { navController.popBackStack() }
        }
        composable(Screen.ActiveSessions.route) {
            com.lullabyai.app.ui.screens.settings.ActiveSessionsScreen(navController) { navController.popBackStack() }
        }
        composable(Screen.LineLink.route) {
            com.lullabyai.app.ui.screens.settings.LineLinkScreen(
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.TrustedDevices.route) {
            com.lullabyai.app.ui.screens.settings.TrustedDevicesScreen(
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.MascotCloset.route) {
            com.lullabyai.app.ui.screens.mascot.MascotClosetScreen(
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.HealthBook.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.child.HealthBookScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.InfoAsk.route) {
            CapGate(childViewModel, navController, AccessCapabilities.EDIT_PROFILE) {
                com.lullabyai.app.ui.screens.child.InfoAskScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.PermissionAccess.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ฟีเจอร์การจัดการสิทธิ์การเข้าถึง" else "Access Permission Management"
            ) {
                com.lullabyai.app.ui.screens.access.PermissionAccessScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ActivityHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_ACTIVITIES) {
                com.lullabyai.app.ui.screens.access.ActivityHistoryScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() },
                    // The screen only offers this to owners/co-owners, and only against caregivers.
                    onChat = { peerId -> navController.navigate(Screen.CareChat.route(peerId)) },
                    onReplyToEntry = { peerId, subject ->
                        navController.navigate(Screen.CareChat.route(peerId, subject))
                    },
                    onOpenHub = { navController.navigate(Screen.CareCircle.route) },
                    onOpenRecord = { kind ->
                        navController.navigate(com.lullabyai.app.viewmodel.ActivityKindLabels.recordRoute(kind))
                    }
                )
            }
        }
        composable(Screen.CareCircle.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "กลุ่มผู้ดูแลเด็ก (Care Circle)" else "Care Circle"
            ) {
                com.lullabyai.app.ui.screens.access.CareCircleScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() },
                    onOpenChat = { peerId -> navController.navigate(Screen.CareChat.route(peerId)) }
                )
            }
        }
        composable(Screen.CareHandoff.route, arguments = listOf(navArgument("childId") { type = NavType.StringType })) { entry ->
            com.lullabyai.app.ui.screens.access.HandoffScreen(
                childId = entry.arguments?.getString("childId").orEmpty(), navController = navController
            )
        }
        composable(Screen.AiEndpoint.route) {
            com.lullabyai.app.ui.screens.settings.AiEndpointScreen(navController = navController)
        }
        composable(Screen.AiParentStyle.route) {
            com.lullabyai.app.ui.screens.settings.AiParentStyleScreen(navController = navController)
        }
        composable(
            Screen.CareRoutine.route,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) { entry ->
            CapGate(childViewModel, navController, AccessCapabilities.LOG_ACTIVITIES) {
                com.lullabyai.app.ui.screens.routine.CareRoutineScreen(
                    childId = entry.arguments?.getString("childId").orEmpty(), navController = navController
                )
            }
        }
        composable(
            Screen.CareHandoverChecklist.route,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) { entry ->
            com.lullabyai.app.ui.screens.access.CaregiverHandoverChecklistScreen(
                childId = entry.arguments?.getString("childId").orEmpty(), navController = navController
            )
        }
        composable(
            Screen.CareChat.route,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("quote") {
                    type = NavType.StringType; defaultValue = ""; nullable = true
                }
            )
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "แชทระหว่างผู้ดูแล" else "Caregiver Chat"
            ) {
                com.lullabyai.app.ui.screens.access.CareChatScreen(
                    viewModel = viewModel(), navController = navController,
                    peerId = peerId,
                    quote = backStackEntry.arguments?.getString("quote").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ExpenseHistory.route) {
            com.lullabyai.app.ui.screens.expense.ExpenseHistoryScreen(
                viewModel = viewModel(), navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Expenses.route) {
            com.lullabyai.app.ui.screens.expense.ExpenseScreen(
                viewModel = viewModel(), navController = navController,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Screen.AddExpense.route) },
                onSeeAll = { navController.navigate(Screen.ExpenseHistory.route) }
            )
        }
        composable(Screen.ActivitySchedule.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_ACTIVITIES) {
                com.lullabyai.app.ui.screens.schedule.ActivityScheduleScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Screen.AddActivity.route) }
                )
            }
        }
        composable(Screen.AddActivity.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_ACTIVITIES) {
                com.lullabyai.app.ui.screens.schedule.AddActivityScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.AddExpense.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนบันทึกค่าใช้จ่าย" else "Registration Required"
            ) {
                com.lullabyai.app.ui.screens.expense.AddExpenseScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.CaregiverUpdate.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนจัดการผู้ดูแล" else "Registration Required"
            ) {
                com.lullabyai.app.ui.screens.access.CaregiverUpdateScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() },
                    onChat = { peerId -> navController.navigate(Screen.CareChat.route(peerId)) }
                )
            }
        }
        composable(Screen.AddUserAccessActivity.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนเพิ่มสิทธิ์ผู้ดูแล" else "Registration Required"
            ) {
                com.lullabyai.app.ui.screens.access.AddUserAccessActivityScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ShareAccessQr.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนแชร์สิทธิ์เข้าถึง" else "Registration Required"
            ) {
                com.lullabyai.app.ui.screens.access.ShareAccessQrScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.RoleManagement.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนจัดการบทบาทและสิทธิ์" else "Registration Required"
            ) {
                com.lullabyai.app.ui.screens.access.RoleManagementScreen(
                    viewModel = viewModel(), navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            Screen.PermissionSettingsDetail.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            com.lullabyai.app.ui.screens.access.PermissionSettingsDetailScreen(
                userId = userId, viewModel = viewModel(), navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.MilkRecord.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_FEEDING) {
                com.lullabyai.app.ui.screens.nutrition.MilkRecordScreen(childViewModel, nutritionViewModel, lang, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.HelpFaq.route) {
            HelpFaqScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.Feedback.route) {
            com.lullabyai.app.ui.screens.settings.FeedbackScreen(
                navController = navController,
                authViewModel = authViewModel,
                lang = lang,
                onBack = { navController.popBackStack() }
            )
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
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "วิดเจ็ตสำหรับสมาชิก" else "Widgets for Registered Members",
                message = if (lang == "th") "ฟีเจอร์วิดเจ็ตหน้าจอหลักเปิดให้ใช้งานสำหรับสมาชิกที่ลงทะเบียนเท่านั้น กรุณาสมัครสมาชิกเพื่อใช้งาน" else "Home screen widgets require an account. Please sign up."
            ) {
                com.lullabyai.app.ui.screens.settings.WidgetsIntroScreen(navController = navController, onBack = { navController.popBackStack() })
            }
        }
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.TermsOfUse.route) {
            TermsOfUseScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        // Doctor-side entries onto the same three pages, differing only in which footer is drawn.
        composable(Screen.DoctorChangePassword.route) {
            ChangePasswordScreen(authViewModel = authViewModel, navController = navController, onBack = { navController.popBackStack() }, isDoctor = true)
        }
        composable(Screen.DoctorNotifications.route) {
            NotificationsScreen(navController = navController, childViewModel = childViewModel, onBack = { navController.popBackStack() }, isDoctor = true)
        }
        composable(Screen.DoctorPrivacyPolicy.route) {
            PrivacyPolicyScreen(navController = navController, onBack = { navController.popBackStack() }, isDoctor = true)
        }
        composable(Screen.EditChild.route) {
            CapGate(childViewModel, navController, AccessCapabilities.EDIT_PROFILE) {
                com.lullabyai.app.ui.screens.child.EditChildScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.AddHealthRecord.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SYMPTOMS) {
                com.lullabyai.app.ui.screens.child.AddHealthRecordScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.HealthDetail.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.child.HealthDetailScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.TaskHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_ACTIVITIES) {
                com.lullabyai.app.ui.screens.child.TaskHistoryScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.DevelopmentHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.child.DevelopmentHistoryScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.AllergyHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                com.lullabyai.app.ui.screens.child.AllergyHistoryScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ---- Doctor Appointment Record ("บันทึกการหาหมอ") ----
        composable(Screen.DoctorVisitHistory.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_APPOINTMENTS) {
                com.lullabyai.app.ui.screens.child.DoctorVisitHistoryScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.RecordAppointment.route) {
            CapGate(childViewModel, navController, AccessCapabilities.BOOK_DOCTOR) {
                com.lullabyai.app.ui.screens.child.RecordAppointmentScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.RecordDoctorVisit.route) {
            CapGate(childViewModel, navController, AccessCapabilities.BOOK_DOCTOR) {
                com.lullabyai.app.ui.screens.child.RecordVisitScreen(childViewModel) { navController.popBackStack() }
            }
        }
        composable(Screen.VisitDetails.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_APPOINTMENTS) {
                com.lullabyai.app.ui.screens.child.VisitDetailsScreen(childViewModel, navController) { navController.popBackStack() }
            }
        }

        // ---- Nutrition / Calorie / Exercise feature ----
        composable(Screen.Nutrition.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) { com.lullabyai.app.data.settings.RecentServices.push("nutrition") }
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_NUTRITION) {
                NutritionScreen(nutritionViewModel, lang, navController)
            }
        }
        composable(Screen.CalorieCalculator.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_NUTRITION) {
                CalorieCalculatorScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.CalorieSummary.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_NUTRITION) {
                CalorieSummaryScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.CalorieCheck.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_NUTRITION) {
                com.lullabyai.app.ui.screens.nutrition.CalorieCheckScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.EditFoodEntries.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_FEEDING) {
                EditFoodEntriesScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.RecommendedFood.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_NUTRITION) {
                com.lullabyai.app.ui.screens.nutrition.RecommendedFoodScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.RefrigeratorScanner.route) {
            com.lullabyai.app.ui.screens.nutrition.RefrigeratorScannerScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
        }
        composable(Screen.Exercise.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_ACTIVITIES) {
                ExerciseScreen(nutritionViewModel, lang, navController)
            }
        }
        composable(Screen.LogExercise.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_ACTIVITIES) {
                com.lullabyai.app.ui.screens.exercise.LogExerciseScreen(nutritionViewModel, lang, navController) { navController.popBackStack() }
            }
        }
        composable(Screen.ExerciseInstruction.route) {
            com.lullabyai.app.ui.screens.exercise.ExerciseInstructionScreen(nutritionViewModel, lang) { navController.popBackStack() }
        }
        composable(Screen.RecipeDetail.route) {
            com.lullabyai.app.ui.screens.nutrition.RecipeDetailScreen(nutritionViewModel, lang) { navController.popBackStack() }
        }

        // ---- Doctor role: auth / settings ----
        composable(Screen.DoctorFirstRunTour.route) {
            NongRakTheme(darkTheme = false) {
                LaunchedEffect(Unit) {
                    doctorTourStep = 0
                    doctorTourActive = true
                    navController.navigate(Screen.DoctorDashboard.route) { popUpTo(0) }
                }
            }
        }
        composable(Screen.DoctorWelcome.route) {
            NongRakTheme(darkTheme = false) {
                com.lullabyai.app.ui.screens.doctor.DoctorWelcomeScreen(navController, settingsViewModel)
            }
        }
        composable(Screen.DoctorSignIn.route) {
            NongRakTheme(darkTheme = false) {
                com.lullabyai.app.ui.screens.doctor.DoctorSignInScreen(authViewModel, navController)
            }
        }
        composable(Screen.DoctorSignUp.route) {
            NongRakTheme(darkTheme = false) {
                val doctorViewModel: com.lullabyai.app.viewmodel.DoctorViewModel = viewModel()
                com.lullabyai.app.ui.screens.doctor.DoctorSignUpScreen(authViewModel, doctorViewModel, navController)
            }
        }
        composable(Screen.DoctorGoogleCompleteProfile.route) {
            NongRakTheme(darkTheme = false) {
                val doctorViewModel: com.lullabyai.app.viewmodel.DoctorViewModel = viewModel()
                com.lullabyai.app.ui.screens.doctor.DoctorGoogleCompleteProfileScreen(
                    authViewModel, doctorViewModel, navController
                )
            }
        }
        composable(Screen.DoctorLogin.route) {
            NongRakTheme(darkTheme = false) {
                com.lullabyai.app.ui.screens.doctor.DoctorLoginScreen(navController)
            }
        }
        composable(Screen.DoctorSettings.route) {
            val doctorViewModel: com.lullabyai.app.viewmodel.DoctorViewModel = viewModel()
            com.lullabyai.app.ui.screens.doctor.DoctorSettingsScreen(
                authViewModel = authViewModel,
                settingsViewModel = settingsViewModel,
                doctorViewModel = doctorViewModel,
                navController = navController,
                onLogout = {
                    com.lullabyai.app.data.settings.ChildSelection.clearForSignOut()
                    navController.clearSavedTabStacks()
                    navController.navigate(Screen.Welcome.route) { popUpTo(0) }
                }
            )
        }
        composable(Screen.DoctorEditProfile.route) {
            val doctorViewModel: com.lullabyai.app.viewmodel.DoctorViewModel = viewModel()
            com.lullabyai.app.ui.screens.doctor.DoctorEditProfileScreen(
                authViewModel = authViewModel,
                doctorViewModel = doctorViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.DoctorWeeklyHours.route) {
            val doctorViewModel: com.lullabyai.app.viewmodel.DoctorViewModel = viewModel()
            com.lullabyai.app.ui.screens.doctor.DoctorWeeklyHoursScreen(
                authViewModel = authViewModel,
                doctorViewModel = doctorViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.DoctorHelpFaq.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorHelpFaqScreen(
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.DoctorAboutNongRak.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorAboutNongRakScreen(
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }

        // ---- Doctor role: operational ----
        composable(Screen.DoctorDashboard.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorDashboardScreen(navController)
        }
        composable(Screen.DoctorPatientDirectory.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorPatientDirectoryScreen(navController)
        }
        composable(
            route = Screen.DoctorPatientInfo.route,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) { entry ->
            val childId = entry.arguments?.getString("childId") ?: return@composable
            com.lullabyai.app.ui.screens.doctor.DoctorPatientInfoScreen(childId, navController)
        }
        composable(
            route = Screen.DoctorPatientVaccineHistory.route,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) { entry ->
            val childId = entry.arguments?.getString("childId") ?: return@composable
            com.lullabyai.app.ui.screens.doctor.DoctorPatientVaccineHistoryScreen(childId, navController)
        }
        composable(
            route = Screen.DoctorClinicalRecordEntry.route,
            arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })
        ) { entry ->
            val appointmentId = entry.arguments?.getString("appointmentId") ?: return@composable
            val clinicalVm: com.lullabyai.app.viewmodel.DoctorClinicalRecordEntryViewModel = viewModel(entry)
            com.lullabyai.app.ui.screens.doctor.DoctorClinicalRecordEntryScreen(appointmentId, navController, clinicalVm)
        }
        composable(Screen.DoctorPatientSchedule.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorPatientScheduleScreen(navController)
        }
        composable(Screen.DoctorManageSlots.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorManageSlotsScreen(navController)
        }
        composable(Screen.DoctorAddAppointment.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorAddAppointmentScreen(navController)
        }
        composable(Screen.DoctorPatientRegistrationApproval.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorPatientRegistrationApprovalScreen(navController)
        }
        composable(
            route = Screen.DoctorPatientRegistrationDetail.route,
            arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })
        ) { backStack ->
            val appointmentId = backStack.arguments?.getString("appointmentId").orEmpty()
            com.lullabyai.app.ui.screens.doctor.DoctorPatientRegistrationDetailScreen(appointmentId, navController)
        }
        composable(Screen.DoctorTimeAppointmentHub.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorTimeAppointmentHubScreen(navController)
        }
        composable(Screen.DoctorSpecialDates.route) {
            val doctorViewModel: com.lullabyai.app.viewmodel.DoctorViewModel = viewModel()
            com.lullabyai.app.ui.screens.doctor.DoctorSpecialDatesScreen(
                authViewModel = authViewModel,
                doctorViewModel = doctorViewModel,
                navController = navController
            )
        }
        composable(Screen.DoctorVaccineVerificationHub.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorVaccineVerificationHubScreen(navController)
        }
        composable(Screen.DoctorVaccineCertificatePreview.route) {
            val certVm: com.lullabyai.app.viewmodel.DoctorVaccineCertificateViewModel =
                viewModel(checkNotNull(androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity))
            com.lullabyai.app.ui.screens.doctor.DoctorVaccineCertificatePreviewScreen(navController, certVm)
        }
        composable(Screen.DoctorVaccineVerificationSent.route) { backStackEntry ->
            val hubEntry = remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(Screen.DoctorVaccineVerificationHub.route) }.getOrNull()
            }
            val hubVm: com.lullabyai.app.viewmodel.DoctorVaccineVerificationHubViewModel =
                if (hubEntry != null) viewModel(hubEntry) else viewModel()
            val certVm: com.lullabyai.app.viewmodel.DoctorVaccineCertificateViewModel =
                viewModel(checkNotNull(androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity))
            com.lullabyai.app.ui.screens.doctor.DoctorVaccineVerificationSentScreen(navController, hubVm, certVm)
        }
        composable(Screen.DoctorManualPatientRegister.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorManualPatientRegisterScreen(navController)
        }
        composable(Screen.DoctorPatientRegistrationForm.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorPatientRegistrationFormScreen(navController)
        }
        composable(Screen.DoctorPatientManagement.route) {
            com.lullabyai.app.ui.screens.doctor.DoctorPatientManagementScreen(navController)
        }

        // ---- Parent-side doctor connect / booking ----
        composable(Screen.SearchDoctor.route) {
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                screenTitle = if (lang == "th") "ค้นหาคุณหมอ" else "Find a doctor"
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.SearchDoctorScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.MyDoctorsHub.route) {
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                screenTitle = if (lang == "th") "คุณหมอของฉัน" else "My doctors"
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.MyDoctorsHubScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Screen.DoctorReservation.route,
            arguments = listOf(navArgument("doctorId") { type = NavType.StringType })
        ) { entry ->
            val doctorId = entry.arguments?.getString("doctorId") ?: return@composable
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.DoctorReservationScreen(
                    doctorId = doctorId,
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ConnectToDoctorWelcome.route) {
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.ConnectToDoctorWelcomeScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ConnectServicesSelection.route) {
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.ConnectServicesSelectionScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        // Routes for screens built by the parallel agents. Those agents were barred from editing
        // this file (five concurrent writers would have collided), so they declared their route
        // constants locally and the orchestrator registers them here.
        composable(com.lullabyai.app.ui.screens.child.VACCINE_BOOK_SCAN_ROUTE) {
            CapGate(childViewModel, navController, AccessCapabilities.UPLOAD_DOCUMENTS) {
                com.lullabyai.app.ui.screens.child.VaccineBookScanScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(com.lullabyai.app.ui.screens.sleep.PRO_SLEEP_HUB_ROUTE) {
            com.lullabyai.app.ui.screens.sleep.ProSleepHubScreen(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(com.lullabyai.app.ui.screens.sleep.RECORD_SLEEP_HISTORY_ROUTE) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SLEEP) {
                com.lullabyai.app.ui.screens.sleep.RecordSleepHistoryScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(com.lullabyai.app.ui.screens.child.AI_GUIDANCE_ROUTE) {
            com.lullabyai.app.ui.screens.child.AiGuidanceScreen(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(com.lullabyai.app.ui.screens.doctorconnect.APPOINTMENT_MANAGEMENT_ROUTE) { entry ->
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.AppointmentManagementScreen(
                    appointmentId = entry.arguments?.getString("appointmentId").orEmpty(),
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.SyncChildProfile.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนซิงค์ข้อมูลลูก" else "Registration Required"
            ) {
                com.lullabyai.app.ui.screens.access.SyncChildProfileScreen(
                    viewModel = viewModel(),
                    navController = navController,
                    onBack = { navController.popBackStack() },
                    onDone = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                    onSeePendingRequests = { navController.navigate(Screen.PendingAccessRequests.route) }
                )
            }
        }
        composable(Screen.PendingAccessRequests.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ต้องสมัครสมาชิกก่อนจัดการคำขอเข้าถึง" else "Registration Required"
            ) {
                com.lullabyai.app.ui.screens.access.PendingAccessRequestsScreen(
                    viewModel = viewModel(),
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.LabResults.route) {
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.LabResultsScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.PediaConnectChooseChild.route) {
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.PediaConnectChooseChildScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Screen.PatientRegistration.route,
            arguments = listOf(navArgument("doctorId") { type = NavType.StringType })
        ) { entry ->
            val doctorId = entry.arguments?.getString("doctorId") ?: return@composable
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.PatientRegistrationScreen(
                    doctorId = doctorId,
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.PediaConnectDashboard.route) {
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.PediaConnectDashboardScreen(
                    childViewModel = childViewModel,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Screen.AppointmentDetails.route,
            arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })
        ) { entry ->
            val appointmentId = entry.arguments?.getString("appointmentId") ?: return@composable
            com.lullabyai.app.ui.screens.access.ConnectAccessGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            ) {
                com.lullabyai.app.ui.screens.doctorconnect.AppointmentDetailsScreen(
                    appointmentId = appointmentId,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // --- Wearables / App Connect (#11) — append-only ---
        composable(Screen.AppConnectSetup.route) {
            if (com.lullabyai.app.data.AppConnectFeatures.IS_READY) {
                com.lullabyai.app.ui.screens.connect.AppConnectSetupScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            } else {
                com.lullabyai.app.ui.screens.connect.AppConnectNotReadyScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ChildDeviceHub.route) {
            if (com.lullabyai.app.data.AppConnectFeatures.IS_READY) {
                com.lullabyai.app.ui.screens.connect.ChildDeviceHubScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            } else {
                com.lullabyai.app.ui.screens.connect.AppConnectNotReadyScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // --- Diary / Special Memories (#10) — append-only ---
        composable(Screen.ChildDiary.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "สมุดบันทึกความทรงจำ (Diary)" else "Child Diary"
            ) {
                com.lullabyai.app.ui.screens.diary.ChildDiaryScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ChildDiaryReadBook.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "สมุดบันทึกความทรงจำ (E-Book & AI Recap)" else "Child Diary & Recap"
            ) {
                com.lullabyai.app.ui.screens.diary.ChildDiaryReadBookScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() },
                    childViewModel = childViewModel
                )
            }
        }
        composable(Screen.CompareMemories.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ย้อนดูความเปลี่ยนแปลง (Then vs Now)" else "Compare Growth (Then vs Now)"
            ) {
                val diaryVm: com.lullabyai.app.viewmodel.DiaryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                com.lullabyai.app.ui.screens.diary.CompareMemoriesScreen(
                    navController = navController,
                    vm = diaryVm,
                    isThai = lang == "th"
                )
            }
        }
        composable(Screen.RewindMemory.route) {

            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ย้อนดูความทรงจำ (Rewind)" else "Rewind Memory"
            ) {
                com.lullabyai.app.ui.screens.diary.RewindMemoryScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.SpecialMemoriesGallery.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "แกลเลอรีความทรงจำพิเศษ" else "Special Memories Gallery"
            ) {
                com.lullabyai.app.ui.screens.diary.SpecialMemoriesGalleryScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.AddMemory.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "เพิ่มบันทึกความทรงจำใหม่" else "Add New Memory"
            ) {
                com.lullabyai.app.ui.screens.diary.AddMemoryScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ChildRecognitionSetup.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ตั้งค่าการจดจำใบหน้าลูก" else "Child Recognition Setup"
            ) {
                com.lullabyai.app.ui.screens.diary.ChildRecognitionSetupScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.GalleryAiSettings.route) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                title = if (lang == "th") "ตั้งค่าแกลเลอรี AI" else "Gallery AI Settings"
            ) {
                com.lullabyai.app.ui.screens.diary.GalleryAiSettingsScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // --- Oral Health Pro System ---
        composable(Screen.OralHealth.route) {
            CapGate(childViewModel, navController, AccessCapabilities.VIEW_HEALTH) {
                OralHealthScreen(
                    oralHealthViewModel = oralHealthViewModel,
                    childViewModel = childViewModel,
                    navController = navController
                )
            }
        }
        composable(Screen.RecordOralHealthEvent.route) {
            CapGate(childViewModel, navController, AccessCapabilities.LOG_SYMPTOMS) {
                RecordOralHealthEventScreen(
                    oralHealthViewModel = oralHealthViewModel,
                    navController = navController
                )
            }
        }

        // --- NongRak Parent Community Suite ---
        composable(Screen.Community.route) {
            com.lullabyai.app.ui.screens.community.ParentCommunityHubScreen(
                navController = navController,
                authViewModel = authViewModel,
                childViewModel = childViewModel
            )
        }
        composable(Screen.CreateCommunityPost.route) {
            com.lullabyai.app.ui.screens.community.CreateCommunityPostScreen(
                navController = navController,
                authViewModel = authViewModel,
                childViewModel = childViewModel
            )
        }
        // The same composer, opened on a chosen tab. The three entry points in the feed
        // (รูปภาพ/วิดีโอ, ตั้งกระทู้ถามหมอ, โพลสำรวจ) each land on their own kind.
        composable(
            route = Screen.CreateCommunityPostOfKind.route,
            arguments = listOf(androidx.navigation.navArgument("kind") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            com.lullabyai.app.ui.screens.community.CreateCommunityPostScreen(
                navController = navController,
                // An unknown value falls back to an ordinary post rather than crashing: the route
                // carries a plain string, and a bad link should still open something usable.
                initialKind = com.lullabyai.app.data.model.PostKind.fromWire(
                    backStackEntry.arguments?.getString("kind")
                ),
                authViewModel = authViewModel,
                childViewModel = childViewModel
            )
        }
        composable(
            route = Screen.CreateCommunityPostInGroup.route,
            arguments = listOf(
                androidx.navigation.navArgument("groupId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("kind") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            com.lullabyai.app.ui.screens.community.CreateCommunityPostScreen(
                navController = navController,
                initialKind = com.lullabyai.app.data.model.PostKind.fromWire(
                    backStackEntry.arguments?.getString("kind")
                ),
                groupId = backStackEntry.arguments?.getString("groupId"),
                authViewModel = authViewModel,
                childViewModel = childViewModel
            )
        }
        composable(
            route = Screen.ChildPicker.route,
            arguments = listOf(
                androidx.navigation.navArgument("next") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = Screen.Dashboard.route
                }
            )
        ) { backStackEntry ->
            com.lullabyai.app.ui.screens.child.ChildPickerScreen(
                navController = navController,
                nextRoute = backStackEntry.arguments?.getString("next") ?: Screen.Dashboard.route,
                childViewModel = childViewModel
            )
        }
        composable(Screen.ParentTips.route) {
            com.lullabyai.app.ui.screens.child.ParentTipsScreen(
                navController = navController,
                childViewModel = childViewModel
            )
        }
        composable(Screen.CommunityGroups.route) {
            com.lullabyai.app.ui.screens.community.CommunityGroupsScreen(navController = navController)
        }
        composable(
            route = Screen.CommunityGroupDetail.route,
            arguments = listOf(androidx.navigation.navArgument("groupId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            com.lullabyai.app.ui.screens.community.CommunityGroupDetailScreen(
                navController = navController,
                groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            )
        }
        composable(Screen.CommunityFriendRequests.route) {
            com.lullabyai.app.ui.screens.community.FriendRequestsScreen(navController = navController)
        }
        composable(Screen.CommunityDoctorArticles.route) {
            com.lullabyai.app.ui.screens.community.CommunityDoctorArticlesScreen(navController = navController)
        }
        composable(
            route = Screen.CommunityPostDetail.route,
            arguments = listOf(androidx.navigation.navArgument("postId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            com.lullabyai.app.ui.screens.community.CommunityPostDetailScreen(
                postId = backStackEntry.arguments?.getString("postId") ?: "",
                navController = navController
            )
        }
        composable(
            route = Screen.ProfessionalBlogDetail.route,
            arguments = listOf(androidx.navigation.navArgument("blogId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val blogId = backStackEntry.arguments?.getString("blogId") ?: ""
            com.lullabyai.app.ui.screens.community.ProfessionalBlogDetailScreen(
                blogId = blogId,
                navController = navController
            )
        }
        composable(
            route = Screen.CommunityUserProfile.route,
            arguments = listOf(androidx.navigation.navArgument("authorName") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val rawAuthorName = backStackEntry.arguments?.getString("authorName") ?: ""
            val authorName = java.net.URLDecoder.decode(rawAuthorName, "UTF-8")
            com.lullabyai.app.ui.screens.community.CommunityUserProfileScreen(
                authorName = authorName,
                navController = navController,
                authViewModel = authViewModel,
                childViewModel = childViewModel
            )
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
            onFinish = { finishTour() },
            onToggleLanguage = { settingsViewModel.toggleLanguage() }
        )
    }
    if (doctorTourActive) {
        com.lullabyai.app.ui.screens.onboarding.DoctorTourOverlay(
            step = doctorTourStep,
            navController = navController,
            isThai = lang == "th",
            onNext = { doctorTourStep++ },
            onBack = { if (doctorTourStep > 0) doctorTourStep-- },
            onSkip = { finishDoctorTour() },
            onFinish = { finishDoctorTour() },
            onToggleLanguage = { settingsViewModel.toggleLanguage() }
        )
    }

    accessRequestState.popup?.let { req ->
        com.lullabyai.app.ui.screens.access.PendingAccessRequestDialog(
            request = req,
            childName = accessRequestState.nameFor(req.childId),
            isThai = lang == "th",
            busy = accessRequestState.resolving,
            onApprove = { accessRequestViewModel.resolve(req.id, true) },
            onDeny = { accessRequestViewModel.resolve(req.id, false) },
            onDismiss = { accessRequestViewModel.dismissPopup() }
        )
    }
    }
    }
}

@Composable
private fun CapGate(
    childViewModel: ChildViewModel,
    navController: NavHostController,
    cap: String,
    title: String? = null,
    content: @Composable () -> Unit
) {
    com.lullabyai.app.ui.screens.access.PermissionAccessGuard(
        childViewModel = childViewModel,
        navController = navController,
        onBack = { navController.popBackStack() },
        capability = cap,
        screenTitle = title,
        content = content
    )
}
