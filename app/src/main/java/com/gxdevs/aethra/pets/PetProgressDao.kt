package com.gxdevs.aethra.pets

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PetProgressDao {

    @Query("SELECT * FROM pet_progress")
    fun getAllPetProgress(): Flow<List<PetProgress>>

    @Query("SELECT * FROM pet_progress")
    suspend fun getAllPetProgressSync(): List<PetProgress>

    @Query("SELECT * FROM pet_progress WHERE moodId = :moodId LIMIT 1")
    suspend fun getPetProgressForMood(moodId: String): PetProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPetProgress(progress: PetProgress)

    @Query("DELETE FROM pet_progress")
    suspend fun deleteAll()
}

