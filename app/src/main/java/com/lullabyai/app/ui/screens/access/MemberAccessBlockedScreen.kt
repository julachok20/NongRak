@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.ui.theme.Primary

/** Shown when a non-owner member tries a feature the owner did not grant (e.g. Connect / book doctor). */
@Composable
fun MemberAccessBlockedScreen(
    navController: NavController,
    onBack: () -> Unit,
    title: String? = null
) {
    val s = LocalStrings.current
    val isThai = s.isThai

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = title ?: if (isThai) "เชื่อมต่อคุณหมอ" else "Connect to doctor",
                onBack = onBack,
                navController = navController
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Error.copy(alpha = 0.10f)
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Error,
                    modifier = Modifier.padding(20.dp).size(48.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                if (isThai) "คุณไม่มีสิทธิ์เข้าถึงส่วนนี้" else "You do not have access here",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (isThai) {
                    "ลองติดต่อเจ้าของบัญชีหากคุณคิดว่านี่เป็นความผิดพลาด"
                } else {
                    "Try contacting the account owner if you think this is a mistake"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton(
                text = if (isThai) "กลับ" else "Go back",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
