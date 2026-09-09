package com.lullabyai.app.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import io.github.jan.supabase.auth.auth

@Composable
fun EditProfileScreen(
    authViewModel: AuthViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val uiState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var shownName by remember {
        mutableStateOf(uiState.profile?.username ?: uiState.profile?.fullName ?: "")
    }
    var relationship by remember {
        mutableStateOf(uiState.profile?.relationship ?: "แม่")
    }
    var phone by remember {
        mutableStateOf(uiState.profile?.phone ?: "")
    }
    val userEmail = remember {
        supabase.auth.currentUserOrNull()?.email ?: ""
    }

    LaunchedEffect(Unit) {
        authViewModel.loadProfile()
    }

    LaunchedEffect(uiState.profile) {
        uiState.profile?.let { p ->
            if (shownName.isBlank()) shownName = p.username ?: p.fullName ?: ""
            if (relationship.isBlank()) relationship = p.relationship ?: "แม่"
            if (phone.isBlank()) phone = p.phone ?: ""
        }
    }

    // Image picker → read bytes → upload
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val bytes = runCatching { context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull()
            if (bytes != null) authViewModel.uploadAvatar(bytes)
        }
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "แก้ไขข้อมูลโปรไฟล์" else "Edit Profile",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.SETTINGS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onCommunity = { navController.navigate(Screen.Community.route) { launchSingleTop = true } }
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
            Spacer(Modifier.height(12.dp))

            // Profile Picture / Avatar
            val avatarUrl = uiState.avatarSignedUrl ?: uiState.profile?.avatarUrl
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .background(PrimaryContainer)
                        .clickable { pickImage() },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !avatarUrl.isNullOrBlank() -> AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        uiState.isLoading -> CircularProgressIndicator(
                            color = Primary,
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 2.dp
                        )
                        else -> Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Primary)
                        .clickable { pickImage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Change avatar",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            TextButton(onClick = { pickImage() }, contentPadding = PaddingValues(0.dp)) {
                Text(
                    text = if (s.isThai) "แตะเพื่อเปลี่ยนรูปโปรไฟล์" else "Tap to change profile photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // General Profile Information Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Badge, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (s.isThai) "ข้อมูลชื่อและบทบาทที่แสดง" else "Display Name & Role",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Shown Name / Username
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (s.isThai) "ชื่อผู้ใช้ / ชื่อที่แสดง (Username / Display Name)" else "Username / Display Name",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = shownName,
                            onValueChange = { shownName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(if (s.isThai) "เช่น abcd หรือ คุณแม่สมศรี" else "e.g. abcd or Mom") },
                            leadingIcon = { Icon(Icons.Filled.Person, null, tint = Primary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                        Text(
                            text = if (s.isThai) "ชื่อนี้จะซิงค์แสดงผลในหน้าหลัก, โปรไฟล์ชุมชน, และระบบจัดการสิทธิ์" else "This name syncs across Home, Community, and Access Permissions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    // Relationship / Role
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (s.isThai) "บทบาท / ความสัมพันธ์กับลูก" else "Role / Relationship to Child",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val roleOptions = if (s.isThai) listOf("แม่", "พ่อ", "ผู้ปกครอง") else listOf("Mother", "Father", "Guardian")
                            roleOptions.forEach { opt ->
                                val isSel = relationship.equals(opt, ignoreCase = true) ||
                                    (opt == "แม่" && relationship == "mother") ||
                                    (opt == "พ่อ" && relationship == "father") ||
                                    (opt == "ผู้ปกครอง" && relationship == "guardian")
                                FilterChip(
                                    selected = isSel,
                                    onClick = { relationship = opt },
                                    label = { Text(opt, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Detailed Contact Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.ContactPhone, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (s.isThai) "ข้อมูลการติดต่อโดยละเอียด" else "Detailed Contact Information",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Phone Number
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (s.isThai) "เบอร์โทรศัพท์ติดต่อ" else "Contact Phone Number",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(if (s.isThai) "เช่น 0812345678" else "e.g. 0812345678") },
                            leadingIcon = { Icon(Icons.Filled.Phone, null, tint = Primary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }

                    // Email (Account read-only)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (s.isThai) "อีเมลบัญชีผู้ใช้" else "Account Email",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = userEmail,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Filled.Email, null, tint = Outline) },
                            enabled = false,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            uiState.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            PrimaryButton(
                text = if (s.isThai) "บันทึกข้อมูลโปรไฟล์" else "Save Profile Changes",
                onClick = {
                    authViewModel.updateProfile(
                        username = shownName.trim().ifBlank { null },
                        fullName = shownName.trim().ifBlank { null },
                        avatarUrl = null,
                        relationship = relationship.trim().ifBlank { null },
                        phone = phone.trim().ifBlank { null }
                    ) {
                        onBack()
                    }
                },
                isLoading = uiState.isLoading
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

