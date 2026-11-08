package com.balancecalories.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.core.food.EnergyPer100g
import com.balancecalories.core.food.ProductSource
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.off.OffLookup
import com.balancecalories.data.off.OffProduct
import com.balancecalories.data.off.UnusableReason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Product persistence against a real SQLite, because the properties under test — a stable row id, an
 * override that survives a refresh, a cached miss that stops a second request — are database behaviour,
 * not just mapping.
 */
@RunWith(AndroidJUnit4::class)
class ProductRepositoryTest {

    private lateinit var db: BalanceCaloriesDatabase
    private lateinit var products: ProductRepository

    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BalanceCaloriesDatabase::class.java,
        ).build()
        products = ProductRepository(db) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun off(
        code: String = "3017620422003",
        name: String? = "Nutella",
        kcal: Double? = 539.0,
        lastModified: Long = 100L,
    ) = OffProduct(
        code = code,
        name = name,
        nameRu = null,
        brand = "Ferrero",
        quantityText = "400 g",
        servingText = null,
        servingGrams = null,
        packGrams = 400.0,
        packMillilitres = null,
        energy = kcal?.let { EnergyPer100g(it, estimated = false) },
        proteinG = 6.3,
        fatG = 30.9,
        carbsG = 57.5,
        fiberG = 0.0,
        sugarG = 56.3,
        saltG = 0.107,
        imageUrl = null,
        lastModifiedSeconds = lastModified,
        nutritionDataPer = "100g",
        declaresNoNutritionData = false,
    )

    @Test
    fun aFetchedProductIsStoredAndFoundAgainWithoutTheNetwork() = runBlocking {
        val stored = products.saveFromOff(off())!!

        assertEquals("Nutella", stored.name)
        assertEquals(ProductSource.OFF, stored.source)

        val found = products.findByBarcode("3017620422003")
        assertNotNull(found)
        assertEquals(stored.id, found!!.id)
        assertEquals(539.0, found.kcal100g!!, 1e-9)
    }

    @Test
    fun refreshingAProductUpdatesTheSameRowInsteadOfCreatingANewOne() = runBlocking {
        val first = products.saveFromOff(off(kcal = 539.0, lastModified = 100L))!!
        now = 2_000L

        val refreshed = products.saveFromOff(off(kcal = 540.0, lastModified = 200L))!!

        assertEquals("the row id must stay stable for entries pointing at it", first.id, refreshed.id)
        assertEquals(540.0, refreshed.kcal100g!!, 1e-9)
        assertEquals(1, db.productDao().count())
    }

    @Test
    fun aProductOffHasNotRevisedIsNotRewritten() = runBlocking {
        now = 1_000L
        products.saveFromOff(off(lastModified = 100L))!!
        now = 5_000L

        val unchanged = products.saveFromOff(off(lastModified = 100L))!!

        assertEquals(1_000L, db.productDao().byId(unchanged.id)!!.updatedAt)
    }

    @Test
    fun aUserCorrectionIsNeverOverwrittenByARefresh() = runBlocking {
        val original = products.saveFromOff(off(kcal = 539.0))!!
        val corrected = products.saveUserProduct(
            original.copy(kcal100g = 520.0, name = "Nutella (my numbers)"),
        )
        assertEquals("a correction to an OFF row is an override", true, corrected.isOverride)
        assertEquals(ProductSource.OFF, corrected.source)

        val afterRefresh = products.saveFromOff(off(kcal = 539.0, lastModified = 999L))!!

        assertEquals("the user's number must survive", 520.0, afterRefresh.kcal100g!!, 1e-9)
        assertEquals("Nutella (my numbers)", afterRefresh.name)
    }

    @Test
    fun aProductWithNoEnergyIsRefusedRatherThanStored() = runBlocking {
        val stored = products.saveFromOff(off(kcal = null))

        assertNull(stored)
        assertEquals(0, db.productDao().count())
    }

    @Test
    fun aNotFoundAnswerIsRememberedSoTheScanDoesNotAskTwice() = runBlocking {
        assertFalse(products.isKnownMiss("2573468086162"))

        products.markNotFound("2573468086162")

        assertTrue(products.isKnownMiss("2573468086162"))
        assertEquals("a cached miss is not a retry job", 0, products.retryable().size)
    }

    @Test
    fun aFailedRequestIsQueuedForRetryAndClearedOnceItResolves() = runBlocking {
        products.queueForRetry("4012345678901", "HTTP 503")
        assertEquals(listOf("4012345678901"), products.retryable())

        products.clearPending("4012345678901")

        assertEquals(0, products.retryable().size)
    }

    @Test
    fun aCachedMissIsNotTurnedIntoARetryJobByAFailure() = runBlocking {
        products.markNotFound("2573468086162")

        products.queueForRetry("2573468086162", "HTTP 503")

        assertTrue(products.isKnownMiss("2573468086162"))
        assertEquals(0, products.retryable().size)
    }

    @Test
    fun searchMatchesRussianNamesAndBrands() = runBlocking {
        products.saveFromOff(off())
        val bare = products.saveUserProduct(
            com.balancecalories.core.food.Product(
                name = "Buckwheat",
                nameRu = "Гречка",
                source = ProductSource.USER,
                kcal100g = 343.0,
            ),
        )!!

        assertEquals(1, products.search("Греч").size)
        assertEquals(bare.id, products.search("Греч").single().id)
        assertEquals(1, products.search("Ferrero").size)
        assertEquals(0, products.search("   ").size)
    }

    // --- resolver ---------------------------------------------------------------------------------

    @Test
    fun aKnownBarcodeIsAnsweredFromTheDatabaseWithoutCallingTheNetwork() = runBlocking {
        products.saveFromOff(off())
        var calls = 0
        val resolver = BarcodeResolver(products)

        val result = resolver.resolve("3017620422003") { calls++; OffLookup.NotFound }

        assertTrue("expected Cached, got $result", result is Lookup.Cached)
        assertEquals(0, calls)
    }

    @Test
    fun aNewBarcodeIsFetchedStoredAndReportedAsFetched() = runBlocking {
        val resolver = BarcodeResolver(products)

        val result = resolver.resolve("3017620422003") { OffLookup.Found(off()) }

        assertTrue("expected Fetched, got $result", result is Lookup.Fetched)
        assertNotNull(products.findByBarcode("3017620422003"))
    }

    @Test
    fun aCachedMissShortCircuitsTheSecondLookup() = runBlocking {
        val resolver = BarcodeResolver(products)
        resolver.resolve("2573468086162") { OffLookup.NotFound }
        var calls = 0

        val second = resolver.resolve("2573468086162") { calls++; OffLookup.NotFound }

        assertEquals(Lookup.KnownMiss, second)
        assertEquals("the miss must be cached", 0, calls)
    }

    @Test
    fun anUnusableProductIsReportedWithAReasonAndNotStored() = runBlocking {
        val resolver = BarcodeResolver(products)

        val result = resolver.resolve("3017620422003") { OffLookup.Found(off(kcal = null)) }

        assertEquals(Lookup.Unusable(UnusableReason.NO_ENERGY), result)
        assertEquals(0, db.productDao().count())
    }

    @Test
    fun anOfflineFailureQueuesTheBarcodeAndAnInvalidOneDoesNot() = runBlocking {
        val resolver = BarcodeResolver(products)

        val offline = resolver.resolve("4012345678901") { OffLookup.Unavailable("no network") }
        assertTrue("expected Offline, got $offline", offline is Lookup.Offline)
        assertEquals(listOf("4012345678901"), products.retryable())

        val invalid = resolver.resolve("abc") { OffLookup.NotFound }
        assertEquals(Lookup.Invalid("abc"), invalid)
        assertEquals("invalid codes are not queued", 1, products.pendingCount())
    }

    @Test
    fun drainingTheQueueResolvesWhatItCanAndKeepsWhatItCannot() = runBlocking {
        val products2 = ProductRepository(db) { now }
        products2.queueForRetry("3017620422003", "HTTP 503")
        products2.queueForRetry("4012345678901", "HTTP 503")
        val resolver = BarcodeResolver(products2)

        var calls = 0
        val resolved = resolver.drainQueue(fetch = { barcode ->
            calls++
            if (barcode == "3017620422003") {
                OffLookup.Found(off(code = barcode))
            } else {
                OffLookup.Unavailable("still offline")
            }
        })

        assertEquals(1, resolved)
        assertEquals(2, calls)
        assertEquals(listOf("4012345678901"), products2.retryable())
        assertNotNull(products2.findByBarcode("3017620422003"))
    }
}
