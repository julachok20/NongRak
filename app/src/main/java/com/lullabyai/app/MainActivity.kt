package com.lullabyai.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.lullabyai.app.data.model.hasDoctorRole
import com.lullabyai.app.data.model.hasParentRole
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.localization.stringsFor
import com.lullabyai.app.ui.navigation.AppNavigation
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.NongRakTheme
import com.lullabyai.app.viewmodel.SettingsViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    companion object {
        /** Intent extra key widgets use to request a specific screen instead of the default Dashboard. */
        const val WIDGET_TARGET_ROUTE_EXTRA = "widget_target_route"

        /**
         * The route an intent is asking for: a widget extra, or a nongrak://open/<route> link.
         *
         * Both funnel into one value so there is a single route-jump mechanism. The path is taken
         * verbatim rather than mapped through a whitelist because AppNavigation already ignores
         * routes it does not know — an unknown one lands on the default screen, not a crash.
         */
        fun targetRoute(intent: Intent?): String? {
            intent?.getStringExtra(WIDGET_TARGET_ROUTE_EXTRA)?.let { return it }
            val uri = intent?.data ?: return null
            if (uri.scheme != "nongrak" || uri.host != "open") return null
            return uri.pathSegments?.firstOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels()
    private var pendingDeepLink by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = targetRoute(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLink = targetRoute(intent)

        // Load persisted state into process-wide holders (multi-child selection + recent services).
        val settingsManager = com.lullabyai.app.data.settings.SettingsManager(applicationContext)
        com.lullabyai.app.data.settings.ChildSelection.init(settingsManager)
        com.lullabyai.app.data.settings.RecentServices.init(settingsManager)

        var isReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isReady }

        setContent {
            val settings by settingsViewModel.settings.collectAsState()

            // Ask for notification permission (Android 13+) so vaccine/health reminders can pop up.
            val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            NongRakTheme(darkTheme = settings.darkMode) {
                CompositionLocalProvider(LocalStrings provides stringsFor(settings.language)) {
                    val navController = rememberNavController()
                    var startDestination by remember { mutableStateOf(Screen.Welcome.route) }

                    LaunchedEffect(Unit) {
                        try {
                            // Wait until Supabase has restored the saved session from disk.
                            // Without this, a cold start reads the session before it loads → user looks signed out.
                            supabase.auth.awaitInitialization()
                            val session = supabase.auth.currentSessionOrNull()
                            startDestination = if (session != null) {
                                val profile = com.lullabyai.app.data.repository.AuthRepository()
                                    .getProfile().getOrNull()
                                val lastPortal = settingsManager.getLastAuthPortalSync()
                                    .takeIf { it != SettingsManager.PORTAL_PARENT }
                                    ?: settingsManager.getLastAuthPortal()
                                when {
                                    profile?.hasParentRole() == true && profile.hasDoctorRole() -> {
                                        if (lastPortal == SettingsManager.PORTAL_DOCTOR) {
                                            Screen.DoctorDashboard.route
                                        } else {
                                            var seenTour = settingsManager.hasSeenTour.first()
                                            if (!seenTour) {
                                                val hasExistingChild = com.lullabyai.app.data.repository.ChildRepository()
                                                    .getChildren().getOrNull()?.isNotEmpty() == true
                                                if (hasExistingChild) {
                                                    settingsManager.setHasSeenTour(true)
                                                    seenTour = true
                                                }
                                            }
                                            if (seenTour) Screen.Dashboard.route else Screen.FirstRunTour.route
                                        }
                                    }
                                    profile?.hasParentRole() == true -> {
                                        var seenTour = settingsManager.hasSeenTour.first()
                                        if (!seenTour) {
                                            val hasExistingChild = com.lullabyai.app.data.repository.ChildRepository()
                                                .getChildren().getOrNull()?.isNotEmpty() == true
                                            if (hasExistingChild) {
                                                settingsManager.setHasSeenTour(true)
                                                seenTour = true
                                            }
                                        }
                                        if (seenTour) Screen.Dashboard.route else Screen.FirstRunTour.route
                                    }
                                    profile?.hasDoctorRole() == true -> Screen.DoctorDashboard.route
                                    else -> Screen.Welcome.route
                                }
                            } else Screen.Welcome.route
                        } catch (e: Exception) {
                            startDestination = Screen.Welcome.route
                        }
                        isReady = true
                    }

                    if (isReady) {
                        androidx.compose.foundation.layout.Box {
                            // Every sign-in path funnels through here, so device trust is checked
                            // once rather than at each of the dozen places that navigate to
                            // Dashboard — eleven of which are easy to update and one easy to miss.
                            com.lullabyai.app.ui.components.DeviceTrustGate(settingsManager) {
                                AppNavigation(
                                    navController = navController,
                                    startDestination = startDestination,
                                    settingsViewModel = settingsViewModel
                                )
                            }
                            // Drawn last so it floats over whatever screen is showing. Tapping it
                            // opens the notification list, which is where the full text lives.
                            com.lullabyai.app.ui.components.InAppAlertHost(
                                onOpen = {
                                    runCatching { navController.navigate(Screen.Notifications.route) }
                                }
                            )
                        }
                        LaunchedEffect(pendingDeepLink) {
                            pendingDeepLink?.let { route ->
                                navController.navigate(route)
                                pendingDeepLink = null
                            }
                        }
                    }
                }
            }
        }
    }
}
