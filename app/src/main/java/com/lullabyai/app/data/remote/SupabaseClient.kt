package com.lullabyai.app.data.remote

import com.lullabyai.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY
) {
    install(Auth) {
        // Deep link config so Google OAuth returns to the app: lullabyai://auth
        scheme = "lullabyai"
        host = "auth"
    }
    install(Postgrest)
    install(Storage)
    // Realtime powers the owner's live approve/deny popup for child access requests
    // (ChildRepository.watchPendingSyncRequests). No FCM — the app must be open to get the popup.
    install(Realtime)
}
