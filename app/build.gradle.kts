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
        versionCode = 16
        versionName = "1.0.3-dev36"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
    implementation(libs.mlkit.barcode.scanning)
    // Real Guava, not just the empty listenablefuture shim CameraX normally relies on —
    // androidx.health.connect:connect-client forces listenablefuture to the empty
    // "avoid-conflict-with-guava" placeholder on the compile classpath without pulling in
    // real Guava to compensate, which makes ListenableFuture (CameraX's ProcessCameraProvider
    // return type) inaccessible at compile time. Adding Guava directly resolves it.
    implementation("com.google.guava:guava:33.3.1-android")

    // Health Connect (Google Fit / Samsung / Fitbit / Garmin bridge on Android)
    implementation(libs.androidx.health.connect)

    // Supabase
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)

    // Ktor engine — OkHttp required for Supabase Realtime WebSockets (Android engine has no WS).
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.core)

    // Ktor JSON serialization for Claude/Anthropic REST API
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

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
