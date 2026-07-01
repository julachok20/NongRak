@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel

@Composable
fun ChangePasswordScreen(
    authViewModel: AuthViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("เปลี่ยนรหัสผ่าน", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "กลับ", tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.SETTINGS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Primary.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Lock, null, tint = Primary, modifier = Modifier.size(36.dp)) }

            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PasswordField("รหัสผ่านใหม่ (New Password)", newPassword) { newPassword = it; localError = null; success = false }
                    PasswordField("ยืนยันรหัสผ่านใหม่ (Confirm Password)", confirmPassword) { confirmPassword = it; localError = null; success = false }

                    if (localError != null) Text(localError!!, color = Error, style = MaterialTheme.typography.labelMedium)
                    uiState.error?.let { Text(it, color = Error, style = MaterialTheme.typography.labelMedium) }
                    if (success) Text("เปลี่ยนรหัสผ่านเรียบร้อยแล้ว", color = HealthGood, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Button(
                        onClick = {
                            localError = when {
                                newPassword.isBlank() || confirmPassword.isBlank() -> "กรุณากรอกข้อมูลให้ครบทุกช่อง"
                                newPassword.length < 8 -> "รหัสผ่านใหม่ต้องมีอย่างน้อย 8 ตัวอักษร"
                                newPassword != confirmPassword -> "รหัสผ่านใหม่และการยืนยันไม่ตรงกัน"
                                else -> null
                            }
                            if (localError == null) {
                                authViewModel.clearError()
                                authViewModel.changePassword(newPassword) { success = true }
                            }
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("บันทึกการเปลี่ยนแปลง", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null, tint = Outline)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}
