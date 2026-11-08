package com.balancecalories.data.repo

import androidx.room.withTransaction
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.ProductEntity
import java.io.InputStream
import org.json.JSONObject

/** What an import attempt did, so the caller can log it or show it on the debug screen. */
data class StarterPackImport(
    val imported: Int,
    val skipped: Boolean,
    val version: Int,
)

/**
 * Imports the bundled starter ingredient pack on first run.
 *
 * Two properties matter here: it is **idempotent** (a stored `starter_pack_version` short-circuits a
 * second run) and it is **atomic** (one transaction, so a failure mid-file cannot leave half a pack).
 *
 * Parsing uses `org.json` rather than a serialization library: this is a single asset read at first
 * launch, and the network client that will need proper JSON handling arrives with its own decision.
 */
class StarterPackImporter(
    private val database: BalanceCaloriesDatabase,
    private val openAsset: () -> InputStream,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Records the bundled pack as present **without importing it**.
     *
     * Used after restoring a backup, which carries the starter products itself: importing again would leave two of
     * every ingredient in the list. The marker is the same one [ensureImported] checks, so the two agree.
     */
    suspend fun markImported() {
        AppSettingsRepository(database).setStarterPackVersion(readVersion())
    }

    suspend fun ensureImported(): StarterPackImport {
        val settings = AppSettingsRepository(database)
        val installed = settings.starterPackVersion()
        val assetVersion = readVersion()
        if (installed != null && installed >= assetVersion) {
            return StarterPackImport(imported = 0, skipped = true, version = installed)
        }

        val items = parseItems()
        val now = clock()
        val rows = items.map { item ->
            ProductEntity(
                barcode = null,
                name = item.name,
                nameRu = item.nameRu,
                brand = null,
                source = SOURCE_STARTER,
                kcal100g = item.kcal100g,
                protein100g = item.protein100g,
                fat100g = item.fat100g,
                carbs100g = item.carbs100g,
                fiber100g = item.fiber100g,
                sugar100g = item.sugar100g,
                salt100g = item.salt100g,
                isLiquid = item.isLiquid,
                createdAt = now,
                updatedAt = now,
            )
        }

        val inserted = database.withTransaction {
            val ids = database.productDao().insertAll(rows)
            settings.setStarterPackVersion(assetVersion)
            ids.count { it != -1L }
        }
        return StarterPackImport(imported = inserted, skipped = false, version = assetVersion)
    }

    private fun readVersion(): Int = openAsset().use { stream ->
        JSONObject(stream.bufferedReader().readText()).optInt("version", 1)
    }

    private fun parseItems(): List<StarterItem> {
        val root = openAsset().use { stream ->
            JSONObject(stream.bufferedReader().readText())
        }
        val array = root.getJSONArray("items")
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    StarterItem(
                        name = item.getString("name"),
                        nameRu = item.optString("name_ru").takeIf { it.isNotBlank() },
                        kcal100g = item.getDouble("kcal_100g"),
                        protein100g = item.optDoubleOrNull("protein_100g"),
                        fat100g = item.optDoubleOrNull("fat_100g"),
                        carbs100g = item.optDoubleOrNull("carbs_100g"),
                        fiber100g = item.optDoubleOrNull("fiber_100g"),
                        sugar100g = item.optDoubleOrNull("sugar_100g"),
                        salt100g = item.optDoubleOrNull("salt_100g"),
                        isLiquid = item.optBoolean("is_liquid", false),
                    ),
                )
            }
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null

    private data class StarterItem(
        val name: String,
        val nameRu: String?,
        val kcal100g: Double,
        val protein100g: Double?,
        val fat100g: Double?,
        val carbs100g: Double?,
        val fiber100g: Double?,
        val sugar100g: Double?,
        val salt100g: Double?,
        val isLiquid: Boolean,
    )

    companion object {
        const val SOURCE_STARTER = "starter"
        const val ASSET_NAME = "starter_products.json"
    }
}
