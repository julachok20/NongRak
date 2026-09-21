package com.lullabyai.app.ui.screens.access

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lullabyai.app.data.repository.ChildRepository

/**
 * Photos and voice notes a caregiver attached to a care log, shown on both the caregiver's
 * quick-record list and the parent's history. The files are private: each is signed on demand for an
 * hour, so nothing about them is ever public.
 */

private val careRepo = ChildRepository()

@Composable
private fun rememberSignedCareUrl(path: String?): String? {
    val url by produceState<String?>(initialValue = null, path) {
        value = path?.let { careRepo.signedCareMediaUrl(it) }
    }
    return url
}

@Composable
fun CarePhotoThumb(path: String, size: Dp = 64.dp, onOpen: ((String) -> Unit)? = null) {
    val url = rememberSignedCareUrl(path)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14173DB8))
            .let { m -> if (url != null && onOpen != null) m.clickable { onOpen(url) } else m },
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        }
    }
}

/**
 * Tap to play, tap again to stop. One MediaPlayer per chip, released when the chip leaves the
 * screen, so scrolling a long history never leaves audio playing from a row that is gone.
 */
@Composable
fun CareVoiceChip(path: String, seconds: Int?, isThai: Boolean, tint: Color = Color(0xFF173DB8)) {
    val url = rememberSignedCareUrl(path)
    var playing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }

    DisposableEffect(path) {
        onDispose { runCatching { player.release() } }
    }

    Row(
        Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(tint.copy(alpha = 0.10f))
            .clickable(enabled = url != null && !preparing) {
                if (playing) {
                    runCatching { player.stop(); player.reset() }
                    playing = false
                } else {
                    failed = false
                    preparing = true
                    runCatching {
                        player.reset()
                        player.setAudioAttributes(
                            AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                        )
                        player.setDataSource(url)
                        player.setOnPreparedListener { preparing = false; playing = true; it.start() }
                        player.setOnCompletionListener { playing = false; runCatching { it.reset() } }
                        player.setOnErrorListener { _, _, _ -> preparing = false; playing = false; failed = true; true }
                        player.prepareAsync()
                    }.onFailure { preparing = false; failed = true }
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            preparing || url == null -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = tint)
            else -> Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(
            when {
                failed -> if (isThai) "เล่นเสียงไม่ได้" else "Can't play"
                else -> (if (isThai) "ข้อความเสียง" else "Voice note") + (seconds?.let { " ${it}s" } ?: "")
            },
            fontSize = 11.sp,
            color = tint
        )
    }
}
