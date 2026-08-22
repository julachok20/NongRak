package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel

/**
 * Gates parent-side Pedia Connect routes. Owners always pass; invited members need `book_doctor`
 * capability (set by the owner in Permission Settings / Role Management).
 */
@Composable
fun ConnectAccessGuard(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit,
    screenTitle: String? = null,
    content: @Composable () -> Unit
) {
    if (childViewModel.isGuest()) {
        com.lullabyai.app.ui.components.GuestLockGuard(
            childViewModel = childViewModel,
            navController = navController,
            onBack = onBack,
            title = screenTitle
        ) {
            content()
        }
        return
    }

    val selectedChildId by ChildSelection.id.collectAsState()
    var allowed by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(selectedChildId) {
        allowed = null
        allowed = childViewModel.canAccessDoctorConnect(selectedChildId)
    }

    when (allowed) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        false -> MemberAccessBlockedScreen(
            navController = navController,
            onBack = onBack,
            title = screenTitle
        )
        true -> content()
    }
}
