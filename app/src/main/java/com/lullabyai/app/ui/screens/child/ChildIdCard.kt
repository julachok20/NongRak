package com.lullabyai.app.ui.screens.child

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lullabyai.app.R
import com.lullabyai.app.util.ChildIdCardData

private val CardBlue = Color(0xFF1D3FA8)
private val CardBlueLight = Color(0xFF3E6BD8)
private val Ink = Color(0xFF12204A)
private val Muted = Color(0xFF8A93AE)
private const val CARD_RATIO = 1.62f

/**
 * บัตรประจำตัวดิจิทัลเด็ก — the same card the website and LINE show, drawn natively.
 *
 * Every size is a fraction of the card's own width, so it reads identically as a strip at the top
 * of the health book, as a full-screen card held out to a nurse, or on a phone turned sideways.
 * Fixed sp sizes would leave 7sp type on a card twice as wide.
 */
@Composable
fun ChildIdCard(data: ChildIdCardData, modifier: Modifier = Modifier) {
    var flipped by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    val isThai = com.lullabyai.app.ui.localization.LocalStrings.current.isThai

    Column(modifier) {
        CardFlipper(data, flipped, isThai) { flipped = !flipped }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { flipped = !flipped }) {
                Text(
                    if (flipped) (if (isThai) "ดูด้านหน้า" else "Front") else (if (isThai) "ดูด้านหลัง" else "Back"),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            TextButton(onClick = { full = true }) {
                Text(
                    if (isThai) "ดูเต็มจอ" else "Full screen",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CardBlue
                )
            }
        }
    }

    if (full) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC0B1836)).clickable { full = false },
                contentAlignment = Alignment.Center
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CardFlipper(data, flipped, isThai) { flipped = !flipped }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { flipped = !flipped }, shape = RoundedCornerShape(100.dp)) {
                            Text(if (isThai) "พลิกบัตร" else "Flip")
                        }
                        Button(
                            onClick = { full = false },
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = CardBlue)
                        ) { Text(if (isThai) "ปิด" else "Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFlipper(data: ChildIdCardData, flipped: Boolean, isThai: Boolean, onFlip: () -> Unit) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(500), label = "cardFlip")
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(CARD_RATIO).clickable(onClick = onFlip)) {
        val w = maxWidth
        // Every size on the card is a fraction of its own width, but `.sp` also multiplies by the
        // phone's font-size setting while the card's box does not grow with it. On a phone set to
        // large text that pushed the lines out of the card. Pinning fontScale keeps the card's
        // proportions intact; the card is a fixed-layout document, not body text.
        val cardDensity = LocalDensity.current.let { Density(it.density, fontScale = 1f) }
        CompositionLocalProvider(LocalDensity provides cardDensity) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 14f * density
                }
            ) {
                if (rotation <= 90f) {
                    CardFace(w) { CardFront(data, w, isThai) }
                } else {
                    // Counter-rotated, or the back would be drawn mirrored.
                    Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                        CardFace(w) { CardBack(data, w, isThai) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFace(w: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(w * 0.055f))
            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFF4F8FF), Color(0xFFFDF2F7))))
    ) {
        CardArtwork(w)
        content()
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.16f)
                .background(Brush.horizontalGradient(listOf(CardBlue, CardBlueLight))),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "สำหรับการยืนยันตัวตนภายในระบบ NongRak เท่านั้น / For identification within the NongRak system only",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = (w.value * 0.020f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = w * 0.035f)
            )
        }
    }
}

/** Soft clouds and hearts from the printed template. */
@Composable
private fun CardArtwork(w: Dp) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).size(w * 0.42f, w * 0.26f).offset(x = w * 0.04f, y = w * 0.04f)
                .clip(CircleShape).background(Color(0xFFF7A8C4).copy(alpha = 0.20f))
        )
        Box(
            Modifier.align(Alignment.BottomStart).size(w * 0.50f, w * 0.24f).offset(x = -w * 0.08f, y = -w * 0.02f)
                .clip(CircleShape).background(Color(0xFF7FA8FF).copy(alpha = 0.16f))
        )
        Box(
            Modifier.align(Alignment.CenterEnd).size(w * 0.30f, w * 0.18f).offset(x = w * 0.06f)
                .clip(CircleShape).background(Color(0xFFBBD3FF).copy(alpha = 0.22f))
        )
    }
}

@Composable
private fun CardHeader(w: Dp, titleTh: String, titleEn: String, note: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column {
            Image(
                painter = painterResource(R.drawable.nongrak_logo_horizon),
                contentDescription = "NongRak",
                modifier = Modifier.height(w * 0.058f),
                contentScale = ContentScale.Fit
            )
            Text("เพราะทุกก้าวของลูก คือความรักของเรา", color = Color(0xFF7D8BB0), fontSize = (w.value * 0.016f).sp, maxLines = 1)
        }
        Spacer(Modifier.width(w * 0.025f))
        Box(Modifier.width(1.dp).height(w * 0.07f).background(Color(0xFFC9D8FF)))
        Spacer(Modifier.width(w * 0.025f))
        Column(Modifier.weight(1f)) {
            Text(titleTh, color = CardBlue, fontWeight = FontWeight.Bold, fontSize = (w.value * 0.032f).sp, maxLines = 1)
            Text(titleEn, color = Color(0xFF5A6B9C), fontSize = (w.value * 0.021f).sp, maxLines = 1)
        }
        note()
    }
}

@Composable
private fun CardField(w: Dp, label: String, value: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = (w.value * 0.018f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            color = Ink,
            fontWeight = FontWeight.Bold,
            fontSize = (w.value * (if (small) 0.027f else 0.030f)).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BoxScope.CardFront(data: ChildIdCardData, w: Dp, isThai: Boolean) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.84f).padding(w * 0.03f)) {
        CardHeader(w, if (isThai) "บัตรประจำตัวดิจิทัลเด็ก" else "Child Digital ID Card", "Child Digital ID Card") {
            Text(
                "Little\nMoments\nBig Love",
                color = Color(0xFF5A6B9C),
                fontSize = (w.value * 0.021f).sp,
                lineHeight = (w.value * 0.026f).sp
            )
        }
        Spacer(Modifier.height(w * 0.015f))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(w * 0.022f)) {
            // The child's own photo, the one set in Child Info.
            Box(Modifier.size(w * 0.19f).clip(CircleShape).background(Color(0xFFE7EEFF)), contentAlignment = Alignment.Center) {
                if (data.photo != null) {
                    coil.compose.AsyncImage(
                        model = data.photo,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("👶", fontSize = (w.value * 0.07f).sp)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(w * 0.003f)) {
                CardField(w, "ชื่อ-นามสกุล / Full Name", data.fullName)
                Row(horizontalArrangement = Arrangement.spacedBy(w * 0.022f)) {
                    CardField(w, "รหัสเด็ก / Child ID", data.cardNumber, Modifier.weight(1f), small = true)
                    CardField(w, "อายุ / Age", data.age, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(w * 0.022f)) {
                    CardField(w, "วันเกิด / Date of Birth", data.birthdate, Modifier.weight(1f))
                    CardField(w, "กรุ๊ปเลือด / Blood", data.bloodGroup, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(w * 0.022f)) {
                    CardField(w, "เพศ / Gender", data.gender, Modifier.weight(1f))
                    CardField(w, "ผู้ปกครอง / Guardian", data.guardian, Modifier.weight(1f))
                }
            }

            // The template's "Status" chip is a QR instead: it is the part a nurse actually uses.
            data.qr?.let { qr ->
                Column(Modifier.width(w * 0.20f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(w * 0.012f))
                            .background(Color.White)
                            .padding(w * 0.006f)
                    )
                    Text(
                        if (isThai) "สแกนดูข้อมูลฉุกเฉิน" else "Scan for emergency info",
                        color = Color(0xFF5A6B9C),
                        fontSize = (w.value * 0.015f).sp,
                        lineHeight = (w.value * 0.019f).sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CardBack(data: ChildIdCardData, w: Dp, isThai: Boolean) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.84f).padding(w * 0.03f)) {
        CardHeader(w, if (isThai) "ข้อมูลพื้นฐานเพิ่มเติม" else "Additional Information", "Additional Basic Information") {
            Column(horizontalAlignment = Alignment.End) {
                Text("ดูแลวันนี้ เพื่ออนาคตที่สดใสของลูก", color = Color(0xFF5A6B9C), fontSize = (w.value * 0.016f).sp, maxLines = 1)
                Text("Healthy Children, Brighter Tomorrows", color = Muted, fontSize = (w.value * 0.015f).sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(w * 0.015f))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(w * 0.018f)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(w * 0.006f)) {
                CardPill(w, "🌐", "สัญชาติ / Nationality", data.nationality)
                CardPill(w, "🩺", "โรคประจำตัว / Conditions", data.conditions)
                CardPill(w, "💊", "การแพ้ยา / Drug Allergies", data.drugAllergies)
                CardPill(w, "🍽️", "การแพ้อาหาร / Food Allergies", data.foodAllergies)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(w * 0.006f)) {
                CardPill(w, "📞", "เบอร์ฉุกเฉิน / Emergency", data.emergencyContact)
                CardPill(w, "🏥", "โรงพยาบาลประจำ / Hospital", data.hospital)
                CardPill(w, "📍", "ที่อยู่ / Address", data.address)
                data.qr?.let { qr ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(w * 0.015f))
                            .background(Color.White.copy(alpha = 0.7f))
                            .padding(w * 0.008f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(w * 0.01f)
                    ) {
                        Image(bitmap = qr.asImageBitmap(), contentDescription = null, modifier = Modifier.size(w * 0.05f))
                        Text(
                            if (isThai) "สแกนดูข้อมูลล่าสุด" else "Scan for latest info",
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (w.value * 0.017f).sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPill(w: Dp, icon: String, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(w * 0.015f))
            .background(Color.White.copy(alpha = 0.7f))
            .padding(horizontal = w * 0.012f, vertical = w * 0.005f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(w * 0.01f)
    ) {
        Box(Modifier.size(w * 0.048f).clip(CircleShape).background(Color(0xFFE7EEFF)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = (w.value * 0.021f).sp)
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = Muted, fontSize = (w.value * 0.015f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value,
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = (w.value * 0.025f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
