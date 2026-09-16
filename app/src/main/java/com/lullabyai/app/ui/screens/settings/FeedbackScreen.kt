@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package com.lullabyai.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.R
import com.lullabyai.app.data.repository.FeedbackRepository
import com.lullabyai.app.data.repository.FeedbackSubmission
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_MESSAGE = 500

private data class FeedbackChip(val key: String, val icon: ImageVector, val th: String, val en: String)

/** Stitch "รีวิวแอปพลิเคชัน": what impressed them (4–5 stars). */
private val PRAISE_CHIPS = listOf(
    FeedbackChip("ai_accurate", Icons.Filled.AutoAwesome, "คำแนะนำ AI แม่นยำ", "Accurate AI advice"),
    FeedbackChip("health_book", Icons.Filled.MenuBook, "สมุดสุขภาพใช้งานง่าย", "Easy health book"),
    FeedbackChip("care_chat", Icons.Filled.ChatBubble, "แชทผู้ดูแลสะดวก", "Handy caregiver chat"),
    FeedbackChip("design", Icons.Filled.Palette, "ดีไซน์สวยสบายตา", "Pleasant design"),
    FeedbackChip("fast_logging", Icons.Filled.Timer, "บันทึกข้อมูลรวดเร็ว", "Quick logging"),
    FeedbackChip("data_safety", Icons.Filled.VerifiedUser, "ความปลอดภัยข้อมูลดีเยี่ยม", "Data safety")
)

/** 1–3 stars: asking an unhappy parent what impressed them reads badly, so ask what to fix. */
private val ISSUE_CHIPS = listOf(
    FeedbackChip("ai_off", Icons.Filled.AutoAwesome, "คำแนะนำ AI ไม่ตรง", "AI advice was off"),
    FeedbackChip("bug", Icons.Filled.BugReport, "แอปค้าง / มีข้อผิดพลาด", "Freezes or errors"),
    FeedbackChip("slow", Icons.Filled.Timer, "ช้า โหลดนาน", "Slow to load"),
    FeedbackChip("hard_to_use", Icons.Filled.TouchApp, "ใช้งานยาก", "Hard to use"),
    FeedbackChip("missing_feature", Icons.Filled.Lightbulb, "ขาดฟีเจอร์ที่ต้องการ", "Missing a feature"),
    FeedbackChip("privacy", Icons.Filled.Lock, "กังวลเรื่องข้อมูลส่วนตัว", "Privacy concern")
)

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
    val repo = remember { FeedbackRepository() }
    val authRepo = remember { com.lullabyai.app.data.repository.AuthRepository() }

    var rating by remember { mutableIntStateOf(5) }
    val picked = remember { mutableStateListOf<String>() }
    var message by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var failedSubmission by remember { mutableStateOf<FeedbackSubmission?>(null) }

    val chips = if (rating >= 4) PRAISE_CHIPS else ISSUE_CHIPS
    val ratingLabel = when (rating) {
        1 -> if (isThai) "ต้องปรับปรุงมาก" else "Needs a lot of work"
        2 -> if (isThai) "ยังไม่ค่อยพอใจ" else "Not satisfied"
        3 -> if (isThai) "พอใช้" else "It's okay"
        4 -> if (isThai) "ดีมาก ชอบเลย" else "Very good"
        else -> if (isThai) "ยอดเยี่ยมมาก! ประทับใจมาก ❤️" else "Excellent! Love it ❤️"
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) imageUri = uri
    }

    fun openEmail(submission: FeedbackSubmission, error: String?) {
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    repo.createEmailIntent(submission, isThai, error),
                    if (isThai) "ติดต่อทีมงานทางอีเมล" else "Contact the team by email"
                )
            )
        }
    }

    fun submit() {
        if (isSubmitting) return
        isSubmitting = true
        errorText = null
        scope.launch {
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "unknown"
            val submission = FeedbackSubmission(
                rating = rating,
                highlights = picked.toList(),
                message = message.trim(),
                contactEmail = authRepo.currentUser?.email,
                appVersion = version
            )
            val uri = imageUri
            val image = uri?.let {
                withContext(Dispatchers.IO) { com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, it, 1280) }
            }
            val result = if (uri != null && image == null) {
                Result.failure(IllegalStateException(if (isThai) "อ่านรูปที่แนบไม่ได้" else "Couldn't read the attached picture"))
            } else {
                repo.submit(submission, image)
            }
            result
                .onSuccess { showSuccess = true }
                .onFailure {
                    // Name what was received, so a report to the team says what actually failed.
                    errorText = it.message?.take(200) ?: it::class.simpleName ?: "unknown_error"
                    failedSubmission = submission
                }
            isSubmitting = false
        }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "รีวิวแอปพลิเคชัน" else "Review the App",
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero: mascot on a soft halo, heart badge, question and subtitle.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                    Box(Modifier.size(150.dp).clip(CircleShape).background(Primary.copy(alpha = 0.10f)))
                    Box(
                        Modifier
                            .size(92.dp)
                            .shadow(10.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.mascot_nongrak),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFD7F5E8),
                        modifier = Modifier.align(Alignment.Center).offset(x = 34.dp, y = 34.dp).size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Favorite, null, tint = Color(0xFF12805C), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Text(
                    if (isThai) "คุณคิดอย่างไรกับ NongRak AI?" else "What do you think of NongRak AI?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isThai) "ความคิดเห็นของคุณช่วยให้เราพัฒนาผู้ช่วยดูแลลูกน้อยให้ดียิ่งขึ้นในทุกๆ วัน"
                    else "Your feedback helps us make a better helper for your little one, every day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Rating card.
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (isThai) "ระดับความพึงพอใจ" else "Your rating",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 1..5) {
                            IconButton(
                                onClick = {
                                    // Crossing 3↔4 swaps the chip set; old picks would mean the wrong thing.
                                    if ((i >= 4) != (rating >= 4)) picked.clear()
                                    rating = i
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (isThai) "$i ดาว" else "$i stars",
                                    tint = if (i <= rating) Primary else Outline.copy(alpha = 0.5f),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }
                    Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(alpha = 0.10f)) {
                        Text(
                            ratingLabel,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary
                        )
                    }
                }
            }

            // What impressed them / what to fix.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (rating >= 4) (if (isThai) "จุดที่คุณประทับใจมากที่สุด?" else "What did you like most?")
                        else (if (isThai) "อยากให้เราปรับปรุงเรื่องไหน?" else "What should we improve?"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isThai) "เลือกได้หลายข้อ" else "Pick any",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { chip ->
                        val selected = chip.key in picked
                        FilterChip(
                            selected = selected,
                            onClick = { if (selected) picked.remove(chip.key) else picked.add(chip.key) },
                            label = { Text(if (isThai) chip.th else chip.en) },
                            leadingIcon = { Icon(chip.icon, null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(100.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.14f),
                                selectedLabelColor = Primary,
                                selectedLeadingIconColor = Primary,
                                iconColor = Primary
                            )
                        )
                    }
                }
            }

            // Comment box.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (isThai) "ความคิดเห็นหรือข้อเสนอแนะเพิ่มเติม" else "Anything else to tell us?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("${message.length}/$MAX_MESSAGE", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(Modifier.padding(14.dp).heightIn(min = 110.dp)) {
                        if (message.isEmpty()) {
                            Text(
                                if (isThai) "เล่าความประทับใจ หรือข้อเสนอแนะที่คุณอยากให้ NongRak พัฒนาเพิ่มเติม..."
                                else "Tell us what you enjoyed, or what NongRak should do better...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Outline
                            )
                        }
                        BasicTextField(
                            value = message,
                            onValueChange = { if (it.length <= MAX_MESSAGE) message = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(Primary),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp)
                        )
                    }
                }
            }

            // Optional picture.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val uri = imageUri
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uri != null) {
                            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Filled.AddAPhoto, null, tint = Primary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isThai) "แนบภาพประกอบ (ถ้ามี)" else "Attach a picture (optional)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (isThai) "ภาพที่จะช่วยให้เราเห็นได้อย่างชัดเจนยิ่งขึ้น" else "A screenshot helps us see what you mean",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uri != null) {
                        IconButton(onClick = { imageUri = null }) {
                            Icon(Icons.Filled.Close, contentDescription = if (isThai) "ลบรูป" else "Remove picture")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isThai) "เลือกรูป" else "Choose", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Error: say what failed, and give a way to reach the team anyway.
            errorText?.let { err ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                if (isThai) "ส่งรีวิวไม่สำเร็จ" else "Couldn't send your review",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            (if (isThai) "ระบบตอบกลับว่า: " else "The server said: ") + err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            if (isThai) "ลองส่งอีกครั้ง หรือส่งอีเมลหาทีมงานโดยตรง รีวิวและข้อผิดพลาดนี้จะแนบไปให้อัตโนมัติ"
                            else "Try again, or email the team directly — your review and this error are included.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { submit() }, shape = RoundedCornerShape(100.dp)) {
                                Text(if (isThai) "ลองอีกครั้ง" else "Try again")
                            }
                            Button(
                                onClick = { failedSubmission?.let { openEmail(it, err) } },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.MailOutline, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isThai) "ติดต่อทีมงาน" else "Contact team")
                            }
                        }
                    }
                }
            }

            // Send.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Brush.verticalGradient(listOf(Primary, Primary.copy(alpha = 0.82f))))
            ) {
                TextButton(
                    onClick = { submit() },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "ส่งรีวิวและให้คะแนน" else "Send review",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Store.
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isThai) "หรือให้คะแนน 5 ดาวโดยตรงบน Store เพื่อสนับสนุนเรา" else "Or rate us 5 stars on the store to support us",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        onClick = {
                            val id = context.packageName
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
                            }.onFailure {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")))
                                }
                            }
                        },
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Shop, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text("Google Play", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Who reads it.
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(6.dp)) {
                        Image(painterResource(R.drawable.mascot_nongrak), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Verified, null, tint = Color(0xFF12805C), modifier = Modifier.size(16.dp))
                            Text(
                                if (isThai) "ทีมพัฒนา NongRak AI" else "The NongRak AI team",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF12805C)
                            )
                        }
                        Text(
                            if (isThai) "ทุกคำติชมถูกส่งตรงถึงทีมพัฒนา เพื่อนำไปปรับปรุงประสบการณ์การดูแลลูกรักของคุณ"
                            else "Every review goes straight to the team, to make caring for your little one easier.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false; onBack() },
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = Primary, modifier = Modifier.size(40.dp)) },
            title = { Text(if (isThai) "ขอบคุณสำหรับรีวิวค่ะ!" else "Thank you for your review!", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isThai) "รีวิวของคุณถูกบันทึกแล้ว ทีมงานจะนำไปพัฒนา NongRak AI ให้ดียิ่งขึ้น"
                    else "Your review was saved. The team will use it to make NongRak AI better."
                )
            },
            confirmButton = {
                Button(onClick = { showSuccess = false; onBack() }, shape = RoundedCornerShape(100.dp)) {
                    Text(if (isThai) "เสร็จสิ้น" else "Done")
                }
            }
        )
    }
}
