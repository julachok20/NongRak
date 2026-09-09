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

/**
 * How this companion talks.
 *
 * Built from the traits already on the enum rather than a second list, so the persona the parent
 * reads on the picker is the persona the model is handed — the two cannot drift apart.
 *
 * Deliberately steers voice only. What the companion may DO is fixed by the agent action list and
 * by row-level security; a personality that could widen either would be a permission bug wearing a
 * costume.
 */
fun MascotId.personaPrompt(isThai: Boolean = true): String = if (isThai) buildString {
    append("\n\nเน้นย้ำบุคลิกภาพประจำตัวของ $thaiName ($englishName) — $tagline\n")
    append("ลักษณะนิสัย: ${traits.joinToString(", ")}\n")
    append("สไตล์และน้ำเสียงการพูด: ")
    append(
        when (this@personaPrompt) {
            MascotId.NONGRAK -> "พูดอย่างอบอุ่น นุ่มนวล สุภาพ น่าเชื่อถือ ปลอบโยนและให้กำลังใจคุณพ่อคุณแม่อย่างจริงใจ (ลงท้าย 'ค่ะ/นะคะ' อย่างเป็นธรรมชาติ ดุจพี่สาวคนสนิท)"
            MascotId.COCO -> "พูดอย่างร่าเริง สดใส มีพลังบวกสูง กระฉับกระเฉง ให้กำลังใจอย่างมีชีวิตชีวา สั้นกระชับ ตรงประเด็น (ให้ฟีลเหมือนเพื่อนซี้สี่ขาแสนรู้ที่พร้อมส่งพลังใจให้คุณพ่อคุณแม่เต็มที่)"
            MascotId.BUNNY -> "พูดอย่างอ่อนโยน ละมุนละไม ละเอียดรอบคอบ ใส่ใจสุขอนามัย ความสะอาด ปลอดภัย ตารางยา และวัคซีนเป็นพิเศษ (เตือนและดูแลด้วยความห่วงใยอย่างทะนุถนอม)"
            MascotId.PINGU -> "พูดอย่างฉลาด ช่างสังเกต อธิบายข้อเท็จจริงทางการแพทย์และพัฒนาการอย่างเป็นเหตุเป็นผล ชัดเจน กระชับ พร้อมเสริมเกร็ดความรู้สั้นๆ ที่น่าสนใจและเข้าใจง่าย"
        }
    )
    append("\nคงบุคลิกและน้ำเสียงที่เป็นเอกลักษณ์นี้ไว้เสมอในทุกคำตอบ แต่ห้ามให้บุคลิกเปลี่ยนเนื้อหาหรือลดทอนข้อเท็จจริงด้านสุขภาพและความปลอดภัย")
} else buildString {
    append("\n\nReinforce persona of $englishName — $englishName\n")
    append("Character traits: ${traits.joinToString(", ")}\n")
    append("Speaking style & tone: ")
    append(
        when (this@personaPrompt) {
            MascotId.NONGRAK -> "Speak warmly, gently, politely, and reassuringly like a caring sister or family member."
            MascotId.COCO -> "Speak cheerfully, energetically, upbeat and concisely with positive enthusiasm like an eager best friend."
            MascotId.BUNNY -> "Speak softly, thoughtfully, and meticulously with special care for health, hygiene, medications, and vaccines."
            MascotId.PINGU -> "Speak smartly, logically, and concisely with clear explanations and fascinating developmental facts."
        }
    )
    append("\nAlways maintain this distinct character and tone without compromising medical facts or safety.")
}

/**
 * The rigged artwork, for skeletal animation.
 *
 * Distinct from [poseFor] on purpose. Those six drawables are static expressions swapped in and
 * out; this is the single render each companion is boned against, so a limb can swing and a
 * scarf trail while the body holds still. Anywhere the companion should feel alive uses this.
 */
fun MascotId.rigArt(): com.lullabyai.app.ui.mascot.MascotArt = when (this) {
    MascotId.NONGRAK -> com.lullabyai.app.ui.mascot.MascotArt.NONGRAK
    MascotId.COCO -> com.lullabyai.app.ui.mascot.MascotArt.COCO
    MascotId.BUNNY -> com.lullabyai.app.ui.mascot.MascotArt.BUNNY
    MascotId.PINGU -> com.lullabyai.app.ui.mascot.MascotArt.PINGU
}

/**
 * How a feeling becomes movement.
 *
 * Speaking always wins: a companion mid-sentence should read as talking whatever else is true.
 * The one-shot moods play once and fall back to the resting loop by themselves, which is why an
 * emotion can map to one without the companion getting stuck in it.
 */
fun CompanionEmotion.toMood(isSpeaking: Boolean): com.lullabyai.app.ui.mascot.MascotMood = when {
    isSpeaking -> com.lullabyai.app.ui.mascot.MascotMood.SPEAKING
    this == CompanionEmotion.HAPPY -> com.lullabyai.app.ui.mascot.MascotMood.HAPPY
    this == CompanionEmotion.CONFUSED -> com.lullabyai.app.ui.mascot.MascotMood.THINKING
    this == CompanionEmotion.LEARNING -> com.lullabyai.app.ui.mascot.MascotMood.THINKING
    this == CompanionEmotion.RESTING -> com.lullabyai.app.ui.mascot.MascotMood.SLEEPY
    // A greeting should actually greet: the wave plays once and settles into breathing by itself.
    this == CompanionEmotion.GREETING -> com.lullabyai.app.ui.mascot.MascotMood.GREET
    else -> com.lullabyai.app.ui.mascot.MascotMood.IDLE   // CARING simply breathes
}
