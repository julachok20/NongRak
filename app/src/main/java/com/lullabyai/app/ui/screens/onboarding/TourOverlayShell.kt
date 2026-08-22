package com.lullabyai.app.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

enum class CardAnchor { START, CENTER, END }

data class TourStepInfo(
    val route: String,
    val color: Color,
    val cardAnchor: CardAnchor,
    val cardWidthFraction: Float,
    val cardBottomPadding: Dp,
    val arrowRotationDeg: Float? = null,
    val targetTh: String? = null,
    val targetEn: String? = null,
    val targetKey: String? = null,
    val samplePreview: (@Composable (Boolean) -> Unit)? = null,
    val titleTh: String,
    val titleEn: String,
    val bodyTh: String,
    val bodyEn: String
)

@Composable
fun GuidedTourOverlay(
    steps: List<TourStepInfo>,
    step: Int,
    homeRoute: String,
    navController: NavController,
    isThai: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    val info = steps[step]
    val isLast = step == steps.lastIndex

    LaunchedEffect(step) {
        navController.navigate(info.route) {
            popUpTo(homeRoute) { inclusive = false }
            launchSingleTop = true
        }
    }

    BackHandler(enabled = true) { if (step > 0) onBack() else onSkip() }

    // A step is finished by doing the thing, not by pressing Next: tapping the spotlit control
    // navigates away from the step's route, and that is the signal. `arrived` guards against the
    // tour's own navigation above counting as the user's action.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var arrived by remember(step) { mutableStateOf(false) }
    LaunchedEffect(currentRoute, step) {
        if (currentRoute == info.route) arrived = true
        else if (arrived) { if (isLast) onFinish() else onNext() }
    }

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val spotlightRect = info.targetKey?.let { TourTargetRegistry.targets[it] }?.translate(-overlayOrigin.x, -overlayOrigin.y)

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() }
            // Swallow taps on the dimmed area only. Leaving the spotlit control tappable is the
            // whole point — the card asks her to tap it, so eating that tap would make the tour lie.
            .pointerInput(spotlightRect) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (spotlightRect?.contains(down.position) != true) down.consume()
                }
            }
    ) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(Color.Black.copy(alpha = 0.45f))
            spotlightRect?.let { r ->
                val pad = 8.dp.toPx()
                val topLeft = Offset(r.left - pad, r.top - pad)
                val size = Size(r.width + pad * 2, r.height + pad * 2)
                val corner = CornerRadius(16.dp.toPx())
                drawRoundRect(color = Color.Transparent, topLeft = topLeft, size = size, cornerRadius = corner, blendMode = BlendMode.Clear)
                drawRoundRect(color = info.color, topLeft = topLeft, size = size, cornerRadius = corner, style = Stroke(width = 3.dp.toPx()))
            }
        }

        Box(
            Modifier.align(
                when (info.cardAnchor) {
                    CardAnchor.START -> Alignment.BottomStart
                    CardAnchor.CENTER -> Alignment.BottomCenter
                    CardAnchor.END -> Alignment.BottomEnd
                }
            )
                .fillMaxWidth(info.cardWidthFraction)
                .padding(horizontal = 16.dp)
                .padding(bottom = info.cardBottomPadding)
        ) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${step + 1}/${steps.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onToggleLanguage, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                Icon(Icons.Filled.Language, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(if (isThai) "EN" else "TH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                            if (!isLast) TextButton(onClick = onSkip) { Text(if (isThai) "ข้าม" else "Skip", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    Text(if (isThai) info.titleTh else info.titleEn, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = info.color)
                    Text(if (isThai) info.bodyTh else info.bodyEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

                    info.samplePreview?.let { preview ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (isThai) "ตัวอย่าง" else "Sample", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            preview(isThai)
                        }
                    }

                    val targetText = if (isThai) info.targetTh else info.targetEn
                    if (targetText != null && info.arrowRotationDeg != null) {
                        Surface(shape = RoundedCornerShape(12.dp), color = info.color.copy(0.08f)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowRightAlt, null, tint = info.color,
                                    modifier = Modifier.graphicsLayer { rotationZ = info.arrowRotationDeg }
                                )
                                Text(
                                    (if (isThai) "แตะ" else "Tap ") + targetText,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (step > 0) {
                            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                                Text(if (isThai) "ย้อนกลับ" else "Back")
                            }
                        }
                        Button(
                            onClick = { if (isLast) onFinish() else onNext() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = info.color)
                        ) {
                            Text(if (isLast) (if (isThai) "เสร็จสิ้น" else "Finish") else (if (isThai) "ถัดไป" else "Next"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
