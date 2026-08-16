package com.lullabyai.app.ui.mascot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/**
 * One frame of a mascot: where the whole body sits, and where each bone points.
 *
 * Offsets are fractions of the drawn size rather than pixels, so a mood looks the same at a 40dp
 * chat avatar as on a full-screen illustration.
 */
data class MascotPose(
    val dx: Float = 0f,
    val dy: Float = 0f,
    /** Whole-body tilt, degrees. */
    val rot: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    /** Bone angles in degrees. Negative swings a raised arm down toward the body. */
    val arm: Float = 0f,
    val armFar: Float = 0f,
    val trail: Float = 0f
)

/**
 * What the mascot is doing.
 *
 * Two rules run through all of them:
 *
 * *Arms swing down, not up.* Every mascot is drawn with a limb already raised, so lifting further
 * reads as a stretch rather than a wave. The useful range is about -30° (down toward the body) to
 * +8° (a little past the rest pose).
 *
 * *The trailing part is always late.* Scarf, ear and cape use the same wave as the body shifted
 * by a phase offset, so they arrive after the motion that caused them. That lag is what separates
 * something alive from a picture being scaled.
 */
enum class MascotMood(val durationMs: Int, val loops: Boolean) {

    /** Resting. Barely there — it must survive being on screen for minutes. */
    IDLE(3200, true) {
        override fun poseAt(t: Float): MascotPose {
            val w = sin(TAU * t)
            return MascotPose(
                dy = -0.018f * w,
                sy = 1f + 0.015f * w,
                sx = 1f - 0.012f * w,
                arm = -5f * sin(TAU * t - 0.5f),
                trail = 4f * sin(TAU * t - 1.0f)
            )
        }
    },

    /** Talking cadence: quick shallow nods at roughly the rhythm of speech. */
    SPEAKING(760, true) {
        override fun poseAt(t: Float): MascotPose {
            val p = TAU * t * 2f
            return MascotPose(
                dy = -0.010f * (0.5f - 0.5f * cos(p)),
                rot = 1.2f * sin(p),
                sy = 1f + 0.010f * sin(p),
                arm = -3f * sin(p - 0.4f),
                trail = 3f * sin(p - 0.9f)
            )
        }
    },

    /** Working on it: a slow lean side to side, arm held slightly down. */
    THINKING(1700, true) {
        override fun poseAt(t: Float): MascotPose = MascotPose(
            dy = -0.012f * abs(sin(PI.toFloat() * t * 2f)),
            rot = 3.2f * sin(TAU * t),
            arm = -9f + 5f * sin(TAU * t - 0.5f),
            trail = 5f * sin(TAU * t - 1.1f)
        )
    },

    /** Restless — wants something now. Faster and less even than Thinking. */
    HUNGRY(1050, true) {
        override fun poseAt(t: Float): MascotPose {
            val p = TAU * t * 2f
            return MascotPose(
                dy = -0.012f * abs(sin(TAU * t)),
                rot = 3.5f * sin(p),
                arm = -14f * (0.5f - 0.5f * cos(p)),
                trail = 6f * sin(p - 0.8f)
            )
        }
    },

    /** Drowsy: long deep breaths, sunk down, arm drooping. */
    SLEEPY(4200, true) {
        override fun poseAt(t: Float): MascotPose {
            val w = sin(TAU * t)
            return MascotPose(
                dy = 0.012f + 0.014f * w,
                rot = 1.5f * w,
                sy = 1f - 0.035f * w,
                sx = 1f + 0.028f * w,
                arm = -22f + 4f * sin(TAU * t - 0.6f),
                trail = 6f * sin(TAU * t - 1.2f)
            )
        }
    },

    /** Sorry: sunk and spread, and it stays that way rather than perking back up. */
    SAD(2600, true) {
        override fun poseAt(t: Float): MascotPose = MascotPose(
            dy = 0.022f,
            rot = -2.2f + 0.8f * sin(TAU * t),
            sy = 0.965f,
            sx = 1.03f,
            arm = -27f + 2f * sin(TAU * t - 0.5f),
            trail = 3f * sin(TAU * t - 1.0f)
        )
    },

    /** The beat when an answer lands: squash, spring, settle. */
    REPLY(720, false) {
        override fun poseAt(t: Float): MascotPose {
            val decay = 1f - t
            return MascotPose(
                dy = -0.10f * sin(PI.toFloat() * t),
                sy = 1f + 0.10f * sin(TAU * t) * decay,
                sx = 1f - 0.08f * sin(TAU * t) * decay,
                arm = -18f * sin(PI.toFloat() * t),
                trail = 10f * sin(TAU * t - 0.6f) * decay
            )
        }
    },

    /** Delight: two hops, the second smaller, because energy drains. */
    HAPPY(950, false) {
        override fun poseAt(t: Float): MascotPose {
            val hop = abs(sin(PI.toFloat() * t * 2f))
            val decay = 1f - 0.6f * t
            return MascotPose(
                dy = -0.13f * hop * decay,
                rot = 5f * sin(TAU * t),
                sy = 1f + 0.06f * cos(TAU * t * 2f) * decay,
                sx = 1f - 0.05f * cos(TAU * t * 2f) * decay,
                arm = 4f - 24f * sin(PI.toFloat() * t * 2f),
                trail = 12f * sin(PI.toFloat() * t * 2f - 0.7f)
            )
        }
    },

    /**
     * A wave.
     *
     * The arm starts a little above rest, swings down to about -30°, comes back up, and settles.
     * The envelope decays to zero so it ends exactly at the rest pose with no snap.
     */
    GREET(1300, false) {
        override fun poseAt(t: Float): MascotPose {
            val envelope = 1f - t * t
            return MascotPose(
                dy = -0.03f * abs(sin(PI.toFloat() * t)),
                rot = -3f * sin(TAU * t * 1.5f) * (1f - t),
                arm = (-14f + 22f * cos(TAU * 1.5f * t)) * envelope,
                trail = 14f * sin(TAU * 1.5f * t - 0.8f) * envelope
            )
        }
    },

    /** Notification: two sharp hops, meant to catch the eye rather than charm. */
    ALERT(620, false) {
        override fun poseAt(t: Float): MascotPose {
            val decay = 1f - 0.5f * t
            return MascotPose(
                dy = -0.16f * abs(sin(PI.toFloat() * t * 2f)) * decay,
                sy = 1f + 0.08f * cos(TAU * t * 2f) * (1f - t),
                sx = 1f - 0.06f * cos(TAU * t * 2f) * (1f - t),
                arm = 8f * sin(PI.toFloat() * t * 2f),
                trail = 10f * sin(PI.toFloat() * t * 2f - 0.6f)
            )
        }
    },

    /** Touched: an immediate squash that springs out, like something soft being pressed. */
    TAP(420, false) {
        override fun poseAt(t: Float): MascotPose {
            // Starts at full squash on frame one — a tap that eases in feels unresponsive.
            val spring = exp(-4f * t) * cos(TAU * 1.2f * t)
            return MascotPose(
                dy = 0.03f * spring,
                sy = 1f - 0.18f * spring,
                sx = 1f + 0.16f * spring,
                arm = -12f * spring,
                trail = 10f * exp(-4f * t) * sin(TAU * 1.2f * t)
            )
        }
    };

    /** @param t progress through this mood, 0..1. */
    abstract fun poseAt(t: Float): MascotPose
}
