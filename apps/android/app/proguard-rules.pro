# ---- Legal Guide ----
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable

# Gson models (reflection)
-keep class com.legalguide.app.data.model.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Retrofit service interface
-keep,allowobfuscation,allowshrinking interface com.legalguide.app.core.net.ApiService
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class retrofit2.Call

# WorkManager workers are instantiated by name
-keep class com.legalguide.app.core.notify.** extends androidx.work.Worker { <init>(...); }

-dontwarn org.commonmark.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
