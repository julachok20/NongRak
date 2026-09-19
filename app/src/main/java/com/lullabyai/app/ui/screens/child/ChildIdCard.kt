package com.lullabyai.app.ui.screens.child

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
                    CardFace(w, FRONT_FOOTER) { CardFront(data, w, isThai) }
                } else {
                    // Counter-rotated, or the back would be drawn mirrored.
                    Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                        CardFace(w, BACK_FOOTER) { CardBack(data, w, isThai) }
                    }
                }
            }
        }
    }
}

/**
 * A share of the card's own width, the Compose twin of the LIFF card's `cqw` unit.
 *
 * Both cards size every label as a percentage of the card, so `w.cqw(3.3f)` here and
 * `text-[3.3cqw]` there are the same number and can be diffed by eye. Keep them equal.
 */
private fun Dp.cqw(percent: Float) = (value * percent / 100f).sp

private const val FRONT_FOOTER =
    "สำหรับการยืนยันตัวตนภายในระบบ NongRak เท่านั้น / For identification within the NongRak system only"
private const val BACK_FOOTER =
    "ใช้สำหรับการดูแลเด็กภายในระบบ NongRak เท่านั้น ไม่สามารถใช้แทนเอกสารราชการได้ / Not valid as an official government document."

@Composable
private fun CardFace(w: Dp, footer: String, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(w * 0.05f))
            .background(Color(0xFFF7FAFF))
            .border(1.dp, Color(0xFFDCE6FF), RoundedCornerShape(w * 0.05f))
    ) {
        CardArtwork()

        // Content stops above the wave, the same 17% the LIFF card reserves.
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.83f)
                .padding(w * 0.03f),
            content = content
        )

        CardWave(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.22f))

        Text(
            footer,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = w.cqw(2f),
            lineHeight = w.cqw(2.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = w * 0.04f, vertical = w * 0.018f)
        )
    }
}

/** The template's blue foot, which rises on the left rather than sitting as a flat band. */
@Composable
private fun CardWave(modifier: Modifier) {
    Canvas(modifier) {
        // Same control points as the LIFF card's SVG, in its 400 x 60 viewBox.
        val sx = size.width / 400f
        val sy = size.height / 60f
        fun x(v: Float) = v * sx
        fun y(v: Float) = v * sy
        val path = Path().apply {
            moveTo(x(0f), y(22f))
            cubicTo(x(70f), y(4f), x(150f), y(34f), x(240f), y(20f))
            cubicTo(x(310f), y(9f), x(360f), y(16f), x(400f), y(12f))
            lineTo(x(400f), y(60f))
            lineTo(x(0f), y(60f))
            close()
        }
        drawPath(
            path,
            Brush.horizontalGradient(listOf(CardBlue, CardBlueLight), startX = 0f, endX = size.width)
        )
    }
}

/**
 * The printed background: a pale wash, clouds up the right side and low on the left, and scattered
 * hearts. Vectors, not a bitmap, so it stays clean when the card is opened full screen.
 */
@Composable
private fun CardArtwork() {
    Canvas(Modifier.fillMaxSize()) {
        val sx = size.width / 400f
        val sy = size.height / 247f
        fun px(v: Float) = v * sx
        fun py(v: Float) = v * sy

        drawRect(
            Brush.linearGradient(
                0f to Color.White,
                0.55f to Color(0xFFF4F8FF),
                1f to Color(0xFFFDF2F7),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )

        fun cloud(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float) {
            drawOval(
                color = color,
                topLeft = Offset(px(cx - rx), py(cy - ry)),
                size = Size(px(rx * 2), py(ry * 2)),
                alpha = alpha
            )
        }
        cloud(330f, 74f, 46f, 26f, Color(0xFFBBD3FF), 0.30f)
        cloud(362f, 86f, 38f, 22f, Color(0xFFBBD3FF), 0.30f)
        cloud(300f, 92f, 34f, 18f, Color(0xFFBBD3FF), 0.30f)
        cloud(56f, 188f, 52f, 26f, Color(0xFFDCE9FF), 0.55f)
        cloud(104f, 198f, 40f, 20f, Color(0xFFDCE9FF), 0.55f)

        fun heart(atX: Float, atY: Float, scale: Float, color: Color) {
            // The template's heart, 20 x 17.5 units, placed by its top-left corner.
            fun hx(v: Float) = px(atX + v * scale)
            fun hy(v: Float) = py(atY + v * scale)
            val path = Path().apply {
                moveTo(hx(10f), hy(17.5f))
                cubicTo(hx(2f), hy(11.5f), hx(0f), hy(6.5f), hx(3.5f), hy(3f))
                cubicTo(hx(6.5f), hy(0f), hx(10f), hy(2f), hx(10f), hy(5f))
                cubicTo(hx(10f), hy(2f), hx(13.5f), hy(0f), hx(16.5f), hy(3f))
                cubicTo(hx(20f), hy(6.5f), hx(18f), hy(11.5f), hx(10f), hy(17.5f))
                close()
            }
            drawPath(path, color, alpha = 0.5f)
        }
        heart(286f, 26.4f, 1.1f, Color(0xFFF9B6CE))
        heart(352f, 34f, 1f, Color(0xFFF6C9DC))
        heart(238f, 116f, 1f, Color(0xFFC9DCFF))
        heart(150f, 206f, 1f, Color(0xFFF9C9DD))
        heart(62f, 40f, 1f, Color(0xFFD8E6FF))
    }
}

@Composable
private fun CardHeader(
    w: Dp,
    titleTh: String,
    titleEn: String,
    /** The back has four rows of pills to fit, so its title may not wrap onto a third line. */
    compact: Boolean = false,
    note: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column {
            // The official horizontal lockup — logo and wordmark are one asset, so nothing can drift.
            Image(
                painter = painterResource(R.drawable.nongrak_logo_horizon),
                contentDescription = "NongRak",
                modifier = Modifier.height(w * 0.054f),
                contentScale = ContentScale.Fit
            )
            // One tagline, not two: the lockup is already two lines tall, and a third pushed the
            // guardian row off the bottom of the card.
            Text(
                "เพราะทุกก้าวของลูก คือความรักของเรา",
                color = Color(0xFF7D8BB0),
                fontSize = w.cqw(1.5f),
                lineHeight = w.cqw(1.8f),
                maxLines = 1,
                modifier = Modifier.padding(top = w * 0.003f)
            )
        }
        Spacer(Modifier.width(w * 0.025f))
        Box(Modifier.width(1.dp).height(w * 0.07f).background(Color(0xFFC9D8FF)))
        Spacer(Modifier.width(w * 0.025f))
        Column(Modifier.weight(1f)) {
            Text(
                titleTh,
                color = CardBlue,
                fontWeight = FontWeight.Bold,
                fontSize = w.cqw(if (compact) 3f else 3.3f),
                lineHeight = w.cqw(if (compact) 3.4f else 3.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                titleEn,
                color = Color(0xFF5A6B9C),
                fontSize = w.cqw(if (compact) 2f else 2.1f),
                lineHeight = w.cqw(if (compact) 2.4f else 2.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        note()
    }
}

@Composable
private fun CardField(
    w: Dp,
    th: String,
    en: String,
    value: String,
    modifier: Modifier = Modifier,
    /** For fixed-length codes that would otherwise clip in a half-width column. */
    small: Boolean = false,
) {
    Column(modifier) {
        Text(
            "$th / $en",
            color = Muted,
            fontSize = w.cqw(1.8f),
            lineHeight = w.cqw(2.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            color = Ink,
            fontWeight = FontWeight.Bold,
            fontSize = w.cqw(if (small) 2.7f else 3f),
            lineHeight = w.cqw(if (small) 3.1f else 3.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ColumnScope.CardFront(data: ChildIdCardData, w: Dp, isThai: Boolean) {
    CardHeader(w, "บัตรประจำตัวดิจิทัลเด็ก", "Child Digital ID Card") {
        Text(
            buildAnnotatedString {
                append("Little\nMoments\nBig Love ")
                withStyle(SpanStyle(color = Color(0xFFF7A8C4))) { append("♥") }
            },
            color = Color(0xFF5A6B9C),
            fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.End,
            fontSize = w.cqw(2.3f),
            lineHeight = w.cqw(2.7f)
        )
    }

    Spacer(Modifier.height(w * 0.015f))

    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(w * 0.02f)) {
        // The child's own photo, the one set in Child Info.
        Box(
            Modifier.size(w * 0.19f).clip(CircleShape).background(Color(0xFFE7EEFF)),
            contentAlignment = Alignment.Center
        ) {
            if (data.photo != null) {
                coil.compose.AsyncImage(
                    model = data.photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("👶", fontSize = w.cqw(7f))
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(w * 0.004f)) {
            CardField(w, "ชื่อ-นามสกุล", "Full Name", data.fullName)
            Row(horizontalArrangement = Arrangement.spacedBy(w * 0.025f)) {
                CardField(w, "รหัสเด็ก", "Child ID", data.cardNumber, Modifier.weight(1f), small = true)
                CardField(w, "อายุ", "Age", data.age, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(w * 0.025f)) {
                CardField(w, "วันเกิด", "Date of Birth", data.birthdate, Modifier.weight(1f))
                CardField(w, "กรุ๊ปเลือด", "Blood", data.bloodGroup, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(w * 0.025f)) {
                CardField(w, "เพศ", "Gender", data.gender, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
            // Guardian names run long — a half-width column clipped them to "นางสาว รักลูก ใ…".
            CardField(w, "ผู้ปกครอง", "Parent or Guardian", data.guardian)
        }

        // The template's "Status: Active" chip sat here; the owner asked for the QR instead, so the
        // code is big enough to scan straight off a phone screen held at arm's length.
        data.qr?.let { qr ->
            Column(Modifier.width(w * 0.21f), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = if (isThai) "QR ข้อมูลฉุกเฉิน" else "Emergency info QR",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(w * 0.02f))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFDCE6FF), RoundedCornerShape(w * 0.02f))
                        .padding(w * 0.006f)
                )
                Text(
                    "สแกนดูข้อมูลฉุกเฉิน\nScan for emergency info",
                    color = Color(0xFF5A6B9C),
                    textAlign = TextAlign.Center,
                    fontSize = w.cqw(1.8f),
                    lineHeight = w.cqw(2.2f),
                    maxLines = 2,
                    modifier = Modifier.padding(top = w * 0.005f)
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CardBack(data: ChildIdCardData, w: Dp, isThai: Boolean) {
    CardHeader(w, "ข้อมูลพื้นฐานเพิ่มเติม", "Additional Basic Information", compact = true) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "ดูแลวันนี้ เพื่ออนาคตที่สดใสของลูก",
                color = Color(0xFF5A6B9C),
                fontSize = w.cqw(1.8f),
                lineHeight = w.cqw(2.2f),
                maxLines = 1
            )
            Text(
                "Healthy Children, Brighter Tomorrows",
                color = Color(0xFF9AA5C4),
                fontSize = w.cqw(1.7f),
                lineHeight = w.cqw(2.1f),
                maxLines = 1
            )
        }
    }

    Spacer(Modifier.height(w * 0.015f))

    // Two columns, filled row by row, in the same order as the LIFF grid.
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(w * 0.015f)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(w * 0.005f)) {
            CardPill(w, "🌐", "สัญชาติ", "Nationality", data.nationality)
            CardPill(w, "🩺", "โรคประจำตัว", "Medical Conditions", data.conditions)
            CardPill(w, "💊", "การแพ้ยา", "Drug Allergies", data.drugAllergies)
            CardPill(w, "🍽️", "การแพ้อาหาร", "Food Allergies", data.foodAllergies)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(w * 0.005f)) {
            CardPill(w, "📞", "เบอร์ติดต่อฉุกเฉิน", "Emergency Contact", data.emergencyContact, sub = data.emergencyRelation)
            CardPill(w, "🏥", "โรงพยาบาลประจำ", "Preferred Hospital", data.hospital)
            CardPill(w, "📍", "ที่อยู่", "Address", data.address)
            data.qr?.let { qr ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(w * 0.02f))
                        .background(Color.White.copy(alpha = 0.7f))
                        .border(1.dp, Color(0xFFDCE6FF), RoundedCornerShape(w * 0.02f))
                        .padding(horizontal = w * 0.015f, vertical = w * 0.005f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(w * 0.01f)
                ) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(w * 0.055f)
                            .clip(RoundedCornerShape(w * 0.008f))
                            .background(Color.White)
                            .padding(w * 0.003f)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "สแกนเพื่อดูข้อมูลล่าสุด",
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = w.cqw(2f),
                            lineHeight = w.cqw(2.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Scan for latest information",
                            color = Color(0xFF9AA5C4),
                            fontSize = w.cqw(1.8f),
                            lineHeight = w.cqw(2.2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPill(w: Dp, icon: String, th: String, en: String, value: String, sub: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(w * 0.02f))
            .background(Color.White.copy(alpha = 0.7f))
            .border(1.dp, Color(0xFFE4ECFF), RoundedCornerShape(w * 0.02f))
            .padding(horizontal = w * 0.015f, vertical = w * 0.005f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(w * 0.01f)
    ) {
        Box(
            Modifier.size(w * 0.05f).clip(CircleShape).background(Color(0xFFE7EEFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = w.cqw(2.3f))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "$th / $en",
                color = Muted,
                fontSize = w.cqw(1.8f),
                lineHeight = w.cqw(2.2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = w.cqw(2.6f),
                lineHeight = w.cqw(3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub != null) {
                Text(
                    sub,
                    color = Color(0xFF9AA5C4),
                    fontSize = w.cqw(1.8f),
                    lineHeight = w.cqw(2.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
