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
    // coerceInputValues is the difference between one bad column and a blank app.
    //
    // Several models declare non-nullable fields with defaults (Child.gender, Child.birthdate,
    // Child.activityLevel). kotlinx.serialization treats an explicit JSON null on those as a
    // DECODE FAILURE rather than falling back to the default - and because decodeList parses the
    // whole array at once, a single null in a single row throws away every row with it. A child
    // added from the web with no gender made the app report no children at all.
    //
    // Any writer - web, doctor portal, LINE, a manual dashboard edit - can produce that null, so
    // the tolerance belongs here at the one place every read passes through.
    defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            explicitNulls = false
        }
    )
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
