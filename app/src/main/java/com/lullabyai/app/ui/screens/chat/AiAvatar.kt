package com.lullabyai.app.ui.screens.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.lullabyai.app.ui.mascot.Mascot
import com.lullabyai.app.ui.mascot.MascotArt
import com.lullabyai.app.ui.mascot.MascotMood
import com.lullabyai.app.ui.theme.Primary

/**
 * NongRak's avatar in chat.
 *
 * The mascot is skeletally deformed rather than merely scaled — see [Mascot] — so the body leans
 * and a limb swings while the AI works, and the mouth and eyes are painted over the printed ones
 * on top of that.
 *
 * Only the halo is left here. Everything that moves belongs to the mascot itself, so the same
 * behaviour is available anywhere else in the app without copying calibration constants around.
 */
@Composable
fun AiAvatarView(isSpeaking: Boolean, modifier: Modifier = Modifier) {
    // The halo still breathes on its own: it belongs to the chat surface, not to the character.
    val transition = rememberInfiniteTransition(label = "avatar")
    val glow by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (isSpeaking) 1600 else 2600), RepeatMode.Reverse
        ),
        label = "glow"
    )
    val ringAlpha = if (isSpeaking) 0.22f + 0.18f * glow else 0.10f + 0.06f * glow

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Primary.copy(alpha = ringAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize(0.92f)
                .clip(CircleShape)
                .background(Color.White)
        )
        Box(Modifier.fillMaxSize(0.88f).aspectRatio(1f), contentAlignment = Alignment.Center) {
            Mascot(
                art = MascotArt.NONGRAK,
                // Talking has its own cadence; otherwise he simply breathes.
                mood = if (isSpeaking) MascotMood.SPEAKING else MascotMood.IDLE,
                speaking = isSpeaking,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
