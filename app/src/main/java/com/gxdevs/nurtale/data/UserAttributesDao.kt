package com.gxdevs.nurtale.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAttributesDao {
    // Custom Emotions
    @Query("SELECT * FROM custom_emotions") fun getAllCustomEmotions(): Flow<List<CustomEmotion>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomEmotion(emotion: CustomEmotion)

}


