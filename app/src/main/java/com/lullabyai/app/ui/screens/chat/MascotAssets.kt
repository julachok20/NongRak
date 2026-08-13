package com.lullabyai.app.ui.screens.chat

import androidx.annotation.DrawableRes
import com.lullabyai.app.R

/**
 * The four companions the parent can pick from, and the poses each one can show.
 *
 * Art lives in res/drawable as `<mascot>_mascot_<emotion>.png`. The mapping is written out
 * explicitly instead of resolved by name at runtime: getIdentifier() defeats R8 resource
 * shrinking, and a missing pose would then fail silently on a user's phone instead of at build.
 */
enum class MascotId(
    val assetPrefix: String,
    val thaiName: String,
    val englishName: String,
    /** One-line persona shown under the name on the picker. */
    val tagline: String,
    /** Three trait chips, in the order the roster poster shows them. */
    val traits: List<String>
) {
    NONGRAK(
        "nongrak", "น้องรัก", "NongRak",
        "เพื่อนคู่ใจที่คอยดูแลลูกน้อยไปกับคุณ",
        listOf("อบอุ่น", "ใส่ใจ", "เชื่อใจได้")
    ),
    COCO(
        "coco", "โคโค่", "Coco",
        "หมาน้อยจอมซน คอยเป็นกำลังใจทุกวัน",
        listOf("ร่าเริง", "ซื่อสัตย์", "เป็นมิตร")
    ),
    BUNNY(
        "bunny", "บันนี่", "Bunny",
        "กระต่ายรักสุขภาพ ไม่ลืมเตือนเรื่องยาและวัคซีน",
        listOf("อ่อนโยน", "ใส่ใจสุขภาพ", "ขยัน")
    ),
    PINGU(
        "pingu", "พิงกุ", "Pingu",
        "เพนกวินช่างสงสัย ช่วยหาข้อมูลให้ทุกเรื่อง",
        listOf("ฉลาด", "ช่างสังเกต", "ช่วยหาข้อมูล")
    );

    companion object {
        val DEFAULT = NONGRAK

        /** Tolerant lookup for a stored preference; unknown ids fall back instead of crashing. */
        fun fromKey(key: String?): MascotId =
            entries.firstOrNull { it.assetPrefix == key } ?: DEFAULT
    }
}

/**
 * What the companion is feeling right now. Drives which pose is on screen.
 *
 * Named after the existing drawables (CONFUSED, not "thinking") so the art files and the enum
 * never drift apart.
 */
enum class CompanionEmotion {
    /** A record was saved, or good news. */
    HAPPY,

    /** A health reminder is due — medicine, vaccine, appointment. */
    CARING,

    /** The AI needs one more detail before it can log something. */
    CONFUSED,

    /** Opening greeting when nothing needs attention. */
    GREETING,

    /** Night hours / sleep mode. */
    RESTING,

    /** Story mode and tips. */
    LEARNING
}

/**
 * The pose used while the AI is speaking.
 *
 * น้องรัก has a dedicated hero pose that [AiAvatarView] paints an animated mouth and eyes onto,
 * using coordinates measured from that one image. The other three have no hero art, so they use
 * their greeting pose — eyes open, mouth visible, which is the closest match.
 */
@DrawableRes
fun MascotId.heroPose(): Int = when (this) {
    MascotId.NONGRAK -> R.drawable.nongrak_mascot_hero
    else -> poseFor(CompanionEmotion.GREETING)
}

@DrawableRes
fun MascotId.poseFor(emotion: CompanionEmotion): Int = when (this) {
    MascotId.NONGRAK -> when (emotion) {
        CompanionEmotion.HAPPY -> R.drawable.nongrak_mascot_happy
        CompanionEmotion.CARING -> R.drawable.nongrak_mascot_caring
        CompanionEmotion.CONFUSED -> R.drawable.nongrak_mascot_confused
        CompanionEmotion.GREETING -> R.drawable.nongrak_mascot_greeting
        CompanionEmotion.RESTING -> R.drawable.nongrak_mascot_resting
        CompanionEmotion.LEARNING -> R.drawable.nongrak_mascot_learning
    }
    MascotId.COCO -> when (emotion) {
        CompanionEmotion.HAPPY -> R.drawable.coco_mascot_happy
        CompanionEmotion.CARING -> R.drawable.coco_mascot_caring
        CompanionEmotion.CONFUSED -> R.drawable.coco_mascot_confused
        CompanionEmotion.GREETING -> R.drawable.coco_mascot_greeting
        CompanionEmotion.RESTING -> R.drawable.coco_mascot_resting
        CompanionEmotion.LEARNING -> R.drawable.coco_mascot_learning
    }
    MascotId.BUNNY -> when (emotion) {
        CompanionEmotion.HAPPY -> R.drawable.bunny_mascot_happy
        CompanionEmotion.CARING -> R.drawable.bunny_mascot_caring
        CompanionEmotion.CONFUSED -> R.drawable.bunny_mascot_confused
        CompanionEmotion.GREETING -> R.drawable.bunny_mascot_greeting
        CompanionEmotion.RESTING -> R.drawable.bunny_mascot_resting
        CompanionEmotion.LEARNING -> R.drawable.bunny_mascot_learning
    }
    MascotId.PINGU -> when (emotion) {
        CompanionEmotion.HAPPY -> R.drawable.pingu_mascot_happy
        CompanionEmotion.CARING -> R.drawable.pingu_mascot_caring
        CompanionEmotion.CONFUSED -> R.drawable.pingu_mascot_confused
        CompanionEmotion.GREETING -> R.drawable.pingu_mascot_greeting
        CompanionEmotion.RESTING -> R.drawable.pingu_mascot_resting
        CompanionEmotion.LEARNING -> R.drawable.pingu_mascot_learning
    }
}

/**
 * Only น้องรัก's hero art has measured mouth/eye coordinates, so only น้องรัก can wear the
 * painted-on talking face. The others animate by breathing and pose changes alone.
 *
 * ponytail: measure per-mascot face coordinates and drop this if the other three ever get
 * hero poses of their own.
 */
fun MascotId.supportsPaintedFace(): Boolean = this == MascotId.NONGRAK
