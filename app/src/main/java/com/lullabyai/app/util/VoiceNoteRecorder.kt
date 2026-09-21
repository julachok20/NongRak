package com.lullabyai.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * A short voice note for a parent: AAC in an .m4a, the format every phone plays back without help.
 *
 * Deliberately not [VoiceTurnRecorder] — that one records raw PCM with silence detection for a
 * conversation turn with the AI, which is the wrong tool for a message someone plays later.
 *
 * Capped at [MAX_SECONDS]; the database rejects anything longer, so the cap lives in both places.
 */
class VoiceNoteRecorder(private val context: Context) {

    data class Note(val bytes: ByteArray, val seconds: Int)

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** Starts recording. Throws if the microphone cannot be opened, so the caller can say why. */
    fun start() {
        check(recorder == null) { "already recording" }
        val out = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setMaxDuration(MAX_SECONDS * 1000)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            out.delete()
            throw e
        }
        recorder = r
        file = out
        startedAt = System.currentTimeMillis()
    }

    /**
     * Stops and returns the note, or null when it was too short to be a message (under a second —
     * almost always an accidental tap). The temp file is removed either way.
     */
    fun stop(): Note? {
        val r = recorder ?: return null
        val out = file
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtMost(MAX_SECONDS)
        recorder = null
        file = null
        val stoppedCleanly = runCatching { r.stop() }.isSuccess   // throws if nothing was captured
        r.release()
        return try {
            if (!stoppedCleanly || seconds < 1 || out == null || !out.exists()) null
            else Note(out.readBytes(), seconds)
        } finally {
            out?.delete()
        }
    }

    /** Throws away a recording in progress (screen left, cancel tapped). */
    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
        file?.delete()
        file = null
    }

    companion object {
        const val MAX_SECONDS = 120
    }
}
