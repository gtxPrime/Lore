package com.gxdevs.lore.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gxdevs.lore.data.journal.JournalEntry
import com.gxdevs.lore.data.journal.JournalDao
import com.gxdevs.lore.data.mood.MoodType
import com.gxdevs.lore.data.mood.MoodDao
import com.gxdevs.lore.data.mood.MoodWordWeight
import com.gxdevs.lore.data.mood.MoodWordWeightDao
import com.gxdevs.lore.pets.PetProgress
import com.gxdevs.lore.pets.PetProgressDao
import com.gxdevs.lore.data.relic.Relic
import com.gxdevs.lore.data.relic.RelicDao
import com.gxdevs.lore.pets.CachedStageImage
import com.gxdevs.lore.pets.CachedStageImageDao
import com.gxdevs.lore.pets.PetCatalogMeta
import com.gxdevs.lore.pets.PetCatalogMetaDao
import com.gxdevs.lore.pets.PetDefinition
import com.gxdevs.lore.pets.PetDefinitionDao
import com.gxdevs.lore.pets.PetStageDefinition
import com.gxdevs.lore.pets.PetStageDefinitionDao

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
        CachedStageImage::class,
        // --- Mood AI ---
        MoodWordWeight::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
    abstract fun moodDao(): MoodDao
    abstract fun userAttributesDao(): UserAttributesDao
    abstract fun petProgressDao(): PetProgressDao
    abstract fun relicDao(): RelicDao
    abstract fun moodWordWeightDao(): MoodWordWeightDao

    // --- Pet Catalog DAOs ---
    abstract fun petCatalogMetaDao(): PetCatalogMetaDao
    abstract fun petDefinitionDao(): PetDefinitionDao
    abstract fun petStageDefinitionDao(): PetStageDefinitionDao
    abstract fun cachedStageImageDao(): CachedStageImageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * Migration 8 → 9: Adds 4 AI mood fields to journal_entries
         * and creates the new mood_word_weights table for adaptive learning.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add AI mood analysis fields to existing journal entries
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN detectedMood TEXT")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN sentimentScore REAL")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN moodConfidence REAL")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN aiTags TEXT")

                // Create the adaptive learning weights table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mood_word_weights (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        mood TEXT NOT NULL,
                        weight INTEGER NOT NULL DEFAULT 1,
                        lastUpdated INTEGER NOT NULL
                    )
                """.trimIndent())

                // Unique index for upsert pattern
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_mood_word_weights_word_mood 
                    ON mood_word_weights (word, mood)
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "journal_database"
                        )
                            .addMigrations(MIGRATION_8_9)
                            .fallbackToDestructiveMigration(true) // For development safety
                            .build()
                    INSTANCE = instance
                    instance
                }
        }
    }
}
