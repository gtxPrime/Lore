# Lore Sanctuary ProGuard / R8 Rules

# ── Obfuscation Tuning ──
-repackageclasses 'com.gxdevs.lore.obf'
-allowaccessmodification
-repackageclasses

# ── Kotlin & Coroutines ──
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.coroutines.**

# ── AndroidX Room ──
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ── Gson & Data Models ──
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.gxdevs.lore.data.journal.** { *; }
-keep class com.gxdevs.lore.data.relic.** { *; }
-keep class com.gxdevs.lore.data.mood.** { *; }
-keep class com.gxdevs.lore.pets.PetCatalogModels** { *; }
-keep class com.gxdevs.lore.ui.journal.Emotion { *; }
-keep class com.gxdevs.lore.ui.journal.AttachedFile { *; }
-keep class com.gxdevs.lore.ui.journal.FileType { *; }

# ── WorkManager ──
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.gxdevs.lore.utils.DriveBackupWorker { *; }
-keep class com.gxdevs.lore.pets.PetCatalogSyncWorker { *; }

# ── Google Play Billing & Auth ──
-keep class com.android.billingclient.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class androidx.credentials.** { *; }

# ── Glide & Media3 ──
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn androidx.media3.**

# ── ML Kit & TFLite ──
-keep class com.google.mlkit.** { *; }
-keep class org.tensorflow.lite.** { *; }