package com.balancecalories.data.repo

import androidx.room.withTransaction
import com.balancecalories.core.food.Barcode
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.ProductSource
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.dao.PendingLookupDao
import com.balancecalories.data.db.entity.PendingLookupEntity
import com.balancecalories.data.mapper.toDomain
import com.balancecalories.data.mapper.toEntity
import com.balancecalories.data.off.OffLookup
import com.balancecalories.data.off.OffProduct
import com.balancecalories.data.off.UnusableReason

/** What a barcode lookup produced, with the failure modes kept apart. */
sealed interface Lookup {

    /** Already in the local database; no network was touched. */
    data class Cached(val product: Product) : Lookup

    /** Fetched from OpenFoodFacts and stored. */
    data class Fetched(val product: Product) : Lookup

    /** OpenFoodFacts says the product does not exist. Remembered, so it is not asked again. */
    data object NotFound : Lookup

    /** We already knew OpenFoodFacts does not have it; short-circuited from the cached miss. */
    data object KnownMiss : Lookup

    /** The product exists but cannot be logged (no name, or no energy). See [reason]. */
    data class Unusable(val reason: UnusableReason) : Lookup

    /** The network failed; the barcode is queued for a later retry. */
    data class Offline(val reason: String) : Lookup

    /** Not a barcode we should send anywhere. */
    data class Invalid(val barcode: String) : Lookup
}

/**
 * Products: the local cache first, OpenFoodFacts second, and the user always above both.
 *
 * Offline is the default (AGENTS.md, hard rule 11): a known barcode never touches the network, and a miss
 * or a failed request leaves a row in `pending_lookup` instead of losing the scan.
 */
class ProductRepository(
    private val database: BalanceCaloriesDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val products = database.productDao()
    private val pending = database.pendingLookupDao()

    suspend fun findByBarcode(barcode: String): Product? =
        products.byBarcode(barcode)?.toDomain()

    suspend fun byId(id: Long): Product? = products.byId(id)?.toDomain()

    suspend fun search(query: String, limit: Int = 20): List<Product> =
        if (query.isBlank()) emptyList() else products.search(query.trim(), limit).map { it.toDomain() }

    /** Everything, for the first look at the food list before the user types anything. */
    suspend fun browse(limit: Int = 200): List<Product> = products.browse(limit).map { it.toDomain() }

    /**
     * Stores an OpenFoodFacts product, unless the local row is the user's own correction or already newer.
     *
     * Returns null when the product is unusable, so the caller can say why instead of showing an empty row.
     */
    suspend fun saveFromOff(off: OffProduct): Product? {
        val existing = off.code.takeIf { it.isNotBlank() }?.let { products.byBarcode(it) }
        if (existing?.isOverride == true) return existing.toDomain()
        // A cached row that OFF has not revised since: nothing to write, no reason to touch updated_at.
        if (existing?.offRev != null && off.lastModifiedSeconds in 1..existing.offRev) {
            return existing.toDomain()
        }

        val entity = off.toEntity(existing, clock()) ?: return null
        return database.withTransaction {
            if (existing == null) {
                entity.copy(id = products.insert(entity)).toDomain()
            } else {
                products.update(entity)
                entity.toDomain()
            }
        }
    }

    /**
     * Saves a product the user typed, or a correction to an OFF row.
     *
     * A correction is marked [Product.isOverride] so a later refresh cannot quietly undo it — the user's own
     * numbers outrank a crowd-sourced database, and that has to survive a re-scan.
     */
    suspend fun saveUserProduct(product: Product): Product {
        val now = clock()
        val existing = product.id.takeIf { it != Product.NEW }?.let { products.byId(it) }
            ?: product.barcode?.let { products.byBarcode(it) }
        val corrected = if (existing != null && existing.source != ProductSource.toStorage(ProductSource.USER)) {
            product.copy(isOverride = true)
        } else {
            product
        }
        val entity = corrected.toEntity(
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return database.withTransaction {
            if (existing == null) {
                entity.copy(id = products.insert(entity)).toDomain()
            } else {
                products.update(entity.copy(id = existing.id))
                entity.copy(id = existing.id).toDomain()
            }
        }
    }

    /** Caches "OpenFoodFacts does not have this product", so a re-scan does not ask again. */
    suspend fun markNotFound(barcode: String) {
        val previous = pending.byBarcode(barcode)
        pending.put(
            PendingLookupEntity(
                barcode = barcode,
                attempts = (previous?.attempts ?: 0) + 1,
                lastError = PendingLookupDao.MISS_MARKER,
                createdAt = previous?.createdAt ?: clock(),
            ),
        )
    }

    suspend fun isKnownMiss(barcode: String): Boolean =
        pending.byBarcode(barcode)?.lastError == PendingLookupDao.MISS_MARKER

    /** Queues a barcode whose lookup failed for a transient reason. */
    suspend fun queueForRetry(barcode: String, error: String) {
        val previous = pending.byBarcode(barcode)
        if (previous?.lastError == PendingLookupDao.MISS_MARKER) return // a cached miss is not a retry job
        pending.put(
            PendingLookupEntity(
                barcode = barcode,
                attempts = (previous?.attempts ?: 0) + 1,
                lastError = error.take(200),
                createdAt = previous?.createdAt ?: clock(),
            ),
        )
    }

    suspend fun retryable(limit: Int = 20): List<String> =
        pending.retryable(PendingLookupDao.MISS_MARKER, limit).map { it.barcode }

    suspend fun clearPending(barcode: String) = pending.delete(barcode)

    suspend fun pendingCount(): Int = pending.count()
}

/**
 * Turns a scanned or typed barcode into something the user can log.
 *
 * The fetch is injected rather than owned, so the whole decision tree — cache, cached miss, found, unusable,
 * offline — is testable without a network and without faking an HTTP client.
 */
class BarcodeResolver(
    private val products: ProductRepository,
) {

    suspend fun resolve(
        rawBarcode: String,
        fetch: suspend (String) -> OffLookup,
    ): Lookup {
        val barcode = Barcode.normalize(rawBarcode) ?: return Lookup.Invalid(rawBarcode)

        products.findByBarcode(barcode)?.let { return Lookup.Cached(it) }
        if (products.isKnownMiss(barcode)) return Lookup.KnownMiss

        return when (val answer = fetch(barcode)) {
            is OffLookup.Found -> {
                val stored = products.saveFromOff(answer.product)
                if (stored != null) {
                    products.clearPending(barcode)
                    Lookup.Fetched(stored)
                } else {
                    Lookup.Unusable(answer.product.unusableReason ?: UnusableReason.NO_ENERGY)
                }
            }

            OffLookup.NotFound -> {
                products.markNotFound(barcode)
                Lookup.NotFound
            }

            is OffLookup.Invalid -> Lookup.Invalid(answer.barcode)

            is OffLookup.Unavailable -> {
                products.queueForRetry(barcode, answer.reason)
                Lookup.Offline(answer.reason)
            }
        }
    }

    /** Retries the offline queue once; returns how many barcodes resolved. */
    suspend fun drainQueue(
        fetch: suspend (String) -> OffLookup,
        limit: Int = 20,
    ): Int {
        var resolved = 0
        for (barcode in products.retryable(limit)) {
            val answer = fetch(barcode)
            val stored = when (answer) {
                is OffLookup.Found -> products.saveFromOff(answer.product)
                else -> null
            }
            if (stored != null) {
                products.clearPending(barcode)
                resolved++
            } else if (answer is OffLookup.Unavailable) {
                products.queueForRetry(barcode, answer.reason)
            } else {
                // A definitive answer — not found or unusable — ends the retry loop for this barcode.
                products.clearPending(barcode)
            }
        }
        return resolved
    }
}
