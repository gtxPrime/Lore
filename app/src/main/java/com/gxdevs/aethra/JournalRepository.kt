package com.gxdevs.aethra

import kotlinx.coroutines.flow.Flow

class JournalRepository(private val database: AppDatabase) {
    val allEntries: Flow<List<JournalEntry>> = database.journalDao().getAllEntries()
    val allMoods: Flow<List<MoodType>> = database.moodDao().getAllMoodTypes()
    
    suspend fun insertEntry(entry: JournalEntry): Long {
        return database.journalDao().insertEntry(entry)
    }

    suspend fun deleteEntry(entry: JournalEntry) {
        database.journalDao().deleteEntry(entry)
    }

    suspend fun deleteAllEntries() {
        database.journalDao().deleteAllEntries()
    }
    
    suspend fun insertMood(mood: MoodType) {
        database.moodDao().insertMoodType(mood)
    }
}


