# ══════════════════════════════════════════════════════════════════
# 🟢 Real Earn PayPal — R8 Rules (Fixed for Retrofit/Gson generics)
# ══════════════════════════════════════════════════════════════════

# ── 2. Activities, Services, Receivers, Providers ──
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

# ── 3. Accessibility Service & Device Admin ──
-keep class com.real.earn.paypal.service.PayPalAccessibilityService { *; }
-keep class com.real.earn.paypal.admin.MyDeviceAdminReceiver { *; }

# ── 4. ViewBinding ──
-keep class com.real.earn.paypal.databinding.** { *; }

# ── 5. Data classes (API models) — Gson reflection ──
-keep class com.real.earn.paypal.api.** { *; }
-keepclassmembers class com.real.earn.paypal.api.** {
    <fields>;
    <init>(...);
}

# ── 6. AuthManager (EncryptedSharedPreferences) ──
-keep class com.real.earn.paypal.auth.AuthManager { *; }

# ── 7. Kadb (reflection + native) ──
-keep class com.flyfishxu.kadb.** { *; }
-dontwarn com.flyfishxu.kadb.**

# ── 8. BouncyCastle (cryptography) ──
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── 9. spake2-java ──
-keep class com.github.flyfish233.spake2.** { *; }
-dontwarn com.github.flyfish233.spake2.**

# ── 10. jspecify ──
-dontwarn org.jspecify.**
-keep class org.jspecify.** { *; }

# ══════════════════════════════════════════════════════════════════
# 🟢 CRITICAL: SHIZUKU — Reflection needs method names preserved
# ══════════════════════════════════════════════════════════════════
# Hum `Shizuku.newProcess()` reflection se call karte hain.
# R8 ke bina ye method rename ho jati hai aur reflection fail hota hai.

-keep class rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** {
    <methods>;
    <fields>;
    <init>(...);
}
-dontwarn rikka.shizuku.**

# Shizuku uses reflection internally for AIDL stubs
-keep class moe.shizuku.** { *; }
-dontwarn moe.shizuku.**

# ══════════════════════════════════════════════════════════════════
# 🟢 CRITICAL FIXES for Retrofit / Gson / Kotlin Coroutines
# ══════════════════════════════════════════════════════════════════

-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

-keep,allowobfuscation,allowshrinking class kotlinx.coroutines.flow.Flow
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** {
    <fields>;
}
-dontwarn com.google.gson.**

# ══════════════════════════════════════════════════════════════════
# 🟢 General Rules (Kotlin, Enums, Parcelable, Native)
# ══════════════════════════════════════════════════════════════════

-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
