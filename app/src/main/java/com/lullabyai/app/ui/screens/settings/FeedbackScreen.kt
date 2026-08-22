@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.repository.FeedbackRepository
import com.lullabyai.app.data.repository.FeedbackSubmission
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@Composable
fun FeedbackScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    lang: String,
    onBack: () -> Unit
) {
    val isThai = lang.startsWith("th")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by authViewModel.uiState.collectAsState()
    val repo = remember { FeedbackRepository() }
    val authRepo = remember { com.lullabyai.app.data.repository.AuthRepository() }

    var rating by remember { mutableStateOf(5) }
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(authRepo.currentUser?.email ?: "") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var lastSubmission by remember { mutableStateOf<FeedbackSubmission?>(null) }

    val categories = remember(isThai) {
        if (isThai) {
            listOf(
                "⭐ รีวิวและความประทับใจ",
                "💡 เสนอแนะฟีเจอร์ใหม่",
                "🐞 รายงานปัญหา / บั๊ก",
                "👶 คำแนะนำ AI & การใช้งาน",
                "💬 ข้อเสนอแนะอื่นๆ"
            )
        } else {
            listOf(
                "⭐ Review & Compliment",
                "💡 Feature Suggestion",
                "🐞 Bug / Technical Issue",
                "👶 AI Guidance & Care",
                "💬 Other Feedback"
            )
        }
    }

    val ratingLabels = remember(isThai) {
        if (isThai) {
            listOf(
                "",
                "⭐ ต้องปรับปรุง (Needs Improvement)",
                "⭐⭐ พอใช้ (Fair)",
                "⭐⭐⭐ ปานกลาง (Good)",
                "⭐⭐⭐⭐ ดีมาก (Very Good)",
                "⭐⭐⭐⭐⭐ ยอดเยี่ยม ประทับใจมาก! (Excellent!)"
            )
        } else {
            listOf(
                "",
                "⭐ Needs Improvement",
                "⭐⭐ Fair",
                "⭐⭐⭐ Good",
                "⭐⭐⭐⭐ Very Good",
                "⭐⭐⭐⭐⭐ Excellent!"
            )
        }
    }

    fun submit() {
        if (message.trim().isBlank()) {
            Toast.makeText(
                context,
                if (isThai) "กรุณากรอกข้อความความคิดเห็นหรือรีวิวของคุณ" else "Please write your review or comments",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        isSubmitting = true
        val submission = FeedbackSubmission(
            rating = rating,
            category = categories.getOrElse(selectedCategoryIndex) { "Feedback" },
            message = message.trim(),
            userEmail = email.trim().ifBlank { null },
            userName = authState.profile?.fullName ?: authState.profile?.username
        )
        lastSubmission = submission

        scope.launch {
            repo.submitFeedback(submission)
            isSubmitting = false
            showSuccessDialog = true
        }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "ข้อเสนอแนะและรีวิว" else "Feedback & Review",
                onBack = onBack,
                navController = navController
            )
        },
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Primary.copy(0.12f), SecondaryContainer.copy(0.2f))))
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.RateReview, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (isThai) "เราพร้อมรับฟังทุกความคิดเห็น" else "We Value Your Feedback",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (isThai) "ร่วมบอกเล่าประสบการณ์ รีวิว หรือแนะนำฟีเจอร์ เพื่อช่วยพัฒนา NongRak AI ให้ดียิ่งขึ้นค่ะ"
                                else "Share your reviews, bug reports, or feature ideas to help us improve NongRak AI.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Star Rating Section
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (isThai) "ให้คะแนนความพึงพอใจต่อแอปพลิเคชัน" else "How would you rate NongRak AI?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            val selected = i <= rating
                            IconButton(
                                onClick = { rating = i },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "$i Stars",
                                    tint = if (selected) Color(0xFFFFB300) else Outline.copy(alpha = 0.5f),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }

                    Text(
                        ratingLabels.getOrElse(rating) { "" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rating >= 4) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Category Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isThai) "ประเภทข้อเสนอแนะ" else "Feedback Category",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEachIndexed { index, cat ->
                        val isSelected = selectedCategoryIndex == index
                        Surface(
                            onClick = { selectedCategoryIndex = index },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Primary.copy(0.1f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Primary else MaterialTheme.colorScheme.outlineVariant.copy(0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    cat,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Review / Feedback Text Area
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isThai) "รายละเอียดข้อเสนอแนะหรือรีวิว *" else "Your Review & Comments *",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${message.length}/1000",
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline
                    )
                }

                val placeholderText = if (isThai) {
                    when (selectedCategoryIndex) {
                        0 -> "บอกเล่าความรู้สึก ความประทับใจ หรือสิ่งที่ชอบใน NongRak AI..."
                        1 -> "มีฟีเจอร์หรือเมนูอะไรที่อยากให้เพิ่มเข้ามาในแอปบ้างคะ..."
                        2 -> "พบปัญหาการใช้งานตรงไหน มีอาการอย่างไร บอกเราได้เลยค่ะ..."
                        else -> "พิมพ์ความคิดเห็นหรือคำแนะนำของคุณที่นี่..."
                    }
                } else {
                    when (selectedCategoryIndex) {
                        0 -> "Tell us what you like most about NongRak AI..."
                        1 -> "What new features would you like to see?..."
                        2 -> "Describe the issue or bug you encountered..."
                        else -> "Type your feedback or comments here..."
                    }
                }

                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 1000) message = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                    placeholder = { Text(placeholderText, color = Outline, style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            // User Email / Contact (Optional)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isThai) "อีเมลของคุณ (เพื่อให้ทีมงานตอบกลับ)" else "Your Contact Email (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("example@email.com", color = Outline) },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = Outline) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            // Direct Developer Email Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Mail, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                    Column {
                        Text(
                            if (isThai) "อีเมลผู้พัฒนาโดยตรง:" else "Direct Developer Email:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            FeedbackRepository.DEVELOPER_EMAIL,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                }
            }

            // Submit Button
            Button(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(100.dp),
                enabled = !isSubmitting && message.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (isThai) "ส่งข้อเสนอแนะและรีวิว" else "Submit Feedback & Review",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Success & Email Notification Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(36.dp))
                }
            },
            title = {
                Text(
                    if (isThai) "ขอบคุณสำหรับข้อเสนอแนะค่ะ!" else "Thank You for Your Feedback!",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isThai)
                            "ความคิดเห็นและรีวิวของคุณถูกบันทึกเรียบร้อยแล้ว และระบบพร้อมส่งข้อมูลแจ้งเตือนตรงถึงทีมงาน (${FeedbackRepository.DEVELOPER_EMAIL}) ค่ะ"
                        else
                            "Your review and feedback have been saved and can be directly notified to our development team (${FeedbackRepository.DEVELOPER_EMAIL}).",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (isThai) "คะแนนที่ให้: ${"★".repeat(rating)} (${rating}/5)" else "Rating: ${"★".repeat(rating)} (${rating}/5)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            Text(
                                if (isThai) "หัวข้อ: ${categories.getOrElse(selectedCategoryIndex) { "" }}" else "Category: ${categories.getOrElse(selectedCategoryIndex) { "" }}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sub = lastSubmission
                        if (sub != null) {
                            runCatching {
                                val intent = repo.createEmailIntent(sub, isThai)
                                context.startActivity(Intent.createChooser(intent, if (isThai) "ส่งอีเมลแจ้งเตือนถึงผู้พัฒนา" else "Send Email Notification"))
                            }
                        }
                        showSuccessDialog = false
                        message = ""
                        onBack()
                    },
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "ส่งอีเมลแจ้งเตือนทันที" else "Send Email to Developer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        message = ""
                        onBack()
                    }
                ) {
                    Text(if (isThai) "เสร็จสิ้น" else "Done", color = Outline)
                }
            }
        )
    }
}
