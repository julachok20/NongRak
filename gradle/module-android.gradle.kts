import java.util.Properties

/** Shared Android/Kotlin settings for NongRak library modules. */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

extra["nongrakCompileSdk"] = 36
extra["nongrakMinSdk"] = 26
extra["nongrakLocalProps"] = localProps
