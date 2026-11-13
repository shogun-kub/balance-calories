# R8 keep rules for BalanceCalories.
#
# The list is deliberately short. AndroidX, Compose, Room, WorkManager, DataStore and Health Connect all
# ship consumer rules of their own, so repeating their advice here would be cargo cult; what is left is
# the three things that reach R8 through a path consumer rules cannot see: JNI, reflection over model
# names, and the plain-JSON backup format.

# --- zxing-cpp -------------------------------------------------------------------------------------
# The decoder is a native library called through a Java class it looks up by name from C++ (JNI). R8
# cannot see that call, so without this rule the library finds a renamed or stripped class at runtime and
# the scanner fails only on a device, only after a release build - the exact failure that tests miss.
-keep class zxingcpp.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Room ------------------------------------------------------------------------------------------
# Room's generated `_Impl` classes are named from the annotated class and instantiated by name by the
# generated `RoomDatabase` subclass. Room's own consumer rules cover the annotations, not the entity
# fields that a `@ColumnInfo` maps by name in a migration test.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Health Connect --------------------------------------------------------------------------------
# The client library reflects over its record types when it serialises them for the store.
-keep class androidx.health.connect.client.records.** { *; }
-keep class androidx.health.connect.client.impl.** { *; }
-dontwarn androidx.health.connect.client.**

# --- WorkManager -----------------------------------------------------------------------------------
# Our worker is constructed by name from the WorkManager factory.
-keep class com.balancecalories.app.sync.** extends androidx.work.ListenableWorker { <init>(...); }

# --- Debuggability of a shipped crash --------------------------------------------------------------
# Keep the line numbers: a stack trace from a phone is the only evidence there will be, and an
# obfuscated one is a puzzle nobody can solve.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
