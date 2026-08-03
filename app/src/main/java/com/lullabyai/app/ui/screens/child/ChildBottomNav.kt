package com.lullabyai.app.ui.screens.child

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.navigation.Screen

/**
 * Footer for all child sub-pages. Delegates to the shared [AppBottomNav]
 * with the "ข้อมูลลูก" (Child) tab selected.
 */
@Composable
fun ChildBottomNav(navController: NavController, currentTab: String) {
    AppBottomNav(
        selected = BottomTab.CHILD,
        onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
        onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
        onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
        onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
        onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
        // Stats is reachable from the dashboard's button grid on child screens, so the footer's
        // Stats slot is replaced with Connect → parent doctor-connect hub (MyDoctorsHub).
        onConnect = { navController.navigate(Screen.MyDoctorsHub.route) { launchSingleTop = true } }
    )
}
