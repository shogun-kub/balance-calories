package com.balancecalories.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.balancecalories.app.debug.DebugScreen
import com.balancecalories.app.food.ProductFormScreen
import com.balancecalories.app.food.ProductScreen
import com.balancecalories.app.food.ProductsScreen
import com.balancecalories.app.recipes.RecipeEditorScreen
import com.balancecalories.app.recipes.RecipeScreen
import com.balancecalories.app.recipes.RecipesScreen
import com.balancecalories.app.profile.ProfileFormScreen
import com.balancecalories.app.profiles.ProfilesScreen
import com.balancecalories.app.scanner.ScannerScreen
import com.balancecalories.app.settings.SettingsScreen
import com.balancecalories.app.today.TodayScreen
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.Recipe
import com.balancecalories.core.profile.Profile
import kotlinx.coroutines.launch

private enum class Screen { TODAY, PROFILES, FORM, SETTINGS, DEBUG, FOOD, PRODUCT, PRODUCT_FORM, SCAN, RECIPES, RECIPE, RECIPE_FORM }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }
}

/**
 * Routing is an enum and the stored "current profile" id: no Navigation Compose yet, because eight
 * destinations still do not justify the dependency.
 *
 * Which profile is shown comes from the database, not from list order. If the stored id points at a
 * profile that no longer exists, the first available one is adopted, so a deleted selection cannot leave
 * the app pointing at nothing.
 */
@Composable
private fun Root() {
    val container = (LocalContext.current.applicationContext as BalanceCaloriesApp).container
    val scope = rememberCoroutineScope()

    val allProfiles by container.profiles.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val profiles = allProfiles.filterNot { it.isArchived }
    val archivedProfiles = allProfiles.filter { it.isArchived }

    val activeId by container.settings.observeActiveProfileId()
        .collectAsStateWithLifecycle(initialValue = null)

    var screen by remember { mutableStateOf(Screen.TODAY) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var openedProduct by remember { mutableStateOf<Product?>(null) }
    var formEditing by remember { mutableStateOf<Product?>(null) }
    var openedRecipe by remember { mutableStateOf<Recipe?>(null) }
    var recipeEditing by remember { mutableStateOf<Recipe?>(null) }
    var scannedBarcode by remember { mutableStateOf<String?>(null) }

    // System back walks one step up the food stack, and returns to Today from anywhere else.
    BackHandler(enabled = screen != Screen.TODAY) {
        screen = when (screen) {
            Screen.PRODUCT, Screen.PRODUCT_FORM, Screen.SCAN -> Screen.FOOD
            Screen.RECIPE, Screen.RECIPE_FORM -> Screen.RECIPES
            else -> {
                editing = null
                Screen.TODAY
            }
        }
    }

    LaunchedEffect(profiles, activeId) {
        if (profiles.isNotEmpty() && profiles.none { it.id == activeId }) {
            container.settings.setActiveProfile(profiles.first().id)
        }
    }

    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    when {
        allProfiles.isEmpty() -> ProfileFormScreen(
            container = container,
            existing = null,
            onSaved = { screen = Screen.TODAY },
        )

        screen == Screen.PROFILES -> ProfilesScreen(
            container = container,
            profiles = profiles,
            archivedProfiles = archivedProfiles,
            activeProfileId = active?.id,
            // Archiving the last visible profile opens the archive instead of hiding the only way back.
            initiallyShowArchived = profiles.isEmpty(),
            onAddProfile = {
                editing = null
                screen = Screen.FORM
            },
            onEditProfile = {
                editing = it
                screen = Screen.FORM
            },
            onSelectProfile = { profile ->
                scope.launch { container.settings.setActiveProfile(profile.id) }
                screen = Screen.TODAY
            },
            onBack = { screen = Screen.TODAY },
        )

        screen == Screen.FORM -> ProfileFormScreen(
            container = container,
            existing = editing,
            onSaved = {
                editing = null
                screen = Screen.TODAY
            },
        )

        screen == Screen.SETTINGS && active != null -> SettingsScreen(
            container = container,
            profile = active,
            onBack = { screen = Screen.TODAY },
            onOpenDebug = { screen = Screen.DEBUG },
        )

        screen == Screen.DEBUG && active != null -> DebugScreen(
            container = container,
            profile = active,
            onBack = { screen = Screen.SETTINGS },
        )

        screen == Screen.RECIPES && active != null -> RecipesScreen(
            container = container,
            profileId = active.id,
            onBack = { screen = Screen.TODAY },
            onOpen = { recipe ->
                openedRecipe = recipe
                screen = Screen.RECIPE
            },
            onNew = {
                recipeEditing = null
                screen = Screen.RECIPE_FORM
            },
        )

        screen == Screen.RECIPE && openedRecipe != null && active != null -> RecipeScreen(
            container = container,
            profileId = active.id,
            recipe = openedRecipe!!,
            onBack = { screen = Screen.RECIPES },
            onEdit = {
                recipeEditing = openedRecipe
                screen = Screen.RECIPE_FORM
            },
            onLogged = {
                openedRecipe = null
                screen = Screen.TODAY
            },
            onDeleted = {
                openedRecipe = null
                screen = Screen.RECIPES
            },
        )

        screen == Screen.RECIPE_FORM && active != null -> RecipeEditorScreen(
            container = container,
            profileId = active.id,
            existing = recipeEditing,
            onSaved = { saved ->
                openedRecipe = saved
                recipeEditing = null
                // Straight to the dish that was just saved: its numbers are the point of the exercise.
                screen = Screen.RECIPE
            },
            onCancel = { screen = if (recipeEditing != null) Screen.RECIPE else Screen.RECIPES },
        )

        screen == Screen.FOOD && active != null -> ProductsScreen(
            container = container,
            profileId = active.id,
            onBack = { screen = Screen.TODAY },
            onPick = { product ->
                openedProduct = product
                screen = Screen.PRODUCT
            },
            onAddManual = {
                openedProduct = null
                formEditing = null
                scannedBarcode = null
                screen = Screen.PRODUCT_FORM
            },
            onScan = { screen = Screen.SCAN },
        )

        screen == Screen.SCAN -> ScannerScreen(
            container = container,
            onBack = { screen = Screen.FOOD },
            onProduct = { product ->
                openedProduct = product
                screen = Screen.PRODUCT
            },
            onAddManually = { barcode ->
                formEditing = null
                scannedBarcode = barcode
                screen = Screen.PRODUCT_FORM
            },
        )

        screen == Screen.PRODUCT && openedProduct != null && active != null -> ProductScreen(
            product = openedProduct!!,
            container = container,
            profileId = active.id,
            onBack = { screen = Screen.FOOD },
            onEdit = {
                formEditing = openedProduct
                scannedBarcode = null
                screen = Screen.PRODUCT_FORM
            },
            onLogged = {
                openedProduct = null
                formEditing = null
                screen = Screen.TODAY
            },
        )

        screen == Screen.PRODUCT_FORM && active != null -> ProductFormScreen(
            container = container,
            existing = formEditing,
            initialBarcode = scannedBarcode,
            onSaved = { saved ->
                if (formEditing != null) {
                    // Correcting a product is not logging it: show the corrected numbers, then let the user
                    // decide the portion. Straight to Today would hide the change the user just made.
                    openedProduct = saved
                    formEditing = null
                    screen = Screen.PRODUCT
                } else {
                    // Straight to Today: a product typed here was typed to be logged, not to be filed away.
                    screen = Screen.TODAY
                }
            },
            onCancel = { screen = if (formEditing != null) Screen.PRODUCT else Screen.FOOD },
        )

        // Everything is archived: show the list with the archive open, so restoring is one tap away
        // instead of dropping the user into onboarding as if profile had never existed.
        active == null -> ProfilesScreen(
            container = container,
            profiles = profiles,
            archivedProfiles = archivedProfiles,
            activeProfileId = null,
            initiallyShowArchived = true,
            onAddProfile = {
                editing = null
                screen = Screen.FORM
            },
            onEditProfile = {
                editing = it
                screen = Screen.FORM
            },
            onSelectProfile = { profile ->
                scope.launch { container.settings.setActiveProfile(profile.id) }
                screen = Screen.TODAY
            },
            onBack = { screen = Screen.TODAY },
        )

        else -> TodayScreen(
            profile = active,
            container = container,
            onOpenRecipes = { screen = Screen.RECIPES },
            onOpenFood = { screen = Screen.FOOD },
            onOpenProfiles = { screen = Screen.PROFILES },
            onOpenSettings = { screen = Screen.SETTINGS },
        )
    }
}
