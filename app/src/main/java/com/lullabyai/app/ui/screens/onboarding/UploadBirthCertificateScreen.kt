package com.lullabyai.app.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.repository.BirthCertificateAiService
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.ImageUtils
import kotlinx.coroutines.launch

/**
 * Third "add a child" entry point: pick a photo of a birth certificate, run it through the
 * vision-AI endpoint to read the child's name / birthdate / gender, then hand those off to the
 * caller — which pre-fills [com.lullabyai.app.viewmodel.OnboardingViewModel] and sends the parent
 * into the normal Step 1 form to review/correct before anything is saved. The image itself is
 * only held in memory for the AI call and is never uploaded or stored.
 */
@Composable
fun UploadBirthCertificateScreen(
    onExtracted: (fullName: String, nickname: String, birthdate: String, gender: String) -> Unit,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun analyze(uri: android.net.Uri) {
        errorMessage = null
        isLoading = true
        scope.launch {
            val bytes = ImageUtils.downscaleToJpeg(context, uri)
            if (bytes == null) {
                isLoading = false
                errorMessage = if (isThai) "ไม่สามารถอ่านรูปภาพนี้ได้ ลองใหม่อีกครั้ง" else "Couldn't read that image. Please try again."
                return@launch
            }
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val result = runCatching { BirthCertificateAiService.extractChildInfo(base64, "image/jpeg") }.getOrNull()
            isLoading = false
            if (result == null) {
                errorMessage = if (isThai) "อ่านข้อมูลจากสูติบัตรไม่ได้ ลองถ่ายรูปให้ชัดขึ้นหรือกรอกข้อมูลเอง"
                else "Couldn't read the certificate. Try a clearer photo, or enter the details manually."
            } else {
                onExtracted(result.fullName, result.nickname, result.birthdateIso ?: "", result.gender)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) analyze(uri)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) }
                Text(
                    if (isThai) "อัปโหลดสูติบัตร" else "Birth Certificate",
                    style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            // Dropzone card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .border(1.dp, Outline.copy(0.4f), RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f), RoundedCornerShape(16.dp))
                        .clickable(enabled = !isLoading) {
                            imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(vertical = 40.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (isThai) "กำลังอ่านข้อมูลจากสูติบัตร..." else "Reading the certificate...",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CloudUpload, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (isThai) "แตะเพื่ออัปโหลดสูติบัตร" else "Tap to upload birth certificate",
                            style = MaterialTheme.typography.bodyLarge, color = Primary, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isThai) "รองรับไฟล์ JPG และ PNG" else "JPG and PNG supported",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            if (isThai) "ข้อมูลสำคัญ" else "Important information",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (isThai) "ระบบจะอ่านชื่อ วันเกิด และเพศจากสูติบัตร เพื่อกรอกข้อมูลลูกให้อัตโนมัติ กรุณาตรวจสอบความถูกต้องก่อนบันทึกในขั้นตอนถัดไป"
                        else "We'll read the child's name, birthdate, and sex from the certificate to auto-fill the next form. Please review everything for accuracy before saving.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Shield, null, tint = HealthGood, modifier = Modifier.size(18.dp))
                        Text(
                            if (isThai) "รูปสูติบัตรจะไม่ถูกเก็บถาวร ใช้เพื่ออ่านข้อมูลเท่านั้น" else "The photo is never stored — it's only used to read the details.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
