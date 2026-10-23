@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.settings.AccountField
import com.lullabyai.app.ui.screens.settings.EditAccountFieldDialog
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.FamilyRoster
import com.lullabyai.app.viewmodel.AccessPermissionViewModel
import com.lullabyai.app.viewmodel.AccessRoleLabels
import com.lullabyai.app.viewmodel.AuthViewModel

/**
 * จัดการครอบครัว — who is in this family, and what each of them may do.
 *
 * This is the PARENT's screen. The parent owns the account and decides who may reach their child's
 * record; the carer-facing screens (handover, carer updates, care chat) answer a different question
 * for a different person and deliberately share no layout with this one.
 *
 * It used to be called "การจัดการสิทธิ์เข้าถึง / Permission access" and led with permissions, which
 * is how the system thinks rather than how a parent does. A parent thinks about PEOPLE: who is
 * grandma, who is the nanny, what can each of them reach, what have they been recording. So every
 * row here is a person first and the permissions hang off them.
 *
 * Only an owner or co-owner may change anything; everyone else reads the same list, inert.
 */
@Composable
fun PermissionAccessScreen(
    viewModel: AccessPermissionViewModel,
    navController: NavController,
    onBack: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
        authViewModel.loadProfile()
    }

    val iCanManage = AccessRoleLabels.isFullAccess(state.myRole)
    val isOwner = state.isOwner
    var confirmLeave by remember { mutableStateOf(false) }
    var editingFamilyName by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ChildAccessMember?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    // family_name lives on the OWNER's profile, so only the owner gets the pencil; a co-owner sees
    // the name and cannot rewrite someone else's family.
    if (editingFamilyName) {
        EditAccountFieldDialog(
            field = AccountField.FAMILY_NAME,
            current = authState.profile?.familyName,
            isThai = isThai,
            onDismiss = { editingFamilyName = false },
            onSaved = { message ->
                editingFamilyName = false
                toast = message
                authViewModel.loadProfile()
            }
        )
    }

    removing?.let { member ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = {
                Text(
                    if (isThai) "นำ${member.displayName}ออกจากครอบครัว?"
                    else "Remove ${member.displayName}?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                // Says what is lost AND what is kept. The records this person wrote stay in the
                // child's history, which is not obvious and is the thing people worry about.
                Text(
                    if (isThai)
                        "${member.displayName}จะเข้าถึงข้อมูลของ${state.childName}ไม่ได้อีก " +
                            "บันทึกที่เคยเพิ่มไว้จะยังอยู่ในประวัติของเด็ก " +
                            "หากต้องการให้กลับมาดูแลอีกครั้ง ต้องเชิญใหม่"
                    else
                        "${member.displayName} will lose access to ${state.childName}'s data. " +
                            "Records they already wrote stay in the child's history. To bring them " +
                            "back you would have to invite them again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMember(member.userId)
                    removing = null
                }) {
                    Text(
                        if (isThai) "นำออก" else "Remove",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = {
                Text(
                    if (isThai) "ออกจากการดูแล?" else "Leave this child?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isThai)
                        "คุณจะเข้าถึงข้อมูลของ${state.childName}ไม่ได้อีก " +
                            "บันทึกที่คุณเคยเพิ่มไว้จะยังอยู่ในประวัติของเด็ก " +
                            "หากต้องการกลับมาดูแลอีกครั้ง ต้องขอสิทธิ์ใหม่จากเจ้าของ"
                    else
                        "You will lose access to ${state.childName}'s data. Records you already " +
                            "wrote stay in the child's history. To return you must request access " +
                            "from the owner again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    state.myUserId?.let { viewModel.removeMember(it) }
                    onBack()
                }) {
                    Text(
                        if (isThai) "ออกจากการดูแล" else "Leave",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            toast = null
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "จัดการครอบครัว" else "Manage family",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { AccessPermissionBottomNav(navController) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            FamilyIdentityCard(
                familyName = FamilyRoster.name(authState.profile, isThai),
                hasCustomName = !authState.profile?.familyName.isNullOrBlank(),
                childName = state.childName,
                memberCount = state.members.size,
                canRename = isOwner,
                isThai = isThai,
                onRename = { editingFamilyName = true }
            )

            // Bringing someone in, the three ways this app supports it.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAccessActionTile(
                    title = if (isThai) "เชิญคน" else "Invite",
                    subtitle = if (isThai) "อีเมลหรือชื่อ" else "Email or name",
                    icon = Icons.Filled.PersonAdd,
                    iconTint = Color.White,
                    iconBg = Primary,
                    isPrimary = true,
                    enabled = iCanManage,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.AddUserAccessActivity.route) }
                )
                QuickAccessActionTile(
                    title = if (isThai) "QR" else "QR code",
                    subtitle = if (isThai) "ให้สแกนตรงนี้" else "Scan here",
                    icon = Icons.Filled.QrCode2,
                    iconTint = Color(0xFF7B1FA2),
                    iconBg = Color(0xFFF3E5F5),
                    isPrimary = false,
                    enabled = iCanManage,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.ShareAccessQr.route) }
                )
                QuickAccessActionTile(
                    title = if (isThai) "บทบาท" else "Roles",
                    subtitle = if (isThai) "พี่เลี้ยง ยาย…" else "Nanny, grandma…",
                    icon = Icons.Filled.Badge,
                    iconTint = Color(0xFF00897B),
                    iconBg = Color(0xFFE0F2F1),
                    isPrimary = false,
                    enabled = iCanManage,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.RoleManagement.route) }
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isThai) "คนที่ดูแล${state.childName.ifBlank { "ลูกน้อย" }}"
                    else "Who looks after ${state.childName.ifBlank { "this child" }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(alpha = 0.10f)) {
                    Text(
                        if (isThai) "${state.members.size} คน" else "${state.members.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                state.members.forEach { m ->
                    val permissionsLocked = AccessRoleLabels.isFullAccess(m.role)
                    val canManageThisMember =
                        if (isOwner) m.userId != state.myUserId
                        else (iCanManage && !permissionsLocked)

                    PersonCard(
                        member = m,
                        isMe = m.userId == state.myUserId,
                        canManage = canManageThisMember,
                        canToggleFlags = iCanManage,
                        isThai = isThai,
                        onPermissions = {
                            navController.navigate(Screen.PermissionSettingsDetail.route(m.userId))
                        },
                        onActivity = {
                            navController.navigate(Screen.ActivityHistory.forAuthor(m.userId))
                        },
                        onToggleFamily = { viewModel.setFamily(m.userId, !m.isFamily) },
                        onToggleCaregiver = { viewModel.setCaregiver(m.userId, !m.isCaregiver) },
                        onRemove = { removing = m }
                    )
                }
            }

            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(4.dp))

            AccessAdminBentoCard(
                icon = Icons.Filled.Timeline,
                title = if (isThai) "ประวัติทั้งครอบครัว" else "Everything, by everyone",
                subtitle = if (isThai) "ทุกบันทึก พร้อมชื่อคนที่บันทึกและการแก้ไข"
                else "Every record for this child, with author and edits",
                iconTint = Color(0xFF3949AB),
                iconBg = Color(0xFFE8EAF6),
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(Screen.ActivityHistory.route) }
            )

            Spacer(Modifier.height(4.dp))

            // Anyone who joined can leave. The owner cannot.
            val myId = state.myUserId
            if (myId != null && state.myRole != "owner") {
                TextButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Filled.Logout, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "ออกจากการดูแลเด็กคนนี้" else "Leave this child",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.VerifiedUser, null,
                        tint = HealthGood, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "ข้อมูลลูกของคุณถูกปกป้องไว้"
                        else "Your child's data is protected",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (isThai) "ข้อมูลสุขภาพของคุณได้รับการปกป้องตามมาตรฐาน HIPAA และ PDPA เราจะไม่แบ่งปันข้อมูลหากไม่ได้รับอนุญาตจากคุณ"
                    else "Your health data is protected under HIPAA and PDPA principles. We never share it without your permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isThai) "เรียนรู้เพิ่มเติม" else "Learn more",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { navController.navigate(Screen.PrivacyPolicy.route) }
                )
            }
        }
    }
}

/**
 * The family, named.
 *
 * `profiles.family_name` could already be set in Settings, but a parent thinking about their family
 * is standing here, not there - so the pencil lives here too. The displayed name falls back through
 * [FamilyRoster.name], the same fallback the home card uses, so the two can never disagree.
 */
@Composable
private fun FamilyIdentityCard(
    familyName: String,
    hasCustomName: Boolean,
    childName: String,
    memberCount: Int,
    canRename: Boolean,
    isThai: Boolean,
    onRename: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Primary.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Diversity3, null, tint = Primary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    familyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (isThai) "$memberCount คนดูแล${childName.ifBlank { "ลูกน้อย" }}"
                    else "$memberCount looking after ${childName.ifBlank { "this child" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canRename) {
                FilledTonalIconButton(
                    onClick = onRename,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Primary.copy(alpha = 0.1f),
                        contentColor = Primary
                    )
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = if (isThai) "ตั้งชื่อครอบครัว" else "Name this family",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Nudged only while the name is still the automatic one; once named, this prompt would be
        // noise on every single visit.
        if (canRename && !hasCustomName) {
            Text(
                if (isThai) "ตั้งชื่อครอบครัวได้ ชื่อนี้จะขึ้นบนหน้าแรก"
                else "You can name your family. It shows on the home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One person, and everything a parent can do about them.
 *
 * Person-first by design: face and name at the top, then what they are to this family, then what
 * they may reach and what they have been recording. The two pills answer two different questions
 * and are deliberately not one switch - a nanny IS a caregiver (she belongs in handover and the
 * carer chat) while simply not being family on anybody's home screen.
 */
@Composable
private fun PersonCard(
    member: ChildAccessMember,
    isMe: Boolean,
    canManage: Boolean,
    canToggleFlags: Boolean,
    isThai: Boolean,
    onPermissions: () -> Unit,
    onActivity: () -> Unit,
    onToggleFamily: () -> Unit,
    onToggleCaregiver: () -> Unit,
    onRemove: () -> Unit
) {
    val (badge, isFull, canLog) = AccessRoleLabels.accessLevel(member, isThai)
    // A renamed or custom role has to win here, or "ป้าแดง" renders as a bare role key.
    val roleTitle = (if (isThai) member.roleNameTh else member.roleNameEn)
        ?.takeIf { it.isNotBlank() }
        ?: AccessRoleLabels.title(member.role, isThai)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccessAvatar(member.avatarUrl, member.displayName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        member.displayName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isMe) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Primary.copy(alpha = 0.10f)
                        ) {
                            Text(
                                if (isThai) "คุณ" else "you",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    roleTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // What they are to this family.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RelationPill(
                on = member.isFamily,
                onLabel = if (isThai) "คนในครอบครัว" else "Family",
                offLabel = if (isThai) "ผู้ช่วยเหลือ" else "Helper",
                onIcon = Icons.Filled.Diversity3,
                offIcon = Icons.Filled.WorkOutline,
                enabled = canToggleFlags,
                onClick = onToggleFamily
            )
            RelationPill(
                on = member.isCaregiver,
                onLabel = if (isThai) "ช่วยดูแลลูก" else "Hands-on carer",
                offLabel = if (isThai) "ไม่ได้ดูแลเอง" else "Not hands-on",
                onIcon = Icons.Filled.VolunteerActivism,
                offIcon = Icons.Filled.PersonOutline,
                enabled = canToggleFlags,
                onClick = onToggleCaregiver
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

        // What they may reach, and what they have been doing with it.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val badgeTint = when {
                isFull -> HealthGood
                canLog -> Color(0xFFF59E0B)
                else -> Primary
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    when {
                        isFull -> Icons.Filled.CheckCircle
                        canLog -> Icons.Filled.EditNote
                        else -> Icons.Filled.Visibility
                    },
                    null, tint = badgeTint, modifier = Modifier.size(14.dp)
                )
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeTint,
                    fontSize = 11.sp
                )
            }

            PersonAction(
                icon = Icons.Filled.Timeline,
                label = if (isThai) "ดูบันทึก" else "Activity",
                onClick = onActivity
            )
            if (canManage) {
                PersonAction(
                    icon = Icons.Filled.Tune,
                    label = if (isThai) "สิทธิ์" else "Access",
                    onClick = onPermissions
                )
                PersonAction(
                    icon = Icons.Filled.PersonRemove,
                    label = if (isThai) "นำออก" else "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onRemove
                )
            }
        }
    }
}

/** One of the two "what is this person to us" switches. */
@Composable
private fun RelationPill(
    on: Boolean,
    onLabel: String,
    offLabel: String,
    onIcon: ImageVector,
    offIcon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(100.dp),
        color = if (on) Primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (on) androidx.compose.foundation.BorderStroke(
            1.dp, Primary.copy(alpha = 0.3f)
        ) else null
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (on) onIcon else offIcon,
                contentDescription = null,
                tint = if (on) Primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(14.dp)
            )
            Text(
                if (on) onLabel else offLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (on) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            )
        }
    }
}

/** A labelled action on a person row. Labelled, because an icon alone is a guess at this size. */
@Composable
private fun PersonAction(
    icon: ImageVector,
    label: String,
    tint: Color = Primary,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontSize = 10.sp
        )
    }
}

/** 3-column top quick action tile for inviting a person, the QR code, and roles. */
@Composable
private fun QuickAccessActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    isPrimary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPrimary) Primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
            )
            .border(
                1.dp,
                if (isPrimary) Primary.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            maxLines = 2
        )
    }
}

/** A wide, flat row for the things that are not about one single person. */
@Composable
private fun AccessAdminBentoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight, null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}
