# Project specific ProGuard rules for ClusterRadio

# Preserve source file and line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AndroidX Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.media3.session.MediaSessionService {
    public <init>();
}
-keep class * extends androidx.media3.session.MediaSession$Callback { *; }

# AndroidX DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ClusterRadio Models & Services
-keep class com.joaohouto.clusterradio.data.model.** { *; }
-keep public class com.joaohouto.clusterradio.ClusterRadioApplication
-keep public class com.joaohouto.clusterradio.service.RadioPlaybackService
-keep public class com.joaohouto.clusterradio.service.AutoMediaButtonReceiver