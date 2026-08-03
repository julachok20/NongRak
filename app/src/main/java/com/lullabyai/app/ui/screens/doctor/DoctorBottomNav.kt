package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary

/** Tabs for the doctor-role bottom bar (hub destinations only). */
enum class DoctorTab { HOME, PATIENTS, SETTINGS }

/**
 * Minimal doctor bottom nav: Dashboard / Patients / Schedule / Settings.
 * Mirrors parent AppBottomNav styling without the AI FAB.
 */
@Composable
fun DoctorBottomNav(
    navController: NavController,
    selected: DoctorTab
) {
    val s = LocalStrings.current
    val items = listOf(
        DoctorNavItem(
            tab = DoctorTab.HOME,
            icon = Icons.Filled.Home,
            label = if (s.isThai) "หน้าหลัก" else "Home",
            route = Screen.DoctorDashboard.route
        ),
        DoctorNavItem(
            tab = DoctorTab.PATIENTS,
            icon = Icons.Filled.Groups,
            label = if (s.isThai) "ผู้ป่วย" else "Patients",
            route = Screen.DoctorPatientManagement.route
        ),
        // Schedule is not a footer tab — the design's footer is Home | Patients | Settings.
        // Patient Schedule hangs off the Patients hub (นัดหมาย tile) and carries a back arrow, so it
        // passes PATIENTS. The old DoctorTab.SCHEDULE value was removed outright: keeping an enum
        // constant that matches no bar item just produced a footer with nothing highlighted.
        DoctorNavItem(
            tab = DoctorTab.SETTINGS,
            icon = Icons.Filled.Settings,
            label = if (s.isThai) "ตั้งค่า" else "Settings",
            route = Screen.DoctorSettings.route
        )
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0),
        modifier = Modifier.navigationBarsPadding()
    ) {
        items.forEach { item ->
            val isSelected = item.tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (item.tab == DoctorTab.HOME) {
                        val home = Screen.DoctorDashboard.route
                        // Time & Appointment hub (and other deep screens) may sit above dashboard on
                        // the stack — popBackStack is the reliable way to return to doctor home.
                        if (navController.currentDestination?.route != home) {
                            if (!navController.popBackStack(home, false)) {
                                navController.navigate(home) { launchSingleTop = true }
                            }
                        }
                        return@NavigationBarItem
                    }
                    navController.navigate(item.route) {
                        launchSingleTop = true
                        popUpTo(Screen.DoctorDashboard.route) {
                            saveState = true
                            inclusive = false
                        }
                        restoreState = !isSelected
                    }
                },
                icon = { Icon(item.icon, item.label, modifier = Modifier.size(24.dp)) },
                label = { Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Primary,
                    selectedTextColor = Primary,
                    unselectedIconColor = Outline,
                    unselectedTextColor = Outline,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

private data class DoctorNavItem(
    val tab: DoctorTab,
    val icon: ImageVector,
    val label: String,
    val route: String
)
