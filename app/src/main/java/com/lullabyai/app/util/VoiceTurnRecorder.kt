package com.lullabyai.app.util

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Captures one spoken turn and hands back a WAV.
 *
 * Replaces the speech-to-text round trip on the call screen. Android's recogniser gave us its
 * guess at the words, so the model never heard what it got wrong — and Thai is exactly where it
 * gets things wrong. Sending the audio itself lets the model hear the sentence, including the
 * parts a transcriber would have mangled.
 *
 * Voice activity is decided from frame energy rather than a library: a phone call needs "is
 * someone talking right now", not phoneme detection, and the noise floor of a room with a baby in
 * it moves too much for a fixed threshold.
 */
class VoiceTurnRecorder {

    companion object {
        /** Gemini takes this happily and it is a quarter the size of 44.1kHz for speech. */
        const val SAMPLE_RATE = 16_000
        const val MIME = "audio/wav"

        /** 20ms of audio. Short enough to notice speech starting, long enough for stable energy. */
        private const val FRAME_SAMPLES = SAMPLE_RATE / 50

        /** Speech must clear the room's own noise by this much. */
        private const val SPEECH_MARGIN = 2.2f

        /** Below this nothing is speech, however quiet the room — stops silence self-calibrating. */
        private const val ABSOLUTE_FLOOR = 550f

        /** A turn shorter than this is a cough or a door, not a question. */
        private const val MIN_SPEECH_MS = 350

        /** Hard stop, so a noisy room cannot record forever. */
        private const val MAX_TURN_MS = 30_000
    }

    @Volatile
    var isRecording = false
        private set

    /** True while the parent is actually talking — drives barge-in and the listening indicator. */
    @Volatile
    var isSpeaking = false
        private set

    private var record: AudioRecord? = null

    /** Rolling estimate of the room, so a fan or a television does not read as speech. */
    private var noiseFloor = ABSOLUTE_FLOOR

    /**
     * Records until [silenceMs] of quiet follows real speech, then returns a WAV.
     *
     * Returns null when nothing worth sending was captured — silence, or a noise too short to be a
     * sentence. Callers treat that as "carry on listening", not as an error.
     *
     * Blocking: call from a background dispatcher.
     */
    @SuppressLint("MissingPermission")
    fun recordTurn(silenceMs: Long, shouldStop: () -> Boolean = { false }): ByteArray? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_SAMPLES * 4)

        val recorder = runCatching {
            AudioRecord(
                // VOICE_RECOGNITION applies the platform's own noise suppression and skips the
                // AGC that makes MIC swing wildly between a whisper and a cry.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer
            )
        }.getOrNull() ?: return null

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }

        record = recorder
        isRecording = true
        val pcm = ByteArrayOutputStream()
        val frame = ShortArray(FRAME_SAMPLES)

        return try {
            recorder.startRecording()
            var heardSpeech = false
            var speechMs = 0
            var quietMs = 0
            var totalMs = 0

            while (!shouldStop()) {
                val read = recorder.read(frame, 0, frame.size)
                if (read <= 0) break

                val level = rms(frame, read)
                val speaking = level > maxOf(noiseFloor * SPEECH_MARGIN, ABSOLUTE_FLOOR)
                isSpeaking = speaking

                // Only silence updates the floor, or a long sentence would train the room's noise
                // estimate up to the speaker's own voice and stop hearing them.
                if (!speaking) noiseFloor = noiseFloor * 0.95f + level * 0.05f

                val frameMs = read * 1000 / SAMPLE_RATE
                totalMs += frameMs

                if (speaking) {
                    heardSpeech = true
                    speechMs += frameMs
                    quietMs = 0
                } else if (heardSpeech) {
                    quietMs += frameMs
                }

                // Keep everything once speech has begun, including the quiet between words —
                // clipping the gaps is what makes recorded speech sound chopped to a model.
                if (heardSpeech) {
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        bytes[i * 2] = (frame[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((frame[i].toInt() shr 8) and 0xFF).toByte()
                    }
                    pcm.write(bytes)
                }

                if (heardSpeech && quietMs >= silenceMs) break
                if (totalMs >= MAX_TURN_MS) break
            }

            if (!heardSpeech || speechMs < MIN_SPEECH_MS) null else wav(pcm.toByteArray())
        } catch (t: Throwable) {
            null
        } finally {
            isSpeaking = false
            isRecording = false
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
            record = null
        }
    }

    /** Ends the current turn early — used when the parent hangs up or mutes. */
    fun stop() {
        runCatching { record?.stop() }
    }

    private fun rms(frame: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) {
            val v = frame[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / count).toFloat()
    }

    /**
     * Wraps PCM in a WAV header.
     *
     * Gemini will take raw PCM only if told the exact rate and encoding; a WAV carries that in the
     * file itself, so there is one fewer thing to keep in sync across the app, the edge function
     * and the model.
     */
    private fun wav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        val byteRate = SAMPLE_RATE * 2
        fun i32(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
        )
        fun i16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

        out.write("RIFF".toByteArray())
        out.write(i32(36 + pcm.size))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(i32(16))          // PCM header size
        out.write(i16(1))           // PCM, uncompressed
        out.write(i16(1))           // mono
        out.write(i32(SAMPLE_RATE))
        out.write(i32(byteRate))
        out.write(i16(2))           // block align
        out.write(i16(16))          // bits per sample
        out.write("data".toByteArray())
        out.write(i32(pcm.size))
        out.write(pcm)
        return out.toByteArray()
    }
}
