package com.gxdevs.athera

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gxdevs.athera.data.CustomEmotion
import com.gxdevs.athera.data.SavedLocation
import com.gxdevs.athera.data.SavedPerson
import com.gxdevs.athera.data.UserAttributesDao
import com.gxdevs.athera.pets.CachedStageImage
import com.gxdevs.athera.pets.CachedStageImageDao
import com.gxdevs.athera.pets.PetCatalogMeta
import com.gxdevs.athera.pets.PetCatalogMetaDao
import com.gxdevs.athera.pets.PetDefinition
import com.gxdevs.athera.pets.PetDefinitionDao
import com.gxdevs.athera.pets.PetStageDefinition
import com.gxdevs.athera.pets.PetStageDefinitionDao

@Database(
    entities = [
        JournalEntry::class,
        MoodType::class,
        CustomEmotion::class,
        SavedLocation::class,
        SavedPerson::class,
        PetProgress::class,
        Relic::class,
        // â”€â”€ Pet Catalog â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        PetCatalogMeta::class,
        PetDefinition::class,
        PetStageDefinition::class,
        CachedStageImage::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
    abstract fun moodDao(): MoodDao
    abstract fun userAttributesDao(): UserAttributesDao
    abstract fun petProgressDao(): PetProgressDao
    abstract fun relicDao(): RelicDao

    // â”€â”€ Pet Catalog DAOs â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

