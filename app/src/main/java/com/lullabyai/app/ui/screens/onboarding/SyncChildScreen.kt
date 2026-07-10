package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.components.BirthDatePickerField
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

/**
 * Requester side of "sync child": find another parent's child by exact name+birthdate, or redeem an
 * invite code they shared. Either path creates a pending request — the owner must confirm before any
 * access is granted (see child_sync_requests / confirm_child_sync_request).
 */
@Composable
fun SyncChildScreen(childViewModel: ChildViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var birthdate by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { childViewModel.clearSyncState() } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) }
                Text(if (isThai) "เชื่อมข้อมูลลูก" else "Sync child", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
            }

            if (state.syncRequestSent) {
                Spacer(Modifier.height(40.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            if (isThai) "ส่งคำขอแล้ว" else "Request sent",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (isThai) "รอผู้ปกครองอีกท่านยืนยันคำขอของคุณ คุณจะได้รับการแจ้งเตือนเมื่อยืนยันแล้ว"
                            else "Waiting for the other parent to confirm. You'll get a notification once they do.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = if (isThai) "เสร็จสิ้น" else "Done", onClick = onDone)
                    }
                }
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Primary) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (isThai) "ค้นหาด้วยชื่อ" else "Search by name") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(if (isThai) "ใช้รหัสเชิญ" else "Invite code") })
            }
            Spacer(Modifier.height(20.dp))

            if (tab == 0) {
                LabelField(if (isThai) "ชื่อลูก" else "Child's name") {
                    OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), singleLine = true)
                }
                Spacer(Modifier.height(12.dp))
                LabelField(if (isThai) "วันเกิด" else "Birthdate") {
                    BirthDatePickerField(value = birthdate, onValueChange = { birthdate = it }, placeholder = if (isThai) "แตะเพื่อเลือกวันเกิดจากปฏิทิน" else "Tap to pick a date", fieldColors = { fieldColors() })
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (isThai) "ค้นหา" else "Search",
                    onClick = { childViewModel.searchChildForSync(name, birthdate) },
                    enabled = name.isNotBlank() && birthdate.isNotBlank() && !state.syncSearching
                )

                Spacer(Modifier.height(16.dp))
                if (state.syncSearching) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
                } else if (state.syncError != null) {
                    Text(state.syncError ?: "", color = Error, style = MaterialTheme.typography.bodySmall)
                } else if (state.syncSearchResults.isEmpty() && name.isNotBlank()) {
                    Text(
                        if (isThai) "ไม่พบข้อมูลลูกที่ตรงกัน ตรวจสอบชื่อและวันเกิดอีกครั้ง" else "No matching child found. Double-check the name and birthdate.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.syncSearchResults) { match ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Filled.Search, null, tint = Primary)
                                    Text(match.childName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { childViewModel.requestSyncBySearch(match.childId) }) {
                                        Text(if (isThai) "ขอเชื่อมข้อมูล" else "Request sync")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LabelField(if (isThai) "รหัสเชิญ" else "Invite code") {
                    OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), singleLine = true)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isThai) "ขอรหัสจากผู้ปกครองที่สร้างข้อมูลลูกไว้แล้ว" else "Ask the parent who already created the child for their code",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (isThai) "ยืนยันรหัส" else "Submit code",
                    onClick = { childViewModel.requestSyncByCode(code) },
                    enabled = code.isNotBlank()
                )
                state.syncError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
