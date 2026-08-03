@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import com.lullabyai.app.ui.theme.HealthGood
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Parent picks which app child to register on Pedia Connect before the intake form. */
@Composable
fun PediaConnectChooseChildScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val pediaRepo = remember { PediaConnectRepository() }
    var registeredIds by remember { mutableStateOf<Set<String>?>(null) }

    LaunchedEffect(state.children) {
        registeredIds = withContext(Dispatchers.IO) {
            pediaRepo.registeredChildIdsAmong(state.children.map { it.id }).getOrDefault(emptySet())
        }
    }

    fun pickChild(child: Child) {
        ChildSelection.set(child.id)
        navController.navigate(Screen.PatientRegistration.route(PATIENT_REGISTRATION_NO_DOCTOR))
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "เลือกลูกในแอป" else "Choose app child",
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
                    Text(
                        if (s.isThai) "เลือกลูกที่ต้องการลงทะเบียน Pedia Connect"
                        else "Select a child to register on Pedia Connect",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (s.isThai) "ข้อมูลจากโปรไฟล์ลูกในแอปจะถูกดึงมาเติมในฟอร์ม — ไม่แก้โปรไฟล์ลูกโดยอัตโนมัติ"
                        else "App profile fields prefill the form — your app child profile is not changed automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Outline
                    )
                }
            }
            when {
                state.children.isEmpty() -> {
                    item {
                        Text(
                            if (s.isThai) "ยังไม่มีลูกในแอป — เพิ่มลูกก่อน"
                            else "No children in the app — add a child first.",
                            color = Outline,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        Button(
                            onClick = { navController.navigate(Screen.AddChildChoice.route) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(if (s.isThai) "เพิ่มลูก" else "Add child")
                        }
                    }
                }
                registeredIds == null -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
                else -> {
                    items(state.children, key = { it.id }) { child ->
                        val already = child.id in registeredIds!!
                        ChooseChildRow(
                            child = child,
                            isThai = s.isThai,
                            alreadyRegistered = already,
                            onSelect = { pickChild(child) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseChildRow(child: Child, isThai: Boolean, alreadyRegistered: Boolean, onSelect: () -> Unit) {
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(childAgeLabel(child, isThai), style = MaterialTheme.typography.bodySmall, color = Outline)
                if (alreadyRegistered) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = HealthGood, modifier = Modifier.size(14.dp))
                        Text(
                            if (isThai) "ลงทะเบียนแล้ว — อัปเดตข้อมูลได้" else "Registered — tap to update",
                            style = MaterialTheme.typography.labelSmall,
                            color = HealthGood
                        )
                    }
                }
            }
            Button(onClick = onSelect, shape = RoundedCornerShape(100.dp)) {
                Text(if (isThai) "เลือก" else "Select", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
