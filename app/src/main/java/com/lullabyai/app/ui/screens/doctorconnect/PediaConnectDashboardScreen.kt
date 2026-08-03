@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.repository.PediaConnectRepository
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Pedia Connect / เลือกบัญชีลูก" — the child chooser under [ConnectToDoctorWelcomeScreen].
 *
 * Only children with a `pediaconnect_patients` row appear in the list. App children who were never
 * registered on Pedia Connect are not shown here (no duplicate child info).
 */
@Composable
fun PediaConnectDashboardScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val pediaRepo = remember { PediaConnectRepository() }
    var registeredIds by remember { mutableStateOf<Set<String>?>(null) }

    LaunchedEffect(state.children) {
        registeredIds = withContext(Dispatchers.IO) {
            pediaRepo.registeredChildIdsAmong(state.children.map { it.id }).getOrDefault(emptySet())
        }
    }

    val registeredChildren = remember(state.children, registeredIds) {
        val ids = registeredIds ?: emptySet()
        state.children.filter { it.id in ids }
    }
    val loadingRegistrations = registeredIds == null && state.children.isNotEmpty()

    fun openPediaRegistration() {
        if (state.children.isEmpty()) {
            navController.navigate(Screen.AddChildChoice.route)
        } else {
            navController.navigate(Screen.PediaConnectChooseChild.route)
        }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = "Pedia Connect",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { ConnectBottomNav() },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pedia Connect", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (s.isThai) "เลือกลูกที่ลงทะเบียน Pedia Connect แล้วเพื่อใช้งาน"
                        else "Select a child already registered with Pedia Connect",
                        style = MaterialTheme.typography.bodyMedium, color = Outline
                    )
                }
            }

            when {
                state.children.isEmpty() -> {
                    item {
                        Text(
                            if (s.isThai) "ยังไม่มีข้อมูลบุตร — เพิ่มลูกในแอปก่อน แล้วค่อยลงทะเบียน Pedia Connect"
                            else "No children in the app yet — add a child first, then register for Pedia Connect.",
                            color = Outline, modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                loadingRegistrations -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
                registeredChildren.isEmpty() -> {
                    item {
                        Text(
                            if (s.isThai) "ยังไม่มีลูกที่ลงทะเบียน Pedia Connect — กดลงทะเบียนด้านล่างเมื่อพร้อม"
                            else "No Pedia Connect registration yet — use Register below when you're ready.",
                            color = Outline, modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                else -> {
                    items(registeredChildren, key = { it.id }) { child ->
                        ChildRow(child, s.isThai) {
                            ChildSelection.set(child.id)
                            navController.navigate(Screen.MyDoctorsHub.route)
                        }
                    }
                }
            }

            item {
                Card(
                    onClick = { openPediaRegistration() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.06f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(48.dp).background(Primary.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Add, null, tint = Primary) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (s.isThai) "ลงทะเบียน Pedia Connect" else "Register for Pedia Connect",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (s.isThai) "ลงทะเบียนแยกจากการเพิ่มลูกในแอป — เลือกลูกเพื่อดึงข้อมูลมาเติม"
                                else "Separate from app add-child — pick a child to sync info into the form",
                                style = MaterialTheme.typography.bodySmall, color = Outline
                            )
                        }
                        Button(onClick = { openPediaRegistration() }, shape = RoundedCornerShape(100.dp)) {
                            Text(if (s.isThai) "เพิ่มข้อมูล" else "Add", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = Outline, modifier = Modifier.size(16.dp))
                    Text(
                        if (s.isThai) "ข้อมูลทางการแพทย์ของคุณได้รับการคุ้มครองด้วยมาตรฐานสากล"
                        else "Your medical data is protected by international standards",
                        style = MaterialTheme.typography.labelSmall, color = Outline
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildRow(child: Child, isThai: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DoctorAvatar(child.avatarUrl, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    child.name.ifBlank { if (isThai) "ไม่ระบุชื่อ" else "Unnamed" },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                )
                Text(childAgeLabel(child, isThai), style = MaterialTheme.typography.bodySmall, color = Outline)
            }
            Button(onClick = onSelect, shape = RoundedCornerShape(100.dp)) {
                Text(if (isThai) "เลือก" else "Select", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
