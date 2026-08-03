-keep class com.lullabyai.app.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn kotlinx.serialization.**

# Google Sign-In (release builds)
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**
