@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.model.VaccineVerificationRequest
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorVaccineVerificationHubViewModel

/** Doctor-side vaccine verification queue — the Stitch source screen for this resource actually
 *  renders a single-record confirm-and-send flow (patient card + vaccine details + a "send
 *  verification" button), not a list; adapted here into a real list of pending requests (each
 *  card keeps that same layout language — patient chip, vaccine detail block, primary action)
 *  since the doctor may have more than one pending verification at a time. Mirrors Stitch
 *  "Vaccine Verification Hub (Doctor Side)". */
@Composable
fun DoctorVaccineVerificationHubScreen(navController: NavController, vm: DoctorVaccineVerificationHubViewModel = viewModel()) {
    val s = LocalStrings.current
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(state.lastResolved) {
        if (state.lastResolved != null) navController.navigate(Screen.DoctorVaccineVerificationSent.route)
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ยืนยันผลวัคซีน" else "Vaccine Verification",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) } }
            } else if (state.pending.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.VerifiedUser, null, tint = Outline, modifier = Modifier.size(40.dp))
                        Text(if (s.isThai) "ไม่มีคำขอยืนยันวัคซีนที่รอดำเนินการ" else "No pending vaccine verifications", color = Outline)
                    }
                }
            } else {
                items(state.pending, key = { it.id }) { req ->
                    VerificationCard(
                        req = req, child = state.childrenById[req.childId], record = state.vaccineRecordsById[req.vaccineRecordId], isThai = s.isThai,
                        onApprove = { vm.resolve(req.id, true, null) },
                        onReject = { vm.resolve(req.id, false, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationCard(req: VaccineVerificationRequest, child: Child?, record: VaccineRecord?, isThai: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(SecondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChildCare, null, tint = OnSecondaryContainer)
                }
                Column {
                    Text(child?.name ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text((if (isThai) "รอการยืนยัน" else "Awaiting verification"), style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
            Surface(shape = RoundedCornerShape(0.dp), color = Primary) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isThai) "ข้อมูลวัคซีน" else "VACCINE RECORD", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    Icon(Icons.Filled.VerifiedUser, null, tint = Color.White)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(if (isThai) "วัคซีน" else "Vaccine", style = MaterialTheme.typography.labelSmall, color = Outline)
                        Text(record?.vaccineName ?: "-", style = MaterialTheme.typography.bodyLarge)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (isThai) "วันที่ได้รับ" else "Date given", style = MaterialTheme.typography.labelSmall, color = Outline)
                        Text(record?.dateGiven ?: "-", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                        Text(if (isThai) "ไม่ยืนยัน" else "Reject")
                    }
                    Button(onClick = onApprove, modifier = Modifier.weight(1.4f).height(48.dp), shape = RoundedCornerShape(100.dp)) {
                        Text(if (isThai) "ส่งข้อมูลยืนยันวัคซีน" else "Send Verification", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Send, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
