package com.lullabyai.app.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lullabyai.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Inline replay widgets for a just-picked (not yet uploaded) attachment.
 *
 * ponytail: uses the platform's own MediaPlayer / VideoView rather than adding ExoPlayer —
 * these are single short clips the parent picked on this same phone, so the whole media3
 * dependency tree buys nothing here. Upgrade path: if we ever stream remote memories with
 * adaptive bitrate, swap both cards for media3 ExoPlayer behind the same two signatures.
 */

/** Human-readable list of formats each media type actually accepts, for the picker hint. */
fun supportedFormatHint(mediaType: String, isThai: Boolean): String = when (mediaType) {
    "audio" -> if (isThai) "รองรับ MP3, M4A, WAV, AAC • สูงสุด 10MB"
    else "Supports MP3, M4A, WAV, AAC • max 10MB"
    "video" -> if (isThai) "รองรับ MP4, MOV, 3GP • สูงสุด 10MB"
    else "Supports MP4, MOV, 3GP • max 10MB"
    "photo" -> if (isThai) "รองรับ JPG, PNG, HEIC • สูงสุด 10MB"
    else "Supports JPG, PNG, HEIC • max 10MB"
    else -> if (isThai) "รองรับ PDF, JPG, PNG • สูงสุด 10MB"
    else "Supports PDF, JPG, PNG • max 10MB"
}

private fun formatClock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Play/pause card for a picked audio clip so the parent can hear it back before saving.
 */
@Composable
fun AudioReplayCard(
    uri: Uri,
    fileName: String?,
    isThai: Boolean,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var positionMs by remember(uri) { mutableIntStateOf(0) }
    var durationMs by remember(uri) { mutableIntStateOf(0) }
    var failed by remember(uri) { mutableStateOf(false) }

    // One player per uri. prepare() is synchronous here because these are local files
    // the user just picked — no network buffering to wait on.
    val player = remember(uri) {
        MediaPlayer().also { mp ->
            runCatching {
                mp.setDataSource(context, uri)
                mp.prepare()
            }.onFailure { failed = true }
        }
    }

    DisposableEffect(player) {
        player.setOnCompletionListener {
            isPlaying = false
            positionMs = 0
        }
        durationMs = runCatching { player.duration }.getOrDefault(0)
        onDispose {
            runCatching { player.release() }
        }
    }

    // Polling beats wiring a progress listener MediaPlayer doesn't have; 200ms reads
    // smooth on a progress bar and costs nothing measurable.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = runCatching { player.currentPosition }.getOrDefault(0)
            delay(200)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = if (failed) Error.copy(0.12f) else Primary) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            runCatching { player.pause() }
                            isPlaying = false
                        } else {
                            runCatching { player.start() }
                            isPlaying = true
                        }
                    },
                    enabled = !failed
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isThai) "เล่นซ้ำ" else "Replay",
                        tint = if (failed) Error else androidx.compose.ui.graphics.Color.White
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    fileName ?: (if (isThai) "ไฟล์เสียง" else "Audio clip"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (failed) {
                    Text(
                        if (isThai) "เล่นไฟล์นี้ไม่ได้ (รูปแบบไม่รองรับ)" else "Can't play this file (unsupported format)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Error
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { if (durationMs > 0) positionMs.toFloat() / durationMs else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary,
                        trackColor = OutlineVariant
                    )
                    Text(
                        "${formatClock(positionMs)} / ${formatClock(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline
                    )
                }
            }
        }
    }
}

/**
 * Inline video preview with the platform scrub controls, so the parent can replay
 * a picked clip before saving it.
 */
@Composable
fun VideoReplayCard(
    uri: Uri,
    isThai: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // key(uri) forces a fresh VideoView when the pick changes — reusing the old one
            // via an update block would restart playback on every recomposition.
            key(uri) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(uri)
                            setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                            // Show frame 1 instead of a black box before the user hits play.
                            seekTo(1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }
            Text(
                if (isThai) "แตะวิดีโอเพื่อเล่นซ้ำ" else "Tap the video to replay",
                style = MaterialTheme.typography.labelSmall,
                color = Outline
            )
        }
    }
}
