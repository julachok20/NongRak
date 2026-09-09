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
        onCommunity = { navController.navigate(Screen.Community.route) { launchSingleTop = true } }
    )
}
