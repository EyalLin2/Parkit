# R8 rules for ParkIt release builds. Retrofit, OkHttp, Moshi, Coil, MapLibre,
# Compose and Play Services ship their own consumer rules; these cover what
# is ours.

# Network models + Retrofit interfaces (backend and geocoding). Moshi's
# KotlinJsonAdapterFactory reads constructors and property names by
# reflection, so these classes keep their names and members.
-keep class com.parkit.app.api.** { *; }
-keepclassmembers class com.parkit.app.api.** { <init>(...); }

# Kotlin metadata is what KotlinJsonAdapterFactory reflects on.
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,Signature,InnerClasses,EnclosingMethod,AnnotationDefault
-dontwarn kotlin.reflect.jvm.internal.**

# Keep line numbers in crash reports (Play de-obfuscates with the mapping
# file bundled in the AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
