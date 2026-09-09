# ==== NOVA AI Browser R8 rules ====
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Kotlinx serialization
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <methods>; }
-keep,includedescriptorclasses class com.nova.browser.**$$serializer { *; }
-keepclassmembers class com.nova.browser.** {
    *** Companion;
}

# Gson / network models
-keep class com.nova.browser.core.network.models.** { *; }
-keep class com.nova.browser.features.**.model.** { *; }
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-dontwarn dagger.hilt.**

# WebView JavaScript interfaces
-keepclassmembers class com.nova.browser.features.browser.web.NovaJsBridge {
   public *;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Compose
-dontwarn androidx.compose.**

# Media3
-dontwarn androidx.media3.**
