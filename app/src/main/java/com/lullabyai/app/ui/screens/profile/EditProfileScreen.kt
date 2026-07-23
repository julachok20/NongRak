package com.lullabyai.app.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel

@Composable
fun EditProfileScreen(
    authViewModel: AuthViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var fullName by remember { mutableStateOf(uiState.profile?.fullName ?: "") }
    var username by remember { mutableStateOf(uiState.profile?.username ?: "") }

    LaunchedEffect(Unit) { authViewModel.loadProfile() }

    // Image picker → read bytes → upload
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val bytes = runCatching { context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull()
            if (bytes != null) authViewModel.uploadAvatar(bytes)
        }
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Scaffold(
        topBar = { LullabyTopBar(title = "แก้ไขโปรไฟล์", onBack = onBack) },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.SETTINGS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Avatar
            val avatarUrl = uiState.avatarSignedUrl
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(Primary.copy(0.12f)).clickable { pickImage() },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !avatarUrl.isNullOrBlank() -> AsyncImage(
                            model = avatarUrl, contentDescription = "รูปโปรไฟล์",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                        uiState.isLoading -> CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        else -> Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(52.dp))
                    }
                }
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Primary).clickable { pickImage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            TextButton(onClick = { pickImage() }, contentPadding = PaddingValues(0.dp)) {
                Text("เปลี่ยนรูปโปรไฟล์", style = MaterialTheme.typography.bodySmall, color = Primary)
            }

            // Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ชื่อ-นามสกุล", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = fullName, onValueChange = { fullName = it },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Filled.Person, null, tint = Outline) },
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ชื่อผู้ใช้ (Username)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = username, onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Filled.AlternateEmail, null, tint = Outline) },
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("อีเมล", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = "", onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Filled.Email, null, tint = Outline) },
                            enabled = false, singleLine = true, shape = RoundedCornerShape(12.dp),
                            placeholder = { Text("ไม่สามารถเปลี่ยนอีเมลได้", color = Outline) },
                            colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            }

            uiState.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            PrimaryButton(
                text = "บันทึกการเปลี่ยนแปลง",
                onClick = {
                    authViewModel.updateProfile(username.ifBlank { null }, fullName.ifBlank { null }, null) { onBack() }
                },
                isLoading = uiState.isLoading
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
