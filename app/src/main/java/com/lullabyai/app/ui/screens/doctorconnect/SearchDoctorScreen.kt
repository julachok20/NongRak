@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.SearchDoctorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "ค้นหาคุณหมอ" — browse/search the doctor directory (DoctorRepository.searchDoctors). Tapping a
 *  result opens registration or reservation depending on the Pedia clinic link for the active child. */
@Composable
fun SearchDoctorScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val vm: SearchDoctorViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val childState by childViewModel.uiState.collectAsState()
    val activeChildId = ChildSelection.current() ?: childState.child?.id
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(20_000)
            vm.refresh()
        }
    }

    fun openClinicFlow(doctorId: String) {
        scope.launch {
            val childId = activeChildId
            if (childId.isNullOrBlank()) {
                snackbarHostState.showSnackbar(clinicGateMissingChildMessage(s.isThai))
                return@launch
            }
            val gate = withContext(Dispatchers.IO) { resolveClinicBookingGate(doctorId, childId) }
            clinicGateSnackbarMessage(gate, s.isThai)?.let { snackbarHostState.showSnackbar(it) }
            navController.navigateForClinicGate(doctorId, gate)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ค้นหาคุณหมอ" else "Search Doctor",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.query, onValueChange = { vm.search(it) }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (s.isThai) "ค้นหาชื่อแพทย์ หรือความเชี่ยวชาญ..." else "Search doctor name or specialty...", color = Outline) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                singleLine = true, shape = RoundedCornerShape(100.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.lullabyai.app.ui.theme.Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (s.isThai) "พบคุณหมอ ${ui.results.size} ท่าน" else "${ui.results.size} doctors found",
                style = MaterialTheme.typography.labelMedium, color = Outline
            )
            Spacer(Modifier.height(8.dp))

            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                ui.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            if (s.isThai) "ไม่พบคุณหมอที่พร้อมรับจอง" else "No bookable doctors found",
                            color = Outline,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (s.isThai)
                                "คุณหมอต้องตั้งเวลทำงาน (จัดการเวลาทำงาน) และช่วงเวลานัดหมายที่เปิดรับ (สถานะเปิด) ในอนาคต จากนั้นกลับมาค้นหาใหม่"
                            else
                                "Doctors need working hours plus open future appointment slots before they appear here.",
                            color = Outline,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(ui.results, key = { it.first.id }) { (profile, doctorProfile) ->
                        DoctorCard(
                            profile = profile, doctorProfile = doctorProfile, isThai = s.isThai,
                            actionLabel = if (s.isThai) "จองคิว" else "Book",
                            onAction = { openClinicFlow(profile.id) },
                            onClick = { openClinicFlow(profile.id) }
                        )
                    }
                }
            }
        }
    }
}
