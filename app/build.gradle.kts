import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.lullabyai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.publish.NongRak"
        minSdk = 26
        targetSdk = 36
        // Next after the newest artifact in Releases/ (v1.0.9, vc26). This file had been
        // left on vc16/1.0.3-dev36 while five releases shipped from elsewhere, so read Releases/
        // rather than this line when picking the next number.
        versionCode = 28
        versionName = "1.0.1"
        testInstrumentationRunner = "com.lullabyai.app.NongRakTestRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProps["SUPABASE_URL"]}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps["SUPABASE_ANON_KEY"]}\"")
        // NOTE: Gemini/Claude API keys are NO LONGER shipped in the app. They live as
        // Supabase secrets, used only by the "ai-generate" Edge Function. See PRIVACY_SECURITY.md.
        buildConfigField("String", "MEDGEMMA_ENDPOINT", "\"${localProps["MEDGEMMA_ENDPOINT"] ?: ""}\"")
        buildConfigField("String", "MEDGEMMA_TOKEN", "\"${localProps["MEDGEMMA_TOKEN"] ?: ""}\"")
        buildConfigField("String", "MEDGEMMA_MODEL", "\"${localProps["MEDGEMMA_MODEL"] ?: ""}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProps["GOOGLE_WEB_CLIENT_ID"]}\"")
        // hCaptcha SITE key only (public by design) — the secret key lives solely in Supabase's
        // Auth "Bot and Abuse Protection" setting, never in the app.
        buildConfigField("String", "HCAPTCHA_SITE_KEY", "\"${localProps["HCAPTCHA_SITE_KEY"] ?: ""}\"")
        // Google Places (New), for nearby place suggestions on the activity schedule. Defaults to
        // empty so a checkout without the key still builds — the feature reports itself as
        // unconfigured rather than failing the build.
        buildConfigField("String", "MAPS_API_KEY", "\"${localProps["MAPS_API_KEY"] ?: ""}\"")
        // LINE OA basic id, e.g. "@nongrak" — used to build the chat deep link. Blank hides
        // the LINE features rather than shipping a link that opens nothing.
        buildConfigField("String", "LINE_OA_ID", "\"${localProps["LINE_OA_ID"] ?: ""}\"")
        // LINE Login channel id. MUST live in the same LINE Provider as the Messaging channel,
        // otherwise the userId from login never matches the one the OA sees and notifications
        // silently go nowhere.
        buildConfigField("String", "LINE_LOGIN_CHANNEL_ID", "\"${localProps["LINE_LOGIN_CHANNEL_ID"] ?: ""}\"")

        manifestPlaceholders["supabaseRedirectScheme"] = "lullabyai"
    }

    // Release signing — keystore details come from local.properties (kept OUT of git), e.g.:
    //   RELEASE_STORE_FILE=keystore/lullabyai-release.jks   (path relative to project root)
    //   RELEASE_STORE_PASSWORD=...
    //   RELEASE_KEY_ALIAS=lullabyai
    //   RELEASE_KEY_PASSWORD=...
    val hasReleaseKeystore = localProps["RELEASE_STORE_FILE"] != null
    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(localProps["RELEASE_STORE_FILE"] as String)
                storePassword = localProps["RELEASE_STORE_PASSWORD"] as String?
                keyAlias = localProps["RELEASE_KEY_ALIAS"] as String?
                keyPassword = localProps["RELEASE_KEY_PASSWORD"] as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign the release APK only when a keystore is configured; otherwise it builds unsigned.
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:navigation"))
    api(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.mlkit.barcode.scanning)
    // Real Guava. androidx.health.connect forced listenablefuture to the empty
    // "avoid-conflict-with-guava" placeholder on the compile classpath without pulling in real
    // Guava to compensate, which hid ListenableFuture - CameraX's ProcessCameraProvider return
    // type - at compile time. Health Connect has since been removed, so this may no longer be
    // needed; CameraX (including the video recorder) still depends on ListenableFuture resolving,
    // so verify the camera builds AND opens before dropping it.
    implementation("com.google.guava:guava:33.3.1-android")

    // Supabase
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.linesdk)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)

    // Ktor engine — OkHttp required for Supabase Realtime WebSockets (Android engine has no WS).
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.core)

    // Ktor JSON serialization for Claude/Anthropic REST API
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Video export pipeline. Media3 Transformer is the ONLY encoder in this project — without it
    // the watermark and outro cannot reach an exported .mp4, which is what the previous
    // implementation silently failed to do (it byte-copied the source and reported success).
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)

    // Image loading
    implementation(libs.coil.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Encrypted SharedPreferences (doctor device-binding)
    implementation(libs.androidx.security.crypto)

    // QR generate/decode for Share Access + Sync Child scan (Stitch #5 C)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.google.play.services.auth)

    // Google Sign-In via Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
