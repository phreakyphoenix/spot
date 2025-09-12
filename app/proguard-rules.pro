# Keep your app's entry points
-keep class com.example.spot.** { *; }

# --- Picovoice / Porcupine ---
# Keep native wrapper classes (Porcupine JNI needs reflection)
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# --- Spotify SDK ---
# Keep Spotify App Remote API classes (uses reflection internally)
-keep class com.spotify.android.appremote.** { *; }
-dontwarn com.spotify.android.appremote.**

# --- AndroidX / Core ---
# Keep annotations (avoid stripping metadata)
-keep class androidx.annotation.** { *; }
-keepattributes *Annotation*

# --- General Safe Defaults ---
# Keep names for anything that might be called by reflection
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes InnerClasses,EnclosingMethod

# Remove all Android Log calls in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# --- Fix for Spotify SDK Jackson dependencies ---
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn com.fasterxml.jackson.core.**
-dontwarn com.fasterxml.jackson.annotation.**

-keep class com.fasterxml.jackson.** { *; }

# --- javax.annotation (used for @Nullable, etc.) ---
-dontwarn javax.annotation.**
-keep class javax.annotation.** { *; }