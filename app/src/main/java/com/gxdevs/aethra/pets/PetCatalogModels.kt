package com.gxdevs.aethra.pets

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// -----------------------------------------------------------------------------
// Room Entities
// -----------------------------------------------------------------------------

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
 * User progress fields (journalCount, lastUpdatedDay) are in [com.gxdevs.aethra.pets.PetProgress] â€” NOT here.
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

// -----------------------------------------------------------------------------
// Gson-mapped JSON models (not Room entities)
// -----------------------------------------------------------------------------

@Keep
data class PetCatalogJson(
    val version: Int,
    @SerializedName("last_updated") val lastUpdated: String,
    val pets: List<PetJson>
)

@Keep
data class PetJson(
    val id: String,
    val name: String,
    val emotion: String,
    val level: Int,
    val description: String,
    @SerializedName("total_stages") val totalStages: Int,
    val stages: List<PetStageJson>
)

@Keep
data class PetStageJson(
    val stage: Int,
    @SerializedName("stage_name") val stageName: String,
    @SerializedName("journals_required") val journalsRequired: Int,
    @SerializedName("image_url") val imageUrl: String
)

