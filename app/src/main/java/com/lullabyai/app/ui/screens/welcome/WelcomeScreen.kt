package com.lullabyai.app.ui.screens.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.R
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*

@Composable
fun WelcomeScreen(
    onSignUp: () -> Unit,
    onLogin: () -> Unit
) {
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(48.dp))

            // Title
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

            // Brand logo
            Image(
                painter = painterResource(R.drawable.nongrak_logo),
                contentDescription = "NongRak AI",
                modifier = Modifier.size(280.dp)
            )

            // Buttons
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureChip("AI-Powered Insights")
                    FeatureChip("🔒 Secure Data")
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
