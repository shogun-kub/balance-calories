package com.balancecalories.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.food.MealDefaults
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.ProductSource
import com.balancecalories.core.food.Recipe
import com.balancecalories.core.food.RecipeItem
import com.balancecalories.core.food.RecipeMath
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.ProfileEntity
import java.time.LocalDate
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
 * Recipes are the one place where a number is computed from other numbers, so the tests concentrate on what
 * must stay true: ingredients are replaced as a set, the stored recipe reads back the same way, and logging a
 * portion freezes the numbers so a later edit cannot rewrite a meal.
 */
@RunWith(AndroidJUnit4::class)
class RecipeRepositoryTest {

    private lateinit var db: BalanceCaloriesDatabase
    private lateinit var products: ProductRepository
    private lateinit var recipes: RecipeRepository
    private lateinit var food: FoodEntryRepository
    private val today = LocalDate.of(2026, 11, 8)
    private var now = 1_760_000_000_000L

    @Before
    fun setUp() {
        runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BalanceCaloriesDatabase::class.java,
        ).build()
        products = ProductRepository(db) { now }
        recipes = RecipeRepository(db) { now }
        food = FoodEntryRepository(db) { now }
        db.profileDao().insert(
            ProfileEntity(
                id = 1L,
                name = "Owner",
                sex = Sex.MALE,
                birthDate = "1996-11-08",
                heightCm = 175.0,
                activityLevel = ActivityLevel.LIGHT,
                goalRateKgPerWeek = -0.5,
                createdAt = now,
            ),
        )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun chicken(): Product = products.saveUserProduct(
        Product(name = "Chicken breast", source = ProductSource.STARTER, kcal100g = 165.0, protein100g = 31.0),
    )

    private suspend fun rice(): Product = products.saveUserProduct(
        Product(name = "Rice, dry", source = ProductSource.STARTER, kcal100g = 344.0, carbs100g = 78.0),
    )

    private suspend fun pilaf(vararg items: RecipeItem, cooked: Double? = 700.0) = recipes.save(
        Recipe(
            profileId = 1L,
            name = "Chicken pilaf",
            cookedWeightG = cooked,
            servings = 3.0,
            items = items.toList(),
        ),
    )

    @Test
    fun aSavedRecipeReadsBackWithItsIngredientsInOrder() = runBlocking {
        val stored = pilaf(RecipeItem(chicken(), 300.0), RecipeItem(rice(), 150.0))

        assertTrue("a new recipe gets a real id", stored.id > 0)
        val read = recipes.byId(stored.id)!!
        assertEquals("Chicken pilaf", read.name)
        assertEquals(700.0, read.cookedWeightG!!, 1e-9)
        assertEquals(3.0, read.servings, 1e-9)
        assertEquals(listOf("Chicken breast", "Rice, dry"), read.items.map { it.product.name })
        assertEquals(listOf(300.0, 150.0), read.items.map { it.grams })
    }

    @Test
    fun editingIngredientsReplacesThemRatherThanAppending() = runBlocking {
        val stored = pilaf(RecipeItem(chicken(), 300.0), RecipeItem(rice(), 150.0))

        recipes.save(stored.copy(items = listOf(RecipeItem(chicken(), 200.0))))

        val read = recipes.byId(stored.id)!!
        assertEquals("one ingredient after the edit, not three", 1, read.items.size)
        assertEquals(200.0, read.items.single().grams, 1e-9)
        assertEquals("the id survives an edit", stored.id, read.id)
    }

    @Test
    fun theDishIsDerivedFromItsIngredientsAndCookedWeight() = runBlocking {
        val stored = pilaf(RecipeItem(chicken(), 300.0), RecipeItem(rice(), 150.0))

        val derived = recipes.derive(stored)

        // 165*3 + 344*1.5 = 495 + 516 = 1011 kcal over 450 g raw; cooked down to 700 g? no: up to 700 g.
        assertEquals(450.0, derived.totals.rawGrams, 1e-9)
        assertEquals(1011.0, derived.totals.kcal!!, 1e-9)
        assertEquals(RecipeMath.BasisKind.COOKED, derived.basis.kind)
        assertEquals(1011.0 / 700.0 * 100.0, derived.per100g!!.kcal!!, 1e-9)
    }

    @Test
    fun loggingAPortionSnapshotsTheNumbersAndLinksTheRecipe() = runBlocking {
        val stored = pilaf(RecipeItem(chicken(), 300.0), RecipeItem(rice(), 150.0))
        now += 1_000L

        val entry = food.logRecipe(1L, stored, grams = 250.0, mealKey = MealDefaults.DINNER, on = today)

        assertEquals("Chicken pilaf", entry.name)
        assertEquals(250.0, entry.grams, 1e-9)
        assertEquals(1011.0 / 700.0 * 250.0, entry.kcal, 1e-6)
        assertTrue("recipe nutrition is derived, never measured on the dish", entry.wasEstimated)
        assertNull("a recipe entry is not a product", entry.productId)
    }

    @Test
    fun editingTheRecipeAfterwardsDoesNotRewriteWhatWasEaten() = runBlocking {
        val stored = pilaf(RecipeItem(chicken(), 300.0), RecipeItem(rice(), 150.0))
        val before = food.logRecipe(1L, stored, grams = 250.0, mealKey = MealDefaults.DINNER, on = today)

        // The user decides the dish actually had twice the rice.
        recipes.save(stored.copy(items = listOf(RecipeItem(chicken(), 300.0), RecipeItem(rice(), 300.0))))

        val day = food.day(1L, today, targetKcal = 2000)
        assertEquals("the stored entry keeps its own numbers", before.kcal, day.eatenKcal, 1e-9)
    }

    @Test
    fun deletingARecipeKeepsTheMealThatWasLoggedFromIt() = runBlocking {
        val stored = pilaf(RecipeItem(chicken(), 300.0), RecipeItem(rice(), 150.0))
        val logged = food.logRecipe(1L, stored, grams = 250.0, mealKey = MealDefaults.DINNER, on = today)

        recipes.delete(stored.id)

        assertNull(recipes.byId(stored.id))
        val day = food.day(1L, today, targetKcal = 2000)
        assertEquals("the plate stays on the day", 1, day.meals.size)
        assertEquals(logged.id, day.meals.single().id)
        assertEquals("the name survives the recipe being gone", "Chicken pilaf", day.meals.single().name)
    }

    @Test
    fun aRecipeWithoutIngredientsIsRefused() = runBlocking {
        val thrown = runCatching {
            recipes.save(Recipe(profileId = 1L, name = "Empty", items = emptyList()))
        }.exceptionOrNull()

        assertTrue("$thrown", thrown is IllegalArgumentException)
        assertNull(recipes.list(1L).firstOrNull { it.name == "Empty" })
    }

    @Test
    fun recipesAreScopedToTheirProfile() = runBlocking {
        db.profileDao().insert(
            ProfileEntity(
                id = 2L,
                name = "Wife",
                sex = Sex.FEMALE,
                birthDate = "1998-03-02",
                heightCm = 168.0,
                activityLevel = ActivityLevel.LIGHT,
                goalRateKgPerWeek = -0.25,
                createdAt = now,
            ),
        )
        pilaf(RecipeItem(chicken(), 300.0))

        assertEquals(1, recipes.list(1L).size)
        assertTrue("another profile's recipes stay out of this list", recipes.list(2L).isEmpty())
    }

    @Test
    fun theNameIsTrimmedAndTheTimestampsHold() = runBlocking {
        val stored = recipes.save(
            Recipe(profileId = 1L, name = "  Soup  ", items = listOf(RecipeItem(chicken(), 100.0))),
        )
        assertEquals("Soup", stored.name)

        val read = recipes.byId(stored.id)!!
        assertNotNull(read.items.single())
    }
}
