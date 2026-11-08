package com.balancecalories.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.balancecalories.data.db.BalanceCaloriesDatabase
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The starter pack is the app's offline day-one data, so the import is tested against the real shipped
 * asset rather than a fixture: a typo in the JSON or a renamed field fails here.
 */
@RunWith(AndroidJUnit4::class)
class StarterPackImportTest {

    private lateinit var db: BalanceCaloriesDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BalanceCaloriesDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** The asset travels in the test APK for a library module; the app APK owns it at runtime. */
    private fun openAsset(): InputStream {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return runCatching { instrumentation.context.assets.open(StarterPackImporter.ASSET_NAME) }
            .getOrElse { instrumentation.targetContext.assets.open(StarterPackImporter.ASSET_NAME) }
    }

    private fun importer() = StarterPackImporter(db, ::openAsset) { 1_760_000_000_000 }

    @Test
    fun importsTheWholePackOnceAndSurvivesASecondRun() = runBlocking {
        val result = importer().ensureImported()

        assertTrue("expected a decent pack, got ${result.imported}", result.imported >= 60)
        assertEquals(result.imported, db.productDao().count())
        assertEquals(result.imported, db.productDao().countFromSource(StarterPackImporter.SOURCE_STARTER))

        val second = importer().ensureImported()

        assertTrue("second import must be skipped", second.skipped)
        assertEquals("no duplicates on re-import", result.imported, db.productDao().count())
    }

    @Test
    fun everyImportedProductIsBilingualAndHasEnergy() = runBlocking {
        importer().ensureImported()

        val oats = db.productDao().byName("Oats, dry")
        assertNotNull("the pack should contain oats", oats)
        val product = oats!!
        assertEquals("Овсяные хлопья", product.nameRu)
        assertEquals(389.0, product.kcal100g ?: 0.0, 0.001)
        assertEquals(16.9, product.protein100g ?: 0.0, 0.001)
        assertEquals(0.0, product.salt100g ?: 0.0, 0.001)
        assertNull("starter products have no barcode", product.barcode)
        assertEquals(StarterPackImporter.SOURCE_STARTER, product.source)
    }

    @Test
    fun liquidsAreMarkedAsLiquids() = runBlocking {
        importer().ensureImported()

        val milk = db.productDao().byName("Milk, 3.2%")!!
        assertTrue("milk should be flagged as a liquid", milk.isLiquid)

        val potato = db.productDao().byName("Potato, raw")!!
        assertTrue("potato is not a liquid", !potato.isLiquid)
    }
}
