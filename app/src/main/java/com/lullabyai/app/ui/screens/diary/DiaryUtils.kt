package com.lullabyai.app.ui.screens.diary

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.ui.theme.OnSurfaceVariant
import com.lullabyai.app.ui.theme.OutlineVariant
import com.lullabyai.app.ui.theme.Primary

fun formatMemoryDate(iso: String?, isThai: Boolean): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val p = iso.take(10).split("-")
        val day = p[2].toInt()
        val month = p[1].toInt() - 1
        val year = p[0].toInt()
        val th = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
        val en = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        if (isThai) "$day ${th[month]} ${year + 543}" else "$day ${en[month]} $year"
    } catch (_: Exception) {
        iso
    }
}

fun milestoneIcon(tag: String?): ImageVector = when (tag) {
    "first_step" -> Icons.Filled.DirectionsWalk
    "first_word" -> Icons.Filled.RecordVoiceOver
    "first_smile" -> Icons.Filled.SentimentSatisfied
    "first_tooth" -> Icons.Filled.Star
    else -> Icons.Filled.AutoAwesome
}

/**
 * Creates attribution & license watermark text for NongRak AI exports.
 */
fun createNongRakShareText(
    title: String?,
    description: String?,
    date: String?,
    isThai: Boolean
): String {
    val datePart = if (!date.isNullOrBlank()) " • ${formatMemoryDate(date, isThai)}" else ""
    val titlePart = if (!title.isNullOrBlank()) {
        if (isThai) "👶 ความทรงจำลูกน้อย: $title$datePart" else "👶 Baby Memory: $title$datePart"
    } else {
        if (isThai) "👶 ความทรงจำลูกน้อย$datePart" else "👶 Precious Baby Moment$datePart"
    }
    val descPart = if (!description.isNullOrBlank()) "\n$description\n" else "\n"

    return if (isThai) {
        """
$titlePart
$descPart
━━━━━━━━━━━━━━━━━━━━━━
✨ สร้างและบันทึกความทรงจำโดย น้องรัก (NongRak AI) — ผู้ช่วย AI ดูแลลูกน้อยวัย 0-3 ปี
🌐 เว็บไซต์: https://nongrak-ai.vercel.app/
🍼 Powered by NongRak AI • #NongRakAI #น้องรัก #BabyMemories #ChildMilestone #SmartParenting
        """.trimIndent()
    } else {
        """
$titlePart
$descPart
━━━━━━━━━━━━━━━━━━━━━━
✨ Created & Preserved by NongRak AI — Smart Baby Care Assistant
🌐 Official Website: https://nongrak-ai.vercel.app/
🍼 Powered by NongRak AI • #NongRakAI #NongRak #BabyMemories #ChildMilestone #Parenting
        """.trimIndent()
    }

}

/**
 * Exports memory to TikTok, Instagram, YouTube Shorts, or General Share with NongRak AI License burned into the media.
 */
fun exportMemoryToSocial(
    context: Context,
    platform: String, // "tiktok", "instagram", "instagram_story", "facebook", "youtube_shorts", "general"
    title: String?,
    description: String?,
    date: String?,
    mediaUri: Uri? = null,
    mimeType: String? = null,
    isThai: Boolean = true,
    // Studio choices. Null means "leave the source as it is" — these are only honoured for video,
    // because a still already carries whatever the editor composited into it.
    aspectRatio: Float? = null,
    mascotResId: Int? = null,
    mascotPosition: MascotPosition? = null
) {
    val pm = context.packageManager
    val isTikTok = platform.lowercase() == "tiktok"
    val isInstagram = platform.lowercase().startsWith("instagram")
    val isFacebook = platform.lowercase() == "facebook"
    val isYoutube = platform.lowercase().contains("youtube")

    val platformPackage = when {
        isTikTok -> {
            val mainPkg = "com.zhiliaoapp.musically"
            val seaPkg = "com.ss.android.ugc.trill"
            val hasMain = try { pm.getPackageInfo(mainPkg, 0); true } catch (_: Exception) { false }
            val hasSea = try { pm.getPackageInfo(seaPkg, 0); true } catch (_: Exception) { false }
            if (hasMain) mainPkg else if (hasSea) seaPkg else mainPkg
        }
        isInstagram -> "com.instagram.android"
        isFacebook -> "com.facebook.katana"
        isYoutube -> "com.google.android.youtube"
        else -> null
    }

    val platformName = when {
        isTikTok -> "TikTok"
        isInstagram -> "Instagram"
        isFacebook -> "Facebook"
        isYoutube -> "YouTube Shorts"
        else -> if (isThai) "แชร์" else "Share"
    }

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        var finalMediaUri: Uri? = mediaUri
        var finalMime = mimeType

        if (mediaUri != null && mimeType != null) {
            try {
                if (mimeType.startsWith("image/")) {
                    finalMediaUri = NongRakMediaWatermarkEngine.burnWatermarkToImage(
                        context = context,
                        imageUri = mediaUri,
                        title = title,
                        dateText = formatMemoryDate(date, isThai),
                        isThai = isThai
                    )
                    finalMime = "image/jpeg"
                } else if (mimeType.startsWith("video/")) {
                    Toast.makeText(
                        context,
                        if (isThai) "กำลังเตรียมวิดีโอ..." else "Preparing video...",
                        Toast.LENGTH_SHORT
                    ).show()
                    finalMediaUri = NongRakMediaWatermarkEngine.prepareWatermarkedVideoForExport(
                        context = context,
                        videoUri = mediaUri,
                        title = title,
                        dateText = formatMemoryDate(date, isThai),
                        isThai = isThai,
                        aspectRatio = aspectRatio,
                        mascotResId = mascotResId,
                        mascotPosition = mascotPosition,
                        // Video is branded when it is MADE, in the studio - every clip now passes
                        // through the editor on the way in. Stamping again here laid a second,
                        // differently-designed badge over the first. Stills keep their export-time
                        // badge, because a photo never goes through the studio.
                        watermark = false
                    )
                    finalMime = "video/mp4"
                }
            } catch (e: Exception) {
                android.util.Log.e("DiaryUtils", "Watermarking fallback: ${e.message}")
            }
        }

        val shareText = createNongRakShareText(title, description, date, isThai)

        // Copy caption to clipboard so user can immediately paste it into TikTok / Instagram Story / Post
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("NongRak AI Caption", shareText)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(
                context,
                if (isThai) "คัดลอกแคปชั่นแล้ว พร้อมวางใน $platformName ได้ทันที 📋"
                else "Caption copied! Ready to paste in $platformName 📋",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {}

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (finalMediaUri != null && finalMime != null) finalMime else "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            finalMediaUri?.let {
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        try {
            if (platformPackage != null) {
                val targetedIntent = Intent(sendIntent).apply {
                    setPackage(platformPackage)
                }
                context.startActivity(targetedIntent)
            } else {
                val chooser = Intent.createChooser(sendIntent, if (isThai) "แชร์ไปยัง $platformName" else "Share to $platformName")
                context.startActivity(chooser)
            }
        } catch (_: Exception) {
            try {
                val chooser = Intent.createChooser(sendIntent, if (isThai) "แชร์ไปยัง $platformName" else "Share to $platformName")
                context.startActivity(chooser)
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    if (isThai) "ไม่สามารถเปิดแอป $platformName สำหรับแชร์ได้" else "Unable to open $platformName",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}


/**
 * Reusable Social Export Section with TikTok, Instagram, and YouTube Shorts buttons.
 */
@Composable
fun SocialExportSection(
    title: String?,
    description: String?,
    date: String?,
    mediaUri: Uri? = null,
    mimeType: String? = null,
    isThai: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isThai) "ส่งออกไปยังโซเชียล (Export to Social)" else "Export to Social",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Text(
                text = if (isThai) "พร้อมลายน้ำ NongRak AI" else "With NongRak License",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SocialExportButton(
                icon = Icons.Filled.PlayCircle,
                label = "TikTok",
                accentColor = Color(0xFF000000),
                modifier = Modifier.weight(1f)
            ) {
                exportMemoryToSocial(context, "tiktok", title, description, date, mediaUri, mimeType, isThai)
            }

            SocialExportButton(
                icon = Icons.Filled.CameraAlt,
                label = "Instagram",
                accentColor = Color(0xFFE1306C),
                modifier = Modifier.weight(1f)
            ) {
                exportMemoryToSocial(context, "instagram", title, description, date, mediaUri, mimeType, isThai)
            }

            SocialExportButton(
                icon = Icons.Filled.SmartDisplay,
                label = "YouTube Shorts",
                accentColor = Color(0xFFFF0000),
                modifier = Modifier.weight(1f)
            ) {
                exportMemoryToSocial(context, "youtube_shorts", title, description, date, mediaUri, mimeType, isThai)
            }
        }
    }
}

@Composable
private fun SocialExportButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineVariant.copy(0.7f)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
            }
            Text(
                label,
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
