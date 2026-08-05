package com.gxdevs.lore.data.mood

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoodType(moodType: MoodType)

    @Query("SELECT * FROM mood_types")
    fun getAllMoodTypes(): Flow<List<MoodType>>
    
    @Query("SELECT * FROM mood_types WHERE id = :id")
    suspend fun getMoodById(id: Long): MoodType?
}


