package com.balancecalories.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.balancecalories.app.backup.BackupScreen
import com.balancecalories.app.debug.DebugScreen
import com.balancecalories.app.food.ProductFormScreen
import com.balancecalories.app.food.ProductScreen
import com.balancecalories.app.food.ProductsScreen
import com.balancecalories.app.history.HistoryScreen
import com.balancecalories.app.profile.ProfileFormScreen
import com.balancecalories.app.profiles.ProfilesScreen
import com.balancecalories.app.recipes.RecipeEditorScreen
import com.balancecalories.app.recipes.RecipeScreen
import com.balancecalories.app.recipes.RecipesScreen
import com.balancecalories.app.scanner.ScannerScreen
import com.balancecalories.app.settings.SettingsScreen
import com.balancecalories.app.today.TodayScreen
import com.balancecalories.app.ui.BalanceTheme
import com.balancecalories.app.weight.WeightScreen
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.Recipe
import com.balancecalories.core.profile.Profile
import kotlinx.coroutines.launch

/**
 * The four places the app has.
 *
 * A tab is somewhere you *return to*; settings, profiles and the product/recipe detail screens are places you
 * *visit*, so they are pushed on top of the current tab instead of taking a tab slot of their own (ADR-027).
 */
private enum class Tab { TODAY, FOOD, HISTORY, WEIGHT }

/** Screens pushed over a tab. One is enough: nothing in this app is three levels deep. */
private enum class Overlay { PROFILES, PROFILE_FORM, SETTINGS, BACKUP, DEBUG, PRODUCT, PRODUCT_FORM, SCAN, RECIPES, RECIPE, RECIPE_FORM }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BalanceTheme {
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

    // Both queries answer asynchronously, and "not answered yet" must not look like "nothing there": an
    // empty list is indistinguishable from a phone with no profile, and a null id from a phone with no
    // selection. Acting on either flashed the create-profile form on every cold start and, worse, let the
    // repair below overwrite the stored profile with the first row in the table (P-39).
    var loadedProfiles by remember { mutableStateOf<List<Profile>?>(null) }
    var loadedActiveId by remember { mutableStateOf<ActiveProfileRead>(ActiveProfileRead.Loading) }

    LaunchedEffect(Unit) {
        container.profiles.observeAll().collect { loadedProfiles = it }
    }
    LaunchedEffect(Unit) {
        container.settings.observeActiveProfileId().collect { loadedActiveId = ActiveProfileRead.Loaded(it) }
    }

    val allProfiles = loadedProfiles.orEmpty()
    val profiles = allProfiles.filterNot { it.isArchived }
    val archivedProfiles = allProfiles.filter { it.isArchived }
    val settingsLoaded = loadedActiveId is ActiveProfileRead.Loaded
    val activeId = (loadedActiveId as? ActiveProfileRead.Loaded)?.id

    // Saveable, so a phone that kills the app overnight does not send the person somewhere else.
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var openedProduct by remember { mutableStateOf<Product?>(null) }
    var formEditing by remember { mutableStateOf<Product?>(null) }
    var openedRecipe by remember { mutableStateOf<Recipe?>(null) }
    var recipeEditing by remember { mutableStateOf<Recipe?>(null) }
    var scannedBarcode by remember { mutableStateOf<String?>(null) }

    // Back pops one overlay, then returns to Today from another tab, then leaves the app. Tab switches do not
    // stack: a person who taps Еда, История, Еда and then back expects Today, not a tour of their taps.
    BackHandler(enabled = overlay != null || tab != Tab.TODAY) {
        val current = overlay
        if (current == null) {
            tab = Tab.TODAY
        } else {
            overlay = when (current) {
                // Product and scanning belong to the Еда tab; stepping back out of them lands there.
                Overlay.PRODUCT, Overlay.PRODUCT_FORM, Overlay.SCAN -> {
                    tab = Tab.FOOD
                    null
                }
                Overlay.RECIPE, Overlay.RECIPE_FORM -> Overlay.RECIPES
                Overlay.BACKUP, Overlay.DEBUG -> Overlay.SETTINGS
                else -> {
                    editing = null
                    null
                }
            }
        }
    }

    // Repair, not selection: runs only once the stored value is actually known, and only when it points at
    // a profile that no longer exists.
    LaunchedEffect(profiles, activeId, settingsLoaded) {
        if (settingsLoaded && profiles.isNotEmpty()) {
            val repaired = repairedActiveId(activeId, profiles.map { it.id })
            if (repaired != null && repaired != activeId) {
                container.settings.setActiveProfile(repaired)
            }
        }
    }

    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    when {
        // Nothing shown until both reads answer: one frame of blank beats a frame of the wrong screen.
        loadedProfiles == null || !settingsLoaded -> Box(modifier = Modifier.fillMaxSize())

        // No profile at all: the only thing to do is create one, so the tabs would be four empty rooms.
        allProfiles.isEmpty() -> ProfileFormScreen(
            container = container,
            existing = null,
            onSaved = { tab = Tab.TODAY },
        )

        // Onboarding and profile management are the whole screen when there is nobody to show data for.
        active == null -> ProfilesScreen(
            container = container,
            profiles = profiles,
            archivedProfiles = archivedProfiles,
            activeProfileId = null,
            initiallyShowArchived = true,
            onAddProfile = {
                editing = null
                overlay = Overlay.PROFILE_FORM
            },
            onEditProfile = {
                editing = it
                overlay = Overlay.PROFILE_FORM
            },
            onSelectProfile = { profile ->
                scope.launch { container.settings.setActiveProfile(profile.id) }
                tab = Tab.TODAY
            },
            onBack = { tab = Tab.TODAY },
        )

        // Overlays: one screen on top of the current tab, with the tab bar hidden while it is open.
        overlay == Overlay.PROFILES -> ProfilesScreen(
            container = container,
            profiles = profiles,
            archivedProfiles = archivedProfiles,
            activeProfileId = active?.id,
            initiallyShowArchived = profiles.isEmpty(),
            onAddProfile = {
                editing = null
                overlay = Overlay.PROFILE_FORM
            },
            onEditProfile = {
                editing = it
                overlay = Overlay.PROFILE_FORM
            },
            onSelectProfile = { profile ->
                scope.launch { container.settings.setActiveProfile(profile.id) }
                overlay = null
                tab = Tab.TODAY
            },
            onBack = { overlay = null },
        )

        overlay == Overlay.PROFILE_FORM -> ProfileFormScreen(
            container = container,
            existing = editing,
            onSaved = {
                editing = null
                overlay = null
            },
        )

        overlay == Overlay.SETTINGS -> SettingsScreen(
            container = container,
            profile = active,
            onBack = { overlay = null },
            onOpenDebug = { overlay = Overlay.DEBUG },
            onOpenData = { overlay = Overlay.BACKUP },
            onOpenProfiles = { overlay = Overlay.PROFILES },
        )

        overlay == Overlay.BACKUP -> BackupScreen(
            container = container,
            profile = active,
            onBack = { overlay = Overlay.SETTINGS },
        )

        overlay == Overlay.DEBUG -> DebugScreen(
            container = container,
            profile = active,
            onBack = { overlay = Overlay.SETTINGS },
        )

        overlay == Overlay.RECIPES -> RecipesScreen(
            container = container,
            profileId = active.id,
            onBack = { overlay = null },
            onOpen = { recipe ->
                openedRecipe = recipe
                overlay = Overlay.RECIPE
            },
            onNew = {
                recipeEditing = null
                overlay = Overlay.RECIPE_FORM
            },
        )

        overlay == Overlay.RECIPE && openedRecipe != null -> RecipeScreen(
            container = container,
            profileId = active.id,
            recipe = openedRecipe!!,
            onBack = { overlay = Overlay.RECIPES },
            onEdit = {
                recipeEditing = openedRecipe
                overlay = Overlay.RECIPE_FORM
            },
            onLogged = {
                openedRecipe = null
                overlay = null
                tab = Tab.TODAY
            },
            onDeleted = {
                openedRecipe = null
                overlay = Overlay.RECIPES
            },
        )

        overlay == Overlay.RECIPE_FORM -> RecipeEditorScreen(
            container = container,
            profileId = active.id,
            existing = recipeEditing,
            onSaved = { saved ->
                openedRecipe = saved
                val wasEditing = recipeEditing != null
                recipeEditing = null
                // Straight to the dish that was just saved: its numbers are the point of the exercise.
                overlay = if (wasEditing) Overlay.RECIPE else Overlay.RECIPE
            },
            onCancel = { overlay = if (recipeEditing != null) Overlay.RECIPE else Overlay.RECIPES },
        )

        overlay == Overlay.SCAN -> ScannerScreen(
            container = container,
            onBack = { overlay = null },
            onProduct = { product ->
                openedProduct = product
                overlay = Overlay.PRODUCT
            },
            onAddManually = { barcode ->
                formEditing = null
                scannedBarcode = barcode
                overlay = Overlay.PRODUCT_FORM
            },
        )

        overlay == Overlay.PRODUCT && openedProduct != null -> ProductScreen(
            product = openedProduct!!,
            container = container,
            profileId = active.id,
            onBack = { overlay = null },
            onEdit = {
                formEditing = openedProduct
                scannedBarcode = null
                overlay = Overlay.PRODUCT_FORM
            },
            onLogged = {
                openedProduct = null
                formEditing = null
                overlay = null
                tab = Tab.TODAY
            },
        )

        overlay == Overlay.PRODUCT_FORM -> ProductFormScreen(
            container = container,
            existing = formEditing,
            initialBarcode = scannedBarcode,
            onSaved = { saved ->
                if (formEditing != null) {
                    // Correcting a product is not logging it: show the corrected numbers, then let the user
                    // decide the portion. Straight to Today would hide the change the user just made.
                    openedProduct = saved
                    formEditing = null
                    overlay = Overlay.PRODUCT
                } else {
                    // A product typed here was typed in order to be eaten, so the next screen is its portion:
                    // grams, meal, and a Log button, with Back meaning "keep the product, drop the meal". Saving
                    // it into the catalogue and stopping there left the person to find their own product again
                    // in yesterday's list (ADR-035).
                    openedProduct = saved
                    formEditing = null
                    overlay = Overlay.PRODUCT
                }
            },
            onCancel = {
                overlay = if (formEditing != null) Overlay.PRODUCT else null
            },
        )

        // Otherwise: the tabs.
        else -> TabScaffold(
            tab = tab,
            onSelectTab = { tab = it },
            profile = active,
            onOpenSettings = { overlay = Overlay.SETTINGS },
        ) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    profile = active,
                    container = container,
                    onOpenFood = { tab = Tab.FOOD },
                )

                Tab.FOOD -> ProductsScreen(
                    container = container,
                    profileId = active.id,
                    onPick = { product ->
                        openedProduct = product
                        overlay = Overlay.PRODUCT
                    },
                    onAddManual = {
                        openedProduct = null
                        formEditing = null
                        scannedBarcode = null
                        overlay = Overlay.PRODUCT_FORM
                    },
                    onScan = { overlay = Overlay.SCAN },
                    onOpenRecipes = { overlay = Overlay.RECIPES },
                )

                Tab.HISTORY -> HistoryScreen(
                    profile = active,
                    container = container,
                )

                Tab.WEIGHT -> WeightScreen(
                    container = container,
                    profile = active,
                )
            }
        }
    }
}

/**
 * The frame every tab shares: the current profile's name, a way into settings, and the four destinations.
 *
 * The bar is drawn here rather than inside each screen so that a tab cannot appear without it — a screen that
 * forgot the bar would become a dead end, and the bug would only show up on the tab nobody was testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabScaffold(
    tab: Tab,
    onSelectTab: (Tab) -> Unit,
    profile: Profile,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    // A label only earns its place if it fits on one line. At a 200% font scale "Сегодня" needs more width than
    // a quarter of the bar, and a squeezed Text does not ellipsize — it breaks inside the word, which is how the
    // previous navigation row rendered "Настройки" as a vertical column of letters. Above the threshold the bar
    // becomes icon-only and the icons carry the names for TalkBack instead.
    val showLabels = LocalDensity.current.fontScale < 1.5f
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = profile.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == tab,
                        onClick = { onSelectTab(destination) },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes()),
                                // With no label the icon is the only name a screen reader gets.
                                contentDescription = if (showLabels) {
                                    null
                                } else {
                                    stringResource(destination.labelRes())
                                },
                            )
                        },
                        label = if (showLabels) {
                            { Text(stringResource(destination.labelRes()), maxLines = 1) }
                        } else {
                            null
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
private fun Tab.labelRes(): Int = when (this) {
    Tab.TODAY -> R.string.nav_today
    Tab.FOOD -> R.string.nav_food
    Tab.HISTORY -> R.string.nav_history
    Tab.WEIGHT -> R.string.nav_weight
}

/** Null content description on purpose: the label beside it already names the destination. */
private fun Tab.iconRes(): Int = when (this) {
    Tab.TODAY -> R.drawable.ic_tab_today
    Tab.FOOD -> R.drawable.ic_tab_food
    Tab.HISTORY -> R.drawable.ic_tab_history
    Tab.WEIGHT -> R.drawable.ic_tab_weight
}