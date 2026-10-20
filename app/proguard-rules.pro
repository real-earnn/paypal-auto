# ══════════════════════════════════════════════════════════════════
# 🟢 Real Earn PayPal — R8 Rules
# ══════════════════════════════════════════════════════════════════

# ── App's own classes (keep everything, obfuscate names) ──
-keep class com.real.earn.paypal.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes LineNumberTable,SourceFile

# ── Kadb (ADB client — reflection + native) ──
-keep class com.flyfishxu.kadb.** { *; }
-dontwarn com.flyfishxu.kadb.**

# ── BouncyCastle (Kadb pairing cryptography — reflection) ──
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── spake2-java (Kadb pairing handshake) ──
-keep class com.github.flyfish233.spake2.** { *; }
-dontwarn com.github.flyfish233.spake2.**

# ── jspecify (annotations) ──
-dontwarn org.jspecify.**
-keep class org.jspecify.** { *; }

# ── Retrofit / OkHttp / Gson ──
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.code.gson.** { *; }

# ── Kotlin metadata ──
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ── Enums ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Native methods ──
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Parcelable ──
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ── Suppress warnings ──
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.**
