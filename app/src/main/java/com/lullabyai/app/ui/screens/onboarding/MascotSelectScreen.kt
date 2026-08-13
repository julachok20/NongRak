package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.screens.chat.CompanionEmotion
import com.lullabyai.app.ui.screens.chat.MascotId
import com.lullabyai.app.ui.screens.chat.poseFor
import com.lullabyai.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Last step of onboarding: pick the AI companion that lives on the chat screen.
 *
 * The choice is written straight to DataStore here rather than handed up through
 * OnboardingViewModel — it is a device preference, not part of the child profile the
 * onboarding flow submits, and it must survive even if the parent never finishes signup.
 */
@Composable
fun MascotSelectScreen(
    onSelected: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(MascotId.DEFAULT) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "เลือกเพื่อนคู่ใจ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "เพื่อนคนนี้จะคอยช่วยจดบันทึกและเตือนเรื่องสุขภาพของลูกน้อย เปลี่ยนได้ทีหลังในหน้าตั้งค่า",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MascotId.entries.forEach { mascot ->
                MascotCard(
                    mascot = mascot,
                    selected = mascot == selected,
                    onClick = { selected = mascot }
                )
            }
        }

        PrimaryButton(
            text = "เลือก${selected.thaiName}",
            isLoading = saving,
            onClick = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        SettingsManager(context).setSelectedMascot(selected.assetPrefix)
                        onSelected()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp, top = 8.dp)
        )
    }
}

@Composable
private fun MascotCard(
    mascot: MascotId,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, label = "border")
    val borderColor by animateColorAsState(
        if (selected) Primary else MaterialTheme.colorScheme.outlineVariant,
        label = "borderColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) PrimaryContainer.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface
            )
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            // Greeting is the waving pose — the one that reads as "pick me" in a picker.
            painter = painterResource(mascot.poseFor(CompanionEmotion.GREETING)),
            contentDescription = mascot.thaiName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                mascot.thaiName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) Primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                mascot.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                mascot.traits.forEach { trait ->
                    Text(
                        trait,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) PrimaryContainer.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (selected) {
            Box(
                modifier = Modifier.size(24.dp).background(Primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = OnPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
