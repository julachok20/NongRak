package com.lullabyai.app.data.remote

import com.lullabyai.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
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
}
