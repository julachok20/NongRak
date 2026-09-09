package com.lullabyai.app.ui.mascot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.data.repository.UnlockAnnouncer
import com.lullabyai.app.data.repository.UnlockToast
import kotlinx.coroutines.delay

private val BannerBlue = Color(0xFFEFF4FF)
private val BannerInk = Color(0xFF1F2937)
private val BannerInkSoft = Color(0xFF4B5563)

/**
 * Slides a "you unlocked this" card in from the top, then leaves by itself.
 *
 * Same motion as the chat feed banner on purpose: one language for "something good just
 * happened", whether that was a meal or a scarf.
 */
@Composable
fun UnlockBannerHost(isThai: Boolean, modifier: Modifier = Modifier) {
    val toast by UnlockAnnouncer.current.collectAsState()
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            toast?.let { UnlockBannerCard(it, isThai) }
        }
    }
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        delay(2800)
        UnlockAnnouncer.dismiss()
    }
}

@Composable
private fun UnlockBannerCard(toast: UnlockToast, isThai: Boolean) {
    val art = MascotArt.entries.firstOrNull { it.key == toast.mascotKey } ?: MascotArt.NONGRAK
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = BannerBlue,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(top = 56.dp, start = 24.dp, end = 24.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Mascot(
                art = art,
                mood = MascotMood.HAPPY,
                modifier = Modifier.size(38.dp)
            )
            Column {
                Text(
                    toast.headline(isThai),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BannerInk
                )
                Text(toast.detail(isThai), fontSize = 11.sp, color = BannerInkSoft)
            }
        }
    }
}
