package com.gxdevs.athera.pets

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import androidx.room.TypeConverter

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Room Entities
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Stores the remote catalog version and the last time we successfully fetched it.
 * There is always exactly one row (id = 1).
 */
@Entity(tableName = "pet_catalog_meta")
data class PetCatalogMeta(
    @PrimaryKey val id: Int = 1,
    val catalogVersion: Int = 0,          // version field from pets.json
    val lastFetchedEpoch: Long = 0L       // System.currentTimeMillis() at last successful fetch
)

/**
 * One row per pet definition (from pets.json).
 * User progress fields (journalCount, lastUpdatedDay) are in [com.gxdevs.athera.PetProgress] â€” NOT here.
 * We never overwrite user progress when updating catalog data.
 */
@Entity(tableName = "pet_definitions")
data class PetDefinition(
    @PrimaryKey val petId: String,          // e.g. "bright_001"
    val name: String,                       // "Auros"
    val emotion: String,                    // "bright"
    val level: Int,                         // 1, 2, â€¦
    val description: String,
    val totalStages: Int
)

/**
 * One row per stage of each pet definition.
 * Stage images are NOT stored here â€” see [CachedStageImage].
 */
@Entity(
    tableName = "pet_stage_definitions",
    primaryKeys = ["petId", "stage"],
    foreignKeys = [ForeignKey(
        entity = PetDefinition::class,
        parentColumns = ["petId"],
        childColumns = ["petId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("petId")]
)
data class PetStageDefinition(
    val petId: String,
    val stage: Int,             // 1-based (1 = Egg, 6 = Mythical)
    val stageName: String,
    val journalsRequired: Int,
    val imageUrl: String        // Cloudinary URL â€” image fetched lazily on unlock
)

/**
 * Stores the LOCAL file path of a pet stage image that has been permanently cached.
 * Once a row exists here, the image is NEVER re-downloaded.
 */
@Entity(
    tableName = "cached_stage_images",
    primaryKeys = ["petId", "stage"]
)
data class CachedStageImage(
    val petId: String,
    val stage: Int,
    val localPath: String,      // absolute path inside app's filesDir
    val cachedAtEpoch: Long = System.currentTimeMillis()
)

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Gson-mapped JSON models (not Room entities)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

data class PetCatalogJson(
    val version: Int,
    val last_updated: String,
    val pets: List<PetJson>
)

data class PetJson(
    val id: String,
    val name: String,
    val emotion: String,
    val level: Int,
    val description: String,
    val total_stages: Int,
    val stages: List<PetStageJson>
)

data class PetStageJson(
    val stage: Int,
    val stage_name: String,
    val journals_required: Int,
    val image_url: String
)

