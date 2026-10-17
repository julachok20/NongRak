package com.lullabyai.app.ui.screens.access

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.lullabyai.app.viewmodel.AccessCapabilities
import com.lullabyai.app.viewmodel.ChildViewModel

/**
 * Gates parent-side Pedia Connect routes. Owners always pass; invited members need `book_doctor`
 * (Stitch: Log Doctor Visits & Clinical Notes).
 */
@Composable
fun ConnectAccessGuard(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit,
    screenTitle: String? = null,
    content: @Composable () -> Unit
) {
    PermissionAccessGuard(
        childViewModel = childViewModel,
        navController = navController,
        onBack = onBack,
        capability = AccessCapabilities.BOOK_DOCTOR,
        screenTitle = screenTitle,
        lockGuests = true,
        content = content
    )
}
