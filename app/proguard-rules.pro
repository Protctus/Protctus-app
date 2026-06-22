
# Target: Android 14+ (API 35), R8 Full Mode, Aggressive Optimization
# Threat Model: APK decompilation, class-level reverse engineering,
#               string extraction, and dynamic instrumentation (Frida/Xposed)

# ── 0. HILT / DAGGER DEPENDENCY INJECTION ──
# Keep Hilt-generated components and entry points
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclassmembers class * {
    @dagger.Provides *;
}


# ── 1. OBFUSCATION DICTIONARY ──
# Maps class/field/method names to visually confusing Il1 sequences
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary obfuscation-dictionary.txt
-packageobfuscationdictionary obfuscation-dictionary.txt

# ── 2. AGGRESSIVE OPTIMIZATION ──
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses 'p'
-optimizationpasses 5
-overloadaggressively

# ── 3. STRIP DEBUG LOGGING (Prevent String Leakage) ──
# Removes Log.d(), Log.v(), Log.i() calls entirely from release bytecode
# Log.w() and Log.e() are preserved for crash diagnostics
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# ── 4. FIREBASE ──
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── 5. ROOM DATABASE ──
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# ── 6. OKHTTP / RETROFIT ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# ── 7. TENSORFLOW LITE (Protect ML Models) ──
# Keep the TFLite runtime but aggressively obfuscate OUR wrapper code
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
# Protect PhishingDetector's native model loading interface
-keepclassmembers class com.protctus.edge.phishing.PhishingDetector {
    <init>(...);
    boolean isPhishing(java.lang.String);
    void close();
}

# ── 8. KOTLIN SERIALIZATION (Navigation Routes) ──
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static *$$serializer *;
    static kotlinx.serialization.KSerializer serializer(...);
}
-keep class * implements kotlinx.serialization.KSerializer { *; }

# ── 9. COMPOSE NAVIGATION TYPED ROUTES ──
-keep class com.protctus.app.navigation.Route { *; }
-keep class com.protctus.app.navigation.Route$* { *; }

# ── 10. GSON (Reflection-based JSON parsing) ──
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature
-dontwarn com.google.gson.**

# ── 11. DATA CLASSES (JSON / Firestore deserialization) ──
-keep class com.protctus.app.network.DarkWebScanResult { *; }
-keep class com.protctus.app.network.BreachDetail { *; }
-keep class com.protctus.app.network.ScanRequest { *; }
-keep class com.protctus.app.network.ScanResponse { *; }
-keep class com.protctus.app.viewmodel.PremiumPlan { *; }
-keep class com.protctus.app.viewmodel.LabChapterData { *; }
-keep class com.protctus.app.viewmodel.LabTopicData { *; }
-keep class com.protctus.app.viewmodel.ThreatSummaryMetrics { *; }
-keep class com.protctus.app.viewmodel.LeaderboardUser { *; }
-keep class com.protctus.app.viewmodel.DailyQuest { *; }
-keep class com.protctus.app.viewmodel.XpGainEvent { *; }
-keep class com.protctus.app.viewmodel.SmartScanThreat { *; }
-keep class com.protctus.app.utils.LevelData { *; }

# ── 12. ROOM ENTITIES (Field names must survive for SQL column mapping) ──
-keep class com.protctus.app.data.local.** { *; }
-keep class com.protctus.app.domain.model.** { *; }

# ── 13. SECURITY-CRITICAL ENGINES ──
# Keep the Bloom Filter's native data structures intact
-keepclassmembers class com.protctus.app.engine.SignatureDatabase {
    void initialize(android.content.Context);
    boolean verifySigsAgainstBloomFilter(com.protctus.app.engine.FileHashes);
}
# Keep IntegrityShield's state flows accessible from Compose
-keep class com.protctus.app.security.IntegrityShield { *; }

# ── 14. PLAY INTEGRITY API ──
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.android.play.core.integrity.**

# ── 15. ZXING QR ──
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ── 16. RAZORPAY SDK ──
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**
-dontwarn proguard.annotation.**
# Razorpay Payment Data Classes (GSON serialization)
-keep class com.protctus.app.data.repository.CreateOrderRequest { *; }
-keep class com.protctus.app.data.repository.CreateOrderResponse { *; }
-keep class com.protctus.app.data.repository.VerifyPaymentRequest { *; }
-keep class com.protctus.app.data.repository.VerifyPaymentResponse { *; }

# ── 17. COROUTINES & KOTLIN ──
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.reflect.** { *; }

# ── 18. WEBVIEW (Cybermap WebGL) ──
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**

# ── 19. STACK TRACE PRESERVATION ──
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes Exceptions
-renamesourcefileattribute SourceFile

# Keep all ViewModels to prevent Hilt injection issues in release
-keep class com.protctus.app.viewmodel.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { public <init>(...); }
-keep public class * extends androidx.lifecycle.ViewModel { *; }
