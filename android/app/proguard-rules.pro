# ResearchRadar Production ProGuard / R8 Rules

# -----------------------------------------------------------------------------
# Kotlin Serialization
# -----------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.researchradar.core.model.** { *; }
-keepclassmembers class com.researchradar.core.model.** {
    *** Companion;
    *** serializer(...);
}

# -----------------------------------------------------------------------------
# Retrofit 2 & OkHttp 3
# -----------------------------------------------------------------------------
-dontnote retrofit2.Platform
-dontnote retrofit2.Platform$Java8
-keepattributes EnclosingMethod
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class com.researchradar.core.network.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# -----------------------------------------------------------------------------
# Room Persistence Library
# -----------------------------------------------------------------------------
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# Hilt / Dagger
# -----------------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class **.*_HiltModules* { *; }
-keep class dagger.hilt.** { *; }

# -----------------------------------------------------------------------------
# Jetpack Compose
# -----------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
