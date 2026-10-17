package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.AccessCapabilities
import com.lullabyai.app.viewmodel.ChildViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

/**
 * Gates a parent-app route by membership capability.
 *
 * Guests hit [GuestLockGuard] on write screens. Signed-in members without the capability
 * see Stitch "จำกัดสิทธิ์การเข้าถึง (Access Denied)". Owners / co-owners always pass.
 */
@Composable
fun PermissionAccessGuard(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit,
    capability: String,
    screenTitle: String? = null,
    lockGuests: Boolean = !capability.startsWith("view_"),
    content: @Composable () -> Unit
) {
    if (childViewModel.isGuest()) {
        if (lockGuests) {
            com.lullabyai.app.ui.components.GuestLockGuard(
                childViewModel = childViewModel,
                navController = navController,
                onBack = onBack,
                title = screenTitle
            ) { content() }
        } else {
            content()
        }
        return
    }

    val selectedChildId by ChildSelection.id.collectAsState()
    var snapshot by remember(selectedChildId, capability) { mutableStateOf<AccessGateSnapshot?>(null) }
    var requested by remember(selectedChildId, capability) { mutableStateOf(false) }
    val repo = remember { ChildRepository() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedChildId, capability) {
        snapshot = null
        snapshot = loadAccessGate(repo, childViewModel, selectedChildId, capability)
    }

    when (val snap = snapshot) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        else -> if (snap.allowed) {
            content()
        } else {
            val spec = AccessCapabilities.spec(capability)
            MemberAccessBlockedScreen(
                navController = navController,
                onBack = onBack,
                title = screenTitle,
                info = AccessDeniedInfo(
                    actionTitleTh = spec?.titleTh ?: capability,
                    actionTitleEn = spec?.titleEn ?: capability,
                    memberName = snap.myName,
                    memberRoleLabel = AccessCapabilities.roleBadge(snap.myRole),
                    accessSummaryTh = AccessCapabilities.accessSummary(snap.caps, isThai = true),
                    accessSummaryEn = AccessCapabilities.accessSummary(snap.caps, isThai = false),
                    ownerName = snap.ownerName,
                    requested = requested
                ),
                                onRequestAccess = {
                                    val childId = selectedChildId
                                    if (childId != null) {
                                        requested = true
                                        scope.launch {
                                            repo.requestCapability(childId, capability, spec?.titleTh)
                                        }
                                    }
                                },
                onChat = {
                    val ownerId = snap.ownerUserId
                    if (!ownerId.isNullOrBlank()) {
                        navController.navigate(Screen.CareChat.route(ownerId))
                    } else {
                        onBack()
                    }
                },
                onHome = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

private data class AccessGateSnapshot(
    val allowed: Boolean,
    val myName: String,
    val myRole: String,
    val ownerName: String,
    val ownerUserId: String?,
    val caps: Set<String>
)

private suspend fun loadAccessGate(
    repo: ChildRepository,
    childViewModel: ChildViewModel,
    childId: String?,
    capability: String
): AccessGateSnapshot {
    val id = childId?.takeIf { it.isNotBlank() }
        ?: repo.getSelectedChild().getOrNull()?.id
    if (id.isNullOrBlank()) {
        return AccessGateSnapshot(true, "", "", "", null, emptySet())
    }
    val uid = supabase.auth.currentUserOrNull()?.id
    val child = childViewModel.uiState.value.children.find { it.id == id }
        ?: repo.getChildById(id).getOrNull()
    if (uid != null && child?.userId == uid) {
        return AccessGateSnapshot(true, "", "owner", "", uid, AccessCapabilities.ALL.toSet())
    }
    val members = repo.listAccessMembers(id).getOrDefault(emptyList())
    val me = members.find { it.userId == uid }
    val owner = members.find { it.role == "owner" }
        ?: members.find { it.userId == child?.userId }
    val caps = me?.let { AccessCapabilities.effectiveCaps(it) } ?: emptySet()
    val allowed = me != null && AccessCapabilities.holds(me, capability)
    return AccessGateSnapshot(
        allowed = allowed,
        myName = me?.displayName.orEmpty(),
        myRole = me?.role.orEmpty(),
        ownerName = owner?.displayName?.takeIf { it.isNotBlank() } ?: "เจ้าของบัญชี",
        ownerUserId = owner?.userId,
        caps = caps
    )
}
