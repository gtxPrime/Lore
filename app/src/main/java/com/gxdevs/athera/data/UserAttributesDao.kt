package com.gxdevs.athera.data

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

    // Saved Locations
    @Query("SELECT * FROM saved_locations") fun getAllLocations(): Flow<List<SavedLocation>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocation(location: SavedLocation)

    // Saved People
    @Query("SELECT * FROM saved_people") fun getAllPeople(): Flow<List<SavedPerson>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPerson(person: SavedPerson)
}


