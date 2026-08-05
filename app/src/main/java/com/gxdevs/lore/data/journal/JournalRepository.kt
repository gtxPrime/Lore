package com.gxdevs.lore.data.journal

import com.gxdevs.lore.data.AppDatabase
import kotlinx.coroutines.flow.Flow

class JournalRepository(private val database: AppDatabase) {
    val allEntries: Flow<List<JournalEntry>> = database.journalDao().getAllEntries()

    suspend fun insertEntry(entry: JournalEntry): Long {
        return database.journalDao().insertEntry(entry)
    }

    suspend fun deleteEntry(entry: JournalEntry) {
        database.journalDao().deleteEntry(entry)
    }
}


