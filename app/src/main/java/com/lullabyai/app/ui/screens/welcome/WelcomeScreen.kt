package com.lullabyai.app.ui.screens.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.R
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.ui.components.LanguageToggleButton
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    onSignUp: () -> Unit,
    onLogin: () -> Unit,
    settingsViewModel: SettingsViewModel,
    // Default no-op keeps this screen wireable today — AppNavigation.kt passes the real
    // navigation to Screen.DoctorWelcome.route once the doctor flow is hooked up centrally.
    onDoctorMode: () -> Unit = {}
) {
    val s = LocalStrings.current
    val settings by settingsViewModel.settings.collectAsState()

    // Staggered reveal: logo pops first, then headline, then chips, then the CTAs —
    // reads as a small "intro" instead of everything dumping in at once.
    var showLogo by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showChips by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showLogo = true
        delay(450)
        showText = true
        delay(300)
        showChips = true
        delay(200)
        showButtons = true
    }

    // Slow continuous breathing glow behind the logo — keeps the screen feeling alive
    // without competing with the entrance animation above.
    val pulse = rememberInfiniteTransition(label = "welcomePulse")
    val glowScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseOutCubic), RepeatMode.Reverse),
        label = "glowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(PrimaryContainer.copy(alpha = 0.12f), Color.White)
                )
            )
    ) {
        LanguageToggleButton(
            isThai = settings.language == AppLanguage.TH,
            onToggle = { settingsViewModel.toggleLanguage() },
            modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(20.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(48.dp))

            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = s.welcomeTitle,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = s.welcomeSubtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Brand logo with a soft pulsing glow behind it
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(PrimaryContainer.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLogo,
                    enter = fadeIn(tween(700)) + scaleIn(tween(700, easing = EaseOutCubic), initialScale = 0.6f)
                ) {
                    Image(
                        painter = painterResource(R.drawable.nongrak_logo),
                        contentDescription = "NongRak AI",
                        modifier = Modifier.size(280.dp)
                    )
                }
            }

            // Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(
                    visible = showButtons,
                    enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 3 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PrimaryButton(text = "${s.signUp} →", onClick = onSignUp)

                        TextButton(onClick = onLogin) {
                            Text(
                                text = "${s.haveAccount} ${s.login}",
                                color = Primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showChips,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureChip("AI-Powered Insights")
                        FeatureChip("🔒 Secure Data")
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDoctorMode) {
                    Text(
                        text = "คุณเป็นแพทย์ใช่ไหม?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FeatureChip(text: String) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
