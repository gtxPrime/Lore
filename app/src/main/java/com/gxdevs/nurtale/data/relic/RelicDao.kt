package com.gxdevs.nurtale.data.relic

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RelicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelic(relic: Relic): Long

    @Query("SELECT * FROM relics ORDER BY sealedAtTimestamp DESC")
    fun getAllRelics(): Flow<List<Relic>>

    /** Relics that are past their unseal date and not yet opened */
    @Query("""
        SELECT * FROM relics
        WHERE isUnsealed = 0
          AND (sealedAtTimestamp + unsealAfterDays * 86400000) <= :nowMs
        ORDER BY sealedAtTimestamp DESC
    """)
    fun getSurfacedRelics(nowMs: Long): Flow<List<Relic>>

    /** Still-sealed relics */
    @Query("""
        SELECT * FROM relics
        WHERE isUnsealed = 0
          AND (sealedAtTimestamp + unsealAfterDays * 86400000) > :nowMs
        ORDER BY sealedAtTimestamp DESC
    """)
    fun getLockedRelics(nowMs: Long): Flow<List<Relic>>

    @Query("UPDATE relics SET isUnsealed = 1 WHERE id = :relicId")
    suspend fun markUnsealed(relicId: Long)

    @Query("DELETE FROM relics WHERE id = :relicId")
    suspend fun deleteRelic(relicId: Long)
}

