@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.auth

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel

@Composable
fun ForceChangePasswordScreen(
    authViewModel: AuthViewModel,
    onDone: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val error = localError ?: uiState.error

    Scaffold(
        topBar = {
            TopAppBar(
                // No back button — the user must change the password to continue
                title = { Text("เปลี่ยนรหัสผ่าน", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
            Spacer(Modifier.height(16.dp))

            // Security notice banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.12f))
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("เพื่อความปลอดภัยของคุณ", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text("กรุณาเปลี่ยนรหัสผ่านใหม่เพื่อใช้งานต่อ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Text("(For your security, please update your password)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(24.dp))
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
                    PasswordField("รหัสผ่านใหม่ (New Password)", newPass) { newPass = it }
                    PasswordField("ยืนยันรหัสผ่านใหม่ (Confirm Password)", confirm) { confirm = it }

                    if (error != null) {
                        Text(error, color = Error, style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = {
                            localError = when {
                                newPass.isBlank() || confirm.isBlank() -> "กรุณากรอกข้อมูลให้ครบทุกช่อง"
                                newPass.length < 6 -> "รหัสผ่านใหม่ต้องมีอย่างน้อย 6 ตัวอักษร"
                                newPass != confirm -> "รหัสผ่านใหม่และการยืนยันไม่ตรงกัน"
                                else -> null
                            }
                            if (localError == null) {
                                authViewModel.clearError()
                                authViewModel.completeForcedChange(newPass, onDone)
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
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
