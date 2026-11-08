package com.balancecalories.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.food.Meal
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.ProductSource
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.ProfileEntity
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Logging is the part where a wrong number becomes a wrong day, and a wrong day becomes a mistrusted app.
 * These tests pin the snapshot behaviour, the day arithmetic and the soft delete.
 */
@RunWith(AndroidJUnit4::class)
class FoodEntryRepositoryTest {

    private lateinit var db: BalanceCaloriesDatabase
    private lateinit var products: ProductRepository
    private lateinit var food: FoodEntryRepository
    private val today = LocalDate.of(2026, 11, 8)
    private var now = 1_760_000_000_000L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BalanceCaloriesDatabase::class.java,
        ).build()
        products = ProductRepository(db) { now }
        food = FoodEntryRepository(db) { now }
        // food_entry.profile_id is a real foreign key (schema v1), so every test needs its people first.
        createProfile(1L, "Owner")
        createProfile(2L, "Wife")
    }

    private suspend fun createProfile(id: Long, name: String) {
        db.profileDao().insert(
            ProfileEntity(
                id = id,
                name = name,
                sex = Sex.MALE,
                birthDate = "1996-11-08",
                heightCm = 175.0,
                activityLevel = ActivityLevel.LIGHT,
                goalRateKgPerWeek = -0.5,
                createdAt = now,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun oats(nameRu: String? = "Овсяные хлопья"): Product = products.saveUserProduct(
        Product(
            name = "Oats, dry",
            nameRu = nameRu,
            source = ProductSource.STARTER,
            kcal100g = 389.0,
            protein100g = 16.9,
            fat100g = 6.9,
            carbs100g = 66.3,
        ),
    )

    @Test
    fun loggingScalesThePortionAndSnapshotsTheNumbers() = runBlocking {
        val product = oats()

        val entry = food.log(profileId = 1L, product = product, grams = 50.0, meal = Meal.BREAKFAST, on = today)

        assertEquals(194.5, entry.kcal, 1e-9)
        assertEquals(8.45, entry.proteinG!!, 1e-9)
        assertEquals(Meal.BREAKFAST, entry.meal)
        assertEquals("Oats, dry", entry.name)
        assertEquals("Овсяные хлопья", entry.nameRu)
        assertFalse(entry.wasEstimated)
    }

    @Test
    fun theDayAddsUpAcrossMeals() = runBlocking {
        val product = oats()
        food.log(1L, product, 50.0, Meal.BREAKFAST, today)
        food.log(1L, product, 100.0, Meal.LUNCH, today)

        val day = food.day(profileId = 1L, on = today, targetKcal = 2000)

        assertEquals(583.5, day.eatenKcal, 1e-9) // 194.5 + 389.0
        assertEquals(1416.5, day.remainingKcal, 1e-9)
        assertFalse(day.isOverBudget)
        assertEquals(1, day.entriesFor(Meal.BREAKFAST).size)
        assertEquals(389.0, day.eatenFor(Meal.LUNCH), 1e-9)
    }

    @Test
    fun eatingOverTheTargetIsReportedNotBlocked() = runBlocking {
        val product = oats()
        food.log(1L, product, 600.0, Meal.DINNER, today) // 2334 kcal

        val day = food.day(1L, today, targetKcal = 2000)

        assertTrue("a heavy day is information, not an error", day.isOverBudget)
        assertEquals(-334.0, day.remainingKcal, 1e-6)
    }

    @Test
    fun anotherProfileAndAnotherDayAreNotCounted() = runBlocking {
        val product = oats()
        food.log(1L, product, 100.0, Meal.LUNCH, today)
        food.log(2L, product, 100.0, Meal.LUNCH, today)
        food.log(1L, product, 100.0, Meal.LUNCH, today.minusDays(1))

        val day = food.day(1L, today, targetKcal = 2000)

        assertEquals(389.0, day.eatenKcal, 1e-9)
    }

    @Test
    fun renamingTheProductDoesNotRewriteWhatWasEaten() = runBlocking {
        val product = oats()
        val entry = food.log(1L, product, 50.0, Meal.BREAKFAST, today)

        products.saveUserProduct(product.copy(name = "Oats (renamed)", kcal100g = 400.0))

        val day = food.day(1L, today, targetKcal = 2000)
        val stored = day.meals.single()
        assertEquals(entry.id, stored.id)
        assertEquals("the snapshot keeps the name used at log time", "Oats, dry", stored.name)
        assertEquals(194.5, stored.kcal, 1e-9)
    }

    @Test
    fun deletingAnEntryKeepsTheRowButRemovesItFromTheDay() = runBlocking {
        val product = oats()
        val entry = food.log(1L, product, 100.0, Meal.LUNCH, today)

        food.delete(entry.id)

        assertEquals(0.0, food.eatenKcal(1L, today), 1e-9)
        assertTrue("the row survives a soft delete", db.foodEntryDao().byId(entry.id)!!.deletedAt != null)
    }

    @Test
    fun editingTheWeightRescalesTheEntriesOwnSnapshot() = runBlocking {
        val product = oats()
        val entry = food.log(1L, product, 100.0, Meal.BREAKFAST, today)

        val edited = food.updateGrams(entry.id, 50.0)

        assertEquals(50.0, edited.grams, 1e-9)
        assertEquals(194.5, edited.kcal, 1e-9)
        assertEquals(8.45, edited.proteinG!!, 1e-9)
        assertEquals(194.5, food.day(1L, today, 2000).eatenKcal, 1e-9)
    }

    @Test
    fun anEditScalesTheSnapshotNotTheCurrentProduct() = runBlocking {
        val product = oats()
        val entry = food.log(1L, product, 100.0, Meal.BREAKFAST, today)
        // The product is corrected afterwards: the logged entry is history and must not absorb it.
        products.saveUserProduct(product.copy(kcal100g = 1000.0))

        val edited = food.updateGrams(entry.id, 200.0)

        assertEquals("same snapshot, doubled", 778.0, edited.kcal, 1e-9)
    }

    @Test
    fun editingToAnImpossibleWeightIsRefused() = runBlocking {
        val entry = food.log(1L, oats(), 100.0, Meal.LUNCH, today)

        assertTrue(runCatching { food.updateGrams(entry.id, 0.0) }.isFailure)
        assertTrue(runCatching { food.updateGrams(entry.id, -5.0) }.isFailure)
        assertEquals(389.0, food.day(1L, today, 2000).eatenKcal, 1e-9)
    }

    @Test
    fun copyingADayDuplicatesTheVisibleEntriesAndLeavesTheOriginalAlone() = runBlocking {
        val product = oats()
        val kept = food.log(1L, product, 100.0, Meal.BREAKFAST, today)
        val removed = food.log(1L, product, 50.0, Meal.LUNCH, today)
        food.delete(removed.id)
        val tomorrow = today.plusDays(1)

        val copied = food.copyDay(1L, from = today, to = tomorrow)

        assertEquals("a deleted entry must not be resurrected", 1, copied)
        val tomorrowIntake = food.day(1L, tomorrow, 2000)
        assertEquals(389.0, tomorrowIntake.eatenKcal, 1e-9)
        assertEquals("the snapshot travels with the copy", "Oats, dry", tomorrowIntake.meals.single().name)
        assertEquals(Meal.BREAKFAST, tomorrowIntake.meals.single().meal)
        assertEquals(389.0, food.day(1L, today, 2000).eatenKcal, 1e-9)
        assertEquals(1, food.countForDay(1L, tomorrow))
    }

    @Test
    fun copyingAnEmptyDayDoesNothing() = runBlocking {
        assertEquals(0, food.copyDay(1L, from = today.minusDays(3), to = today))
        assertEquals(0, food.countForDay(1L, today))
    }

    @Test
    fun aProductWithNoEnergyIsRefused() = runBlocking {
        val unknown = products.saveUserProduct(
            Product(name = "Mystery", source = ProductSource.USER, kcal100g = null),
        )

        val failure = runCatching { food.log(1L, unknown, 100.0, Meal.SNACK, today) }

        assertTrue("expected a refusal, got $failure", failure.isFailure)
        assertEquals(0.0, food.eatenKcal(1L, today), 1e-9)
    }

    @Test
    fun anEstimatedProductsEntryCarriesTheEstimateFlag() = runBlocking {
        val estimated = Product(
            name = "Derived",
            source = ProductSource.OFF,
            kcal100g = 343.2,
            energyEstimated = true,
        )

        val entry = food.log(1L, estimated, 100.0, Meal.SNACK, today)

        assertTrue("the day can then say it contains estimates", entry.wasEstimated)
        assertTrue(food.day(1L, today, 2000).hasEstimates)
    }
}
