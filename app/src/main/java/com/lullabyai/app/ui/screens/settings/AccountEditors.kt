@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.ThaiPhone
import com.lullabyai.app.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** The four Personal Info rows; each one is edited alone in [EditAccountFieldDialog]. */
enum class AccountField { USERNAME, FULL_NAME, EMAIL, PHONE }

private const val MAX_NAME = 50
private const val AVATAR_PX = 512
private const val SAMPLE_DIR = "avatars/user"

/**
 * Edits one Personal Info value in place (no separate screen). Validates, saves, and keeps the
 * dialog open with the reason when saving fails. Email only starts a change: Supabase sends a
 * link to the new address and the account switches after it is opened.
 */
@Composable
fun EditAccountFieldDialog(
    field: AccountField,
    current: String?,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onSaved: (message: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }
    var text by remember { mutableStateOf(current.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val title = when (field) {
        AccountField.USERNAME -> if (isThai) "แก้ไขชื่อผู้ใช้" else "Edit username"
        AccountField.FULL_NAME -> if (isThai) "แก้ไขชื่อ-นามสกุล" else "Edit full name"
        AccountField.EMAIL -> if (isThai) "เปลี่ยนอีเมลที่ลงทะเบียน" else "Change registered email"
        AccountField.PHONE -> if (isThai) "แก้ไขเบอร์โทรศัพท์" else "Edit phone number"
    }

    fun save() {
        val value = text.trim()
        error = null
        // Validate first; nothing leaves the phone until the value is acceptable.
        val problem = when (field) {
            AccountField.USERNAME -> when {
                value.isEmpty() -> if (isThai) "กรุณากรอกชื่อผู้ใช้" else "Enter a username"
                value.length > MAX_NAME -> if (isThai) "ยาวได้ไม่เกิน $MAX_NAME ตัวอักษร" else "At most $MAX_NAME characters"
                else -> null
            }
            AccountField.FULL_NAME -> when {
                value.isEmpty() -> if (isThai) "กรุณากรอกชื่อ-นามสกุล" else "Enter your full name"
                value.length > MAX_NAME -> if (isThai) "ยาวได้ไม่เกิน $MAX_NAME ตัวอักษร" else "At most $MAX_NAME characters"
                else -> null
            }
            AccountField.EMAIL -> when {
                !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches() -> if (isThai) "รูปแบบอีเมลไม่ถูกต้อง" else "Not a valid email"
                value.equals(current, ignoreCase = true) -> if (isThai) "เป็นอีเมลเดิมอยู่แล้ว" else "That is already your email"
                else -> null
            }
            AccountField.PHONE -> if (value.isNotEmpty() && ThaiPhone.normalize(value) == null) {
                if (isThai) "เบอร์ไม่ถูกต้อง เช่น 0812345678" else "Not a valid Thai number, e.g. 0812345678"
            } else null
        }
        if (problem != null) {
            error = problem
            return
        }
        if (field != AccountField.EMAIL && value == current.orEmpty()) {
            onDismiss()
            return
        }

        saving = true
        scope.launch {
            val result = when (field) {
                AccountField.USERNAME -> repo.updateProfileField("username", value).map { }
                AccountField.FULL_NAME -> repo.updateProfileField("full_name", value).map { }
                AccountField.PHONE -> repo.updateProfileField("phone", value.ifEmpty { null }?.let { ThaiPhone.normalize(it) }).map { }
                AccountField.EMAIL -> repo.requestEmailChange(value)
            }
            saving = false
            result.onSuccess {
                onSaved(
                    if (field == AccountField.EMAIL) {
                        if (isThai) "ส่งลิงก์ยืนยันไปที่ $value แล้ว อีเมลจะเปลี่ยนหลังกดลิงก์" else "Confirmation link sent to $value. The email changes after you open it."
                    } else {
                        if (isThai) "บันทึกแล้ว" else "Saved"
                    }
                )
            }.onFailure {
                val raw = it.message ?: it::class.simpleName.orEmpty()
                error = when {
                    raw == "username_taken" -> if (isThai) "ชื่อผู้ใช้นี้มีคนใช้แล้ว ลองชื่ออื่น" else "That username is taken"
                    raw.contains("already been registered", ignoreCase = true) ->
                        if (isThai) "อีเมลนี้ใช้กับบัญชีอื่นอยู่แล้ว" else "That email belongs to another account"
                    else -> (if (isThai) "บันทึกไม่สำเร็จ: " else "Could not save: ") + raw
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    enabled = !saving,
                    isError = error != null,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field) {
                            AccountField.EMAIL -> KeyboardType.Email
                            AccountField.PHONE -> KeyboardType.Phone
                            else -> KeyboardType.Text
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                when (field) {
                    AccountField.EMAIL -> Text(
                        if (isThai) "ระบบจะส่งลิงก์ยืนยันไปที่อีเมลใหม่ ใช้อีเมลเดิมเข้าสู่ระบบได้จนกว่าจะกดยืนยัน"
                        else "A confirmation link goes to the new address. Keep signing in with the old one until you open it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AccountField.PHONE -> Text(
                        if (isThai) "เว้นว่างเพื่อลบเบอร์" else "Leave empty to remove it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Unit
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = !saving) {
                Text(if (field == AccountField.EMAIL) (if (isThai) "ส่งลิงก์ยืนยัน" else "Send link") else (if (isThai) "บันทึก" else "Save"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
    )
}

/**
 * Profile picture: a photo from the phone, or one of the bundled sample pictures
 * (assets/avatars/user). Both are shrunk to a 512px JPEG and go through the same private-bucket
 * upload, so every place that shows the avatar (app, web, doctor portal) needs no change.
 */
@Composable
fun AvatarPickerSheet(isThai: Boolean, onDismiss: () -> Unit, onDone: (message: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val samples = remember { runCatching { context.assets.list(SAMPLE_DIR)?.sorted().orEmpty() }.getOrDefault(emptyList()) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun upload(read: () -> Bitmap?) {
        uploading = true
        error = null
        scope.launch {
            val result = runCatching {
                val jpeg = withContext(Dispatchers.IO) {
                    val bitmap = read() ?: error("image_unreadable")
                    ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                        out.toByteArray()
                    }
                }
                AuthRepository().uploadAvatar(jpeg).getOrThrow()
            }
            uploading = false
            result.onSuccess { onDone(if (isThai) "เปลี่ยนรูปโปรไฟล์แล้ว" else "Profile picture updated") }
                .onFailure { error = (if (isThai) "เปลี่ยนรูปไม่สำเร็จ: " else "Could not change picture: ") + (it.message ?: it::class.simpleName) }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) upload { decodeScaled(context, uri) }
    }

    ModalBottomSheet(onDismissRequest = { if (!uploading) onDismiss() }) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (isThai) "เปลี่ยนรูปโปรไฟล์" else "Change profile picture", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !uploading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "เลือกรูปจากเครื่อง" else "Choose from phone")
            }
            Text(if (isThai) "หรือเลือกรูปตัวอย่าง" else "Or pick a sample", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(64.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(samples) { name ->
                    coil.compose.AsyncImage(
                        model = "file:///android_asset/$SAMPLE_DIR/$name",
                        contentDescription = if (isThai) "รูปตัวอย่าง" else "Sample picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .border(1.dp, Primary.copy(alpha = 0.15f), CircleShape)
                            .clickable(enabled = !uploading) {
                                upload { context.assets.open("$SAMPLE_DIR/$name").use { BitmapFactory.decodeStream(it) } }
                            }
                    )
                }
            }
        }
    }
}

/** Decodes a picked photo at no more than [AVATAR_PX] on its long side, upright (ImageDecoder applies EXIF rotation). */
private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height)
            if (longSide > AVATAR_PX) {
                val scale = AVATAR_PX.toFloat() / longSide
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    // ponytail: API 26-27 ignores EXIF rotation; a sideways photo there needs ExifInterface if it ever matters.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_PX) sample *= 2
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
