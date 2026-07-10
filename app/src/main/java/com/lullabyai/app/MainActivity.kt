package com.lullabyai.app

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

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                                // A child is no longer required to enter the app — Dashboard now nudges
                                // childless accounts to add/sync one instead of gating startup on it.
                                // Covers accounts that already had a child before this tour existed —
                                // new signups already go through it via LastSetup's onComplete.
                                val seenTour = com.lullabyai.app.data.settings.SettingsManager(applicationContext).hasSeenTour.first()
                                if (seenTour) Screen.Dashboard.route else Screen.FirstRunTour.route
                            } else Screen.Welcome.route
                        } catch (e: Exception) {
                            startDestination = Screen.Welcome.route
                        }
                        isReady = true
                    }

                    if (isReady) {
                        AppNavigation(
                            navController = navController,
                            startDestination = startDestination,
                            settingsViewModel = settingsViewModel
                        )
                    }
                }
            }
        }
    }
}
