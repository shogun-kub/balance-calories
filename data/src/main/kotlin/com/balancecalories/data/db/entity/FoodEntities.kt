package com.balancecalories.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A food product. Nutrition is always stored per 100 g (or 100 ml when [isLiquid]), because that is
 * the one unit OpenFoodFacts guarantees and the one unit that survives a serving-size edit.
 */
@Entity(
    tableName = "product",
    indices = [
        Index(value = ["barcode"], unique = true, name = "idx_product_barcode"),
        Index(value = ["name"], name = "idx_product_name"),
    ],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Null for user-created products with no barcode. SQLite allows many NULLs in a unique index. */
    @ColumnInfo(name = "barcode")
    val barcode: String? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "brand")
    val brand: String? = null,

    /** `off|user|starter`. */
    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "kcal_100g")
    val kcal100g: Double? = null,
    @ColumnInfo(name = "protein_100g")
    val protein100g: Double? = null,
    @ColumnInfo(name = "fat_100g")
    val fat100g: Double? = null,
    @ColumnInfo(name = "carbs_100g")
    val carbs100g: Double? = null,
    @ColumnInfo(name = "fiber_100g")
    val fiber100g: Double? = null,
    @ColumnInfo(name = "sugar_100g")
    val sugar100g: Double? = null,
    @ColumnInfo(name = "salt_100g")
    val salt100g: Double? = null,

    @ColumnInfo(name = "is_liquid", defaultValue = "0")
    val isLiquid: Boolean = false,

    @ColumnInfo(name = "serving_grams")
    val servingGrams: Double? = null,
    @ColumnInfo(name = "pack_grams")
    val packGrams: Double? = null,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    /** OpenFoodFacts `last_modified_t` when known, so a stale cached row can be refreshed. */
    @ColumnInfo(name = "off_rev")
    val offRev: Long? = null,

    /** The user corrected an OFF product; a later OFF refresh must not overwrite it. */
    @ColumnInfo(name = "is_override", defaultValue = "0")
    val isOverride: Boolean = false,

    /** kcal was derived from macros rather than read from the OFF energy field. */
    @ColumnInfo(name = "energy_estimated", defaultValue = "0")
    val energyEstimated: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/** FTS4 index over [ProductEntity], kept in sync by Room through the external-content mechanism. */
@Entity(tableName = "product_fts")
@Fts4(contentEntity = ProductEntity::class)
data class ProductFtsEntity(
    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "brand")
    val brand: String? = null,
)

/** A recipe the user cooks repeatedly. Nutrition is derived from its items at log time. */
@Entity(
    tableName = "recipe",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profile_id"], name = "idx_recipe_profile")],
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    @ColumnInfo(name = "name")
    val name: String,

    /** Null means "use the raw sum of the items" rather than the cooked weight. */
    @ColumnInfo(name = "cooked_weight_g")
    val cookedWeightG: Double? = null,

    @ColumnInfo(name = "servings", defaultValue = "1")
    val servings: Double = 1.0,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "recipe_item",
    primaryKeys = ["recipe_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
        ),
    ],
    indices = [Index(value = ["product_id"], name = "idx_recipe_item_product")],
)
data class RecipeItemEntity(
    @ColumnInfo(name = "recipe_id")
    val recipeId: Long,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "grams")
    val grams: Double,

    /** Explicit ordering: a recipe is a list, and SQLite has no inherent row order. */
    @ColumnInfo(name = "position")
    val position: Int,
)

/**
 * One logged food. The nutrition columns are a snapshot taken when the entry was created, so editing a
 * product or refreshing it from OpenFoodFacts can never rewrite history (hard rule in the docs).
 */
@Entity(
    tableName = "food_entry",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["profile_id", "local_date"], name = "idx_entry_profile_date"),
        Index(value = ["product_id"], name = "idx_entry_product"),
        Index(value = ["recipe_id"], name = "idx_entry_recipe"),
    ],
)
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    /** UTC epoch millis. */
    @ColumnInfo(name = "consumed_at")
    val consumedAt: Long,

    @ColumnInfo(name = "local_date")
    val localDate: String,

    /** `breakfast|lunch|dinner|snack`. */
    @ColumnInfo(name = "meal")
    val meal: String,

    @ColumnInfo(name = "product_id")
    val productId: Long? = null,

    @ColumnInfo(name = "recipe_id")
    val recipeId: Long? = null,

    @ColumnInfo(name = "grams")
    val grams: Double,

    @ColumnInfo(name = "kcal")
    val kcal: Double,
    @ColumnInfo(name = "protein_g")
    val proteinG: Double? = null,
    @ColumnInfo(name = "fat_g")
    val fatG: Double? = null,
    @ColumnInfo(name = "carbs_g")
    val carbsG: Double? = null,
    @ColumnInfo(name = "fiber_g")
    val fiberG: Double? = null,
    @ColumnInfo(name = "sugar_g")
    val sugarG: Double? = null,
    @ColumnInfo(name = "salt_g")
    val saltG: Double? = null,

    /** At least one macro was derived rather than measured, so the UI can say so. */
    @ColumnInfo(name = "was_estimated", defaultValue = "0")
    val wasEstimated: Boolean = false,

    @ColumnInfo(name = "note")
    val note: String? = null,

    /** Soft delete; hard purge after 30 days. */
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
