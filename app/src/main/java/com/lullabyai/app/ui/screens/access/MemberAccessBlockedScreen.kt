@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Background
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.ui.theme.OnBackground
import com.lullabyai.app.ui.theme.OnSurfaceVariant
import com.lullabyai.app.ui.theme.Primary

data class AccessDeniedInfo(
    val actionTitleTh: String,
    val actionTitleEn: String,
    val memberName: String,
    val memberRoleLabel: String,
    val accessSummaryTh: String,
    val accessSummaryEn: String,
    val ownerName: String,
    val requested: Boolean = false
)

/**
 * Stitch: "จำกัดสิทธิ์การเข้าถึง (Access Denied)".
 * Header is the standard [LullabyTopBar]; the body copies the lock / context / request flow.
 */
@Composable
fun MemberAccessBlockedScreen(
    navController: NavController,
    onBack: () -> Unit,
    title: String? = null,
    info: AccessDeniedInfo? = null,
    onRequestAccess: (() -> Unit)? = null,
    onChat: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val barTitle = title ?: if (isThai) "จำกัดสิทธิ์การเข้าถึง" else "Access Denied"

    Scaffold(
        containerColor = Background,
        topBar = {
            LullabyTopBar(
                title = barTitle,
                onBack = onBack,
                navController = navController
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Error.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null, tint = Error, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Error.copy(alpha = 0.12f)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Shield, null, tint = Error, modifier = Modifier.size(12.dp))
                    Text(
                        "Secured",
                        color = Error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "จำกัดสิทธิ์การเข้าถึง",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = OnBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "คุณไม่มีสิทธิ์ในการเข้าถึงหรือดำเนินการในส่วนนี้ เนื่องจากบทบาทปัจจุบันของคุณไม่ได้รับการอนุญาต",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Primary.copy(alpha = 0.10f)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Lock, null, tint = Primary, modifier = Modifier.size(14.dp))
                    Text(
                        "เฉพาะเจ้าของหลักและผู้ที่ได้รับมอบหมายเท่านั้น",
                        color = Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Shield, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(
                            "บริบทการเข้าถึงของคุณ",
                            fontWeight = FontWeight.Bold,
                            color = OnBackground
                        )
                    }
                    AccessDeniedContextRow(
                        icon = { Icon(Icons.Outlined.Description, null, tint = Primary, modifier = Modifier.size(20.dp)) },
                        title = "ส่วนที่พยายามดำเนินการ",
                        body = info?.let { "${it.actionTitleTh}" } ?: "ส่วนนี้",
                        sub = info?.actionTitleEn
                    )
                    AccessDeniedContextRow(
                        icon = { Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(20.dp)) },
                        title = info?.memberName ?: if (isThai) "บัญชีนี้" else "This account",
                        body = "สิทธิ์ปัจจุบัน: ${info?.accessSummaryTh ?: if (isThai) "จำกัด" else "Limited"}",
                        sub = info?.memberRoleLabel
                    )
                    AccessDeniedContextRow(
                        icon = { Icon(Icons.Filled.FamilyRestroom, null, tint = Primary, modifier = Modifier.size(20.dp)) },
                        title = "ผู้ดูแลหลัก: ${info?.ownerName ?: if (isThai) "เจ้าของบัญชี" else "Account owner"}",
                        trailing = {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val ownerFirst = info?.ownerName?.substringAfter("คุณ")?.substringBefore(" ")?.ifBlank { null }
                ?: info?.ownerName
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Shield, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(
                            "ขั้นตอนการขอรับสิทธิ์เข้าถึง",
                            fontWeight = FontWeight.Bold,
                            color = OnBackground
                        )
                    }
                    AccessDeniedStep(
                        number = "1",
                        title = "กดส่งคำขอเปิดสิทธิ์ไปยังผู้ดูแลหลัก",
                        body = "ระบบจัดทำแบบฟอร์มขออนุมัติทางการแพทย์โดยอัตโนมัติ พร้อมแจ้งวัตถุประสงค์"
                    )
                    AccessDeniedStep(
                        number = "2",
                        title = "${ownerFirst ?: "ผู้ดูแลหลัก"}ตรวจสอบและอนุมัติผ่านแอป",
                        body = "แจ้งเตือนเร่งด่วนจะถูกส่งไปยังสมาร์ทโฟนเจ้าของบัญชีทันทีเพื่อดูความรวดเร็ว"
                    )
                    AccessDeniedStep(
                        number = "3",
                        title = "ระบบปลดล็อกข้อมูลทันทีเมื่อได้รับการอนุมัติ",
                        body = "ไม่ต้องออกจากหน้านี้ ระบบจะรีเฟรชสิทธิ์การเข้าถึงให้อัตโนมัติ"
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            PrimaryButton(
                text = if (info?.requested == true) {
                    if (isThai) "ส่งคำขอแล้ว" else "Request sent"
                } else {
                    "ส่งคำขอเพิ่มสิทธิ์ (Request Access)"
                },
                onClick = { onRequestAccess?.invoke() },
                enabled = onRequestAccess != null && info?.requested != true,
                isLoading = false,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onChat?.invoke() ?: onBack() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("แชทสอบถาม", fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = {
                    onHome?.invoke() ?: onBack()
                },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("← กลับสู่หน้าหลัก (Back to Home)", color = OnSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Shield, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    "ข้อมูลสุขภาพเด็กได้รับการคุ้มครองความปลอดภัยตามมาตรฐาน PDPA & HIPAA",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun AccessDeniedContextRow(
    icon: @Composable () -> Unit,
    title: String,
    body: String? = null,
    sub: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 14.sp)
            if (!body.isNullOrBlank()) {
                Text(body, color = OnSurfaceVariant, fontSize = 12.sp)
            }
            if (!sub.isNullOrBlank()) {
                Text(sub, color = OnSurfaceVariant, fontSize = 11.sp)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun AccessDeniedStep(number: String, title: String, body: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 14.sp)
            Text(body, color = OnSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}
