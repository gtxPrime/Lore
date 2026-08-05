package com.gxdevs.lore.pets

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// -----------------------------------------------------------------------------
// PetCatalogMetaDao
// -----------------------------------------------------------------------------

@Dao
interface PetCatalogMetaDao {

    @Query("SELECT * FROM pet_catalog_meta WHERE id = 1 LIMIT 1")
    suspend fun getMeta(): PetCatalogMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: PetCatalogMeta)
}

// -----------------------------------------------------------------------------
// PetDefinitionDao
// -----------------------------------------------------------------------------

@Dao
interface PetDefinitionDao {

    @Query("SELECT * FROM pet_definitions ORDER BY emotion, level")
    fun getAllDefinitions(): Flow<List<PetDefinition>>

    @Query("SELECT * FROM pet_definitions ORDER BY emotion, level")
    suspend fun getAllDefinitionsSuspend(): List<PetDefinition>

    @Query("SELECT * FROM pet_definitions WHERE petId = :petId LIMIT 1")
    suspend fun getDefinition(petId: String): PetDefinition?

    /**
     * Upsert a single definition — only inserts or updates catalog metadata,
     * never touches user progress.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(def: PetDefinition)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(defs: List<PetDefinition>)
}

// -----------------------------------------------------------------------------
// PetStageDefinitionDao
// -----------------------------------------------------------------------------

@Dao
interface PetStageDefinitionDao {

    @Query("SELECT * FROM pet_stage_definitions ORDER BY petId, stage ASC")
    fun getAllStages(): Flow<List<PetStageDefinition>>

    @Query("SELECT * FROM pet_stage_definitions WHERE petId = :petId ORDER BY stage ASC")
    suspend fun getStagesForPet(petId: String): List<PetStageDefinition>

    @Query("SELECT * FROM pet_stage_definitions WHERE petId = :petId AND stage = :stage LIMIT 1")
    suspend fun getStage(petId: String, stage: Int): PetStageDefinition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stages: List<PetStageDefinition>)

    @Query("DELETE FROM pet_stage_definitions WHERE petId = :petId AND stage NOT IN (:validStages)")
    suspend fun deleteObsoleteStages(petId: String, validStages: List<Int>)
}

// -----------------------------------------------------------------------------
// CachedStageImageDao
// -----------------------------------------------------------------------------

@Dao
interface CachedStageImageDao {

    @Query("SELECT * FROM cached_stage_images")
    fun getAllCachedImages(): Flow<List<CachedStageImage>>

    @Query("SELECT * FROM cached_stage_images WHERE petId = :petId AND stage = :stage LIMIT 1")
    suspend fun getCachedImage(petId: String, stage: Int): CachedStageImage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: CachedStageImage)

    @Query("DELETE FROM cached_stage_images")
    suspend fun deleteAll()
}

