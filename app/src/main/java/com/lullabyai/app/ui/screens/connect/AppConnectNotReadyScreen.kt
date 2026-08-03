@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.screens.doctorconnect.ConnectBottomNav
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary

/**
 * Shown when [com.lullabyai.app.data.AppConnectFeatures.IS_READY] is false —
 * App Connect is not production-ready yet (no full wearable / health-app bridge).
 */
@Composable
fun AppConnectNotReadyScreen(navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = "App Connect",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { ConnectBottomNav() },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(Primary.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DeviceHub, null, tint = Primary, modifier = Modifier.size(44.dp))
            }

            Text(
                if (s.isThai) "ยังไม่พร้อมใช้งาน" else "Not ready for now",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (s.isThai)
                            "App Connect กำลังอยู่ระหว่างการพัฒนา ฟีเจอร์นี้จะให้คุณซิงค์ข้อมูลจากแอปสุขภาพ (เช่น Google Fit, Samsung Health) นาฬิกา และอุปกรณ์ IoT เข้ากับ NongRak"
                        else
                            "App Connect is still in development. It will let you sync health apps (Google Fit, Samsung Health), wearables, and child IoT devices into NongRak.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (s.isThai)
                            "ตอนนี้ยังไม่สามารถเชื่อมต่อได้จริง — กรุณาใช้ Pedia Connect หากต้องการเชื่อมต่อกับคุณหมอและโรงพยาบาล"
                        else
                            "You cannot connect yet — use Pedia Connect if you need to link with doctors and hospitals.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(
                    if (s.isThai) "เราจะแจ้งในอัปเดตถัดไปเมื่อ App Connect พร้อมใช้งาน"
                    else "We will enable App Connect in a future app update.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Outline
                )
            }
        }
    }
}
