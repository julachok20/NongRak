package com.lullabyai.app.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.realtime

/**
 * Supabase Realtime requires an explicit WebSocket connect (supabase-kt 3.x) before
 * [io.github.jan.supabase.realtime.RealtimeChannel.subscribe]. Without this, QR sync popups
 * and co-parent data watches never fire even though channels subscribe without error.
 */
suspend fun ensureRealtimeConnected() {
    if (supabase.auth.currentSessionOrNull() == null) return
    runCatching { supabase.realtime.connect() }
}
