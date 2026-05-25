package com.gxdevs.aethra.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gxdevs.aethra.data.journal.JournalEntry
import com.gxdevs.aethra.data.journal.JournalDao
import com.gxdevs.aethra.data.mood.MoodType
import com.gxdevs.aethra.data.mood.MoodDao
import com.gxdevs.aethra.pets.PetProgress
import com.gxdevs.aethra.pets.PetProgressDao
import com.gxdevs.aethra.data.relic.Relic
import com.gxdevs.aethra.data.relic.RelicDao
import com.gxdevs.aethra.pets.CachedStageImage
import com.gxdevs.aethra.pets.CachedStageImageDao
import com.gxdevs.aethra.pets.PetCatalogMeta
import com.gxdevs.aethra.pets.PetCatalogMetaDao
import com.gxdevs.aethra.pets.PetDefinition
import com.gxdevs.aethra.pets.PetDefinitionDao
import com.gxdevs.aethra.pets.PetStageDefinition
import com.gxdevs.aethra.pets.PetStageDefinitionDao

@Database(
    entities = [
        JournalEntry::class,
        MoodType::class,
        CustomEmotion::class,
        PetProgress::class,
        Relic::class,
        // --- Pet Catalog ---
        PetCatalogMeta::class,
        PetDefinition::class,
        PetStageDefinition::class,
        CachedStageImage::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
    abstract fun moodDao(): MoodDao
    abstract fun userAttributesDao(): UserAttributesDao
    abstract fun petProgressDao(): PetProgressDao
    abstract fun relicDao(): RelicDao

    // --- Pet Catalog DAOs ---
    abstract fun petCatalogMetaDao(): PetCatalogMetaDao
    abstract fun petDefinitionDao(): PetDefinitionDao
    abstract fun petStageDefinitionDao(): PetStageDefinitionDao
    abstract fun cachedStageImageDao(): CachedStageImageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "journal_database"
                        )
                            .fallbackToDestructiveMigration(true) // For development simplicity
                            .build()
                    INSTANCE = instance
                    instance
                }
        }
    }
}

