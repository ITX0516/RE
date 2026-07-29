# ProGuard / R8 rules for BadukNext

# ---------- Room ----------
# Room uses reflection on Entity classes and the generated Dao implementations.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class androidx.room.RoomSQLiteQuery { *; }
-dontwarn androidx.room.paging.**

# ---------- Kotlin / Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---------- Compose ----------
-keep class androidx.compose.runtime.** { *; }
-keep class * extends androidx.compose.runtime.CompositionLocal

# ---------- DataStore ----------
-keep class androidx.datastore.** { *; }

# ---------- Keep Gson / reflective model classes (if used) ----------
-keepattributes Signature
-keepattributes *Annotation*
