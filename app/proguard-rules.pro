# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.GeneratedComponent

# Keep Room entities
-keep class com.nfcmanager.app.data.local.** { *; }

# Keep serialized data models
-keep class com.nfcmanager.app.domain.model.** { *; }

# Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# NFC classes
-keep class android.nfc.** { *; }
