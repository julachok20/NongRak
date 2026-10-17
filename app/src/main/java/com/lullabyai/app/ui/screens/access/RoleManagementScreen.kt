@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.ChildRole
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AccessCapabilities
import com.lullabyai.app.viewmodel.AccessRoleViewModel

/**
 * Role management: define named capability bundles and apply them to members.
 *
 * No Stitch reference exists for this screen, so it deliberately reuses the visual language of
 * "Permission Settings Detail" ([PermSection] / [PermToggle]) rather than introducing a new one.
 *
 * Every write here is owner-only, enforced in Postgres. A co_owner can open the screen and see the
 * roles but the RPCs will refuse their writes, so the UI hides the editing affordances for them
 * instead of letting them hit an error.
 */
@Composable
fun RoleManagementScreen(
    viewModel: AccessRoleViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()
    val isOwner = state.isOwner

    LaunchedEffect(Unit) { viewModel.load() }

    var confirmDelete by remember { mutableStateOf<ChildRole?>(null) }
    var assignTo by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "จัดการบทบาท" else "Manage roles",
                onBack = onBack,
                navController = navController
            )
        },
        floatingActionButton = {
            if (isOwner) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.edit(null) },
                    containerColor = Primary,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(if (isThai) "สร้างบทบาท" else "New role") }
                )
            }
        },
        bottomBar = { AccessPermissionBottomNav(navController) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isThai)
                    "แตะบทบาทเพื่อเปิด/ปิดสิทธิ์ บันทึกแล้วระบบจะบังคับใช้จริง บทบาทที่สร้างเองลบได้"
                else
                    "Tap a role to turn permissions on or off. Saves are enforced in the database. Custom roles can be deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isOwner) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (isThai) "เฉพาะเจ้าของบัญชีลูกเท่านั้นที่แก้ไขบทบาทได้"
                            else "Only the child's owner can change roles",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (state.loading && state.roles.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }

            state.roles.forEach { role ->
                val canDelete = isOwner && (!role.isSystem || role.isCustomized)
                RoleCard(
                    role = role,
                    isThai = isThai,
                    canEdit = isOwner,
                    canDelete = canDelete,
                    onEdit = { viewModel.edit(role) },
                    onDelete = { confirmDelete = role }
                )
            }

            if (isOwner && state.members.any { it.role != "owner" }) {
                Text(
                    if (isThai) "กำหนดบทบาทให้ผู้ใช้" else "Assign a role",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                state.members.filter { it.role != "owner" }.forEach { m ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AccessAvatar(m.avatarUrl, m.displayName, Modifier.size(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.displayName, fontWeight = FontWeight.Bold)
                                Text(
                                    (if (isThai) m.roleNameTh else m.roleNameEn)
                                        ?: "${m.capabilities.size} ${if (isThai) "สิทธิ์" else "permissions"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Primary
                                )
                            }
                            TextButton(onClick = { assignTo = m.userId }) {
                                Text(if (isThai) "เปลี่ยน" else "Change")
                            }
                        }
                    }
                }
            }

            state.error?.let {
                if (state.editing == null) {
                    Text(
                        roleErrorText(it, isThai),
                        color = Error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    state.editing?.let { draft ->
        val canDeleteDraft = isOwner && draft.id.isNotBlank() &&
            (!draft.isSystem || draft.isCustomized)
        RoleEditorSheet(
            draft = draft,
            isThai = isThai,
            saving = state.saving,
            canDelete = canDeleteDraft,
            error = state.error,
            onDismiss = { viewModel.closeEditor() },
            onToggle = { viewModel.toggleCapability(it) },
            onNameChange = { th, en, key -> viewModel.updateDraft(nameTh = th, nameEn = en, key = key) },
            onSave = { viewModel.saveRole() },
            onDelete = {
                confirmDelete = draft
                viewModel.closeEditor()
            }
        )
    }

    confirmDelete?.let { role ->
        val resettingBuiltin = role.isSystem && role.isCustomized
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = {
                Text(
                    when {
                        resettingBuiltin && isThai -> "คืนค่าเริ่มต้น?"
                        resettingBuiltin -> "Reset to default?"
                        isThai -> "ลบบทบาทนี้?"
                        else -> "Delete this role?"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    when {
                        resettingBuiltin && isThai ->
                            "บทบาทนี้จะกลับไปใช้สิทธิ์ค่าเริ่มต้น ผู้ใช้ที่ผูกอยู่ยังคงสิทธิ์เดิมจนกว่าจะกำหนดบทบาทใหม่"
                        resettingBuiltin ->
                            "This role will use the default permissions again. Assigned members keep their current access until you re-assign."
                        isThai ->
                            "ผู้ใช้ที่ถือบทบาทนี้จะยังมีสิทธิ์เดิมอยู่ แต่จะไม่ผูกกับบทบาทนี้อีก"
                        else ->
                            "Members holding this role keep their current permissions, but are no longer linked to it."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRole(role.id)
                    confirmDelete = null
                }) { Text(if (isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    assignTo?.let { userId ->
        AlertDialog(
            onDismissRequest = { assignTo = null },
            title = { Text(if (isThai) "เลือกบทบาท" else "Pick a role", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.roles.forEach { r ->
                        TextButton(
                            onClick = {
                                viewModel.assignRole(userId, r.id)
                                assignTo = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${if (isThai) r.nameTh else r.nameEn}  ·  ${r.capabilities.size}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { assignTo = null }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun RoleCard(
    role: ChildRole,
    isThai: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .then(if (canEdit) Modifier.clickable(onClick = onEdit) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (role.isSystem) Icons.Filled.Shield else Icons.Filled.Badge,
                    null,
                    tint = Primary
                )
                Column(Modifier.weight(1f)) {
                    Text(if (isThai) role.nameTh else role.nameEn, fontWeight = FontWeight.Bold)
                    Text(
                        buildString {
                            append(role.capabilities.size)
                            append(if (isThai) " สิทธิ์" else " permissions")
                            if (role.memberCount > 0) {
                                append(" · ")
                                append(role.memberCount)
                                append(if (isThai) " คน" else " assigned")
                            }
                            if (canEdit) {
                                append(" · ")
                                append(if (isThai) "แตะเพื่อแก้ไข" else "tap to edit")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    role.isSystem && role.isCustomized -> AssistChip(
                        onClick = {},
                        label = { Text(if (isThai) "ปรับแต่งแล้ว" else "Customized") }
                    )
                    role.isSystem -> AssistChip(
                        onClick = {},
                        label = { Text(if (isThai) "ค่าเริ่มต้น" else "Built-in") }
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, null, tint = Error)
                    }
                }
                if (canEdit) {
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                }
            }
            if (role.capabilities.isNotEmpty()) {
                Text(
                    role.capabilities.joinToString(" · ") { AccessCapabilities.label(it, isThai) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RoleEditorSheet(
    draft: ChildRole,
    isThai: Boolean,
    saving: Boolean,
    canDelete: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onNameChange: (String?, String?, String?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val isBuiltin = draft.isSystem && draft.baseRoleKey != null
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                when {
                    draft.id.isBlank() && isThai -> "สร้างบทบาทใหม่"
                    draft.id.isBlank() -> "New role"
                    isBuiltin && isThai -> "แก้ไขสิทธิ์ของบทบาทนี้ (เฉพาะลูกคนนี้)"
                    isBuiltin -> "Edit this role's permissions (this child only)"
                    isThai -> "แก้ไขบทบาท"
                    else -> "Edit role"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!draft.isSystem) {
                OutlinedTextField(
                    value = draft.nameTh,
                    onValueChange = { onNameChange(it, null, null) },
                    label = { Text(if (isThai) "ชื่อ (ไทย)" else "Name (Thai)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.nameEn,
                    onValueChange = { onNameChange(null, it, null) },
                    label = { Text(if (isThai) "ชื่อ (อังกฤษ)" else "Name (English)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Existing roles keep their key: it is the row's identity in the upsert.
            if (draft.id.isBlank()) {
                OutlinedTextField(
                    value = draft.key,
                    onValueChange = { onNameChange(null, null, it) },
                    label = { Text(if (isThai) "รหัสบทบาท (a-z, _)" else "Role key (a-z, _)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (draft.isSystem) {
                OutlinedTextField(
                    value = if (isThai) draft.nameTh else draft.nameEn,
                    onValueChange = { if (isThai) onNameChange(it, null, null) else onNameChange(null, it, null) },
                    label = { Text(if (isThai) "ชื่อที่แสดง" else "Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AccessCapabilities.SECTIONS.forEach { section ->
                PermSection(
                    icon = permissionSectionIcon(section.id),
                    titleTh = section.titleTh,
                    titleEn = section.titleEn
                ) {
                    section.items.forEach { item ->
                        PermToggle(
                            titleTh = item.titleTh,
                            titleEn = item.titleEn,
                            checked = item.key in draft.capabilities
                        ) { onToggle(item.key) }
                    }
                }
            }

            PrimaryButton(
                text = if (isThai) "บันทึกสิทธิ์" else "Save permissions",
                onClick = onSave,
                isLoading = saving
            )

            error?.let {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.08f))
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = Error)
                        Text(roleErrorText(it, isThai), color = Error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (canDelete) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, null, tint = Error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (draft.isSystem && draft.isCustomized) {
                            if (isThai) "คืนค่าเริ่มต้น" else "Reset to default"
                        } else {
                            if (isThai) "ลบบทบาท" else "Delete role"
                        },
                        color = Error
                    )
                }
            }
        }
    }
}

/** Maps the RPC's raise messages onto something a parent can act on. */
private fun roleErrorText(raw: String, isThai: Boolean): String = when {
    raw.contains("only the owner") ->
        if (isThai) "เฉพาะเจ้าของบัญชีลูกเท่านั้นที่จัดการบทบาทได้" else "Only the child's owner can manage roles"
    raw.contains("reserved by a built-in") ->
        if (isThai) "รหัสนี้ถูกใช้โดยบทบาทค่าเริ่มต้นแล้ว" else "That key is already used by a built-in role"
    raw.contains("invalid role key") || raw == "invalid_key" ->
        if (isThai) "รหัสบทบาทต้องเป็น a-z, 0-9 หรือ _ อย่างน้อย 2 ตัว" else "Role key must be a-z, 0-9 or _, at least 2 characters"
    raw.contains("cannot change your own role") ->
        if (isThai) "เปลี่ยนบทบาทของตัวเองไม่ได้" else "You can't change your own role"
    raw.contains("cannot change the owner") ->
        if (isThai) "เปลี่ยนบทบาทของเจ้าของไม่ได้" else "The owner's role can't be changed"
    raw == "no_child" ->
        if (isThai) "ยังไม่ได้เลือกลูก" else "No child selected"
    else -> raw
}
