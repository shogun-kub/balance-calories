package com.balancecalories.app.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.AppContainer
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.R
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.Recipe
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.RecipeMath

/**
 * The dishes this profile cooks.
 *
 * Nutrition is not stored on the recipe: it is derived from the ingredients on every read, so correcting an
 * ingredient's label fixes every recipe that uses it. What was already eaten is protected separately by the
 * entry snapshot, which is why this screen can show live numbers without endangering history.
 */
@Composable
fun RecipesScreen(
    container: AppContainer,
    profileId: Long,
    onOpen: (Recipe) -> Unit,
    onNew: () -> Unit,
) {
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }

    LaunchedEffect(profileId) {
        recipes = runCatching { container.recipes.list(profileId) }.getOrDefault(emptyList())
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Recipes are the second tab of "Еда", so this screen has no back button and no title of its own: the tab
        // names it, and a second copy of the word above the list made the header read like a heading inside a tab.
        TextButton(onClick = onNew) { Text(stringResource(R.string.recipe_new)) }

        if (recipes.isEmpty()) {
            Text(text = stringResource(R.string.recipe_empty), style = MaterialTheme.typography.titleSmall)
            Text(text = stringResource(R.string.recipe_empty_note), style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(recipes, key = { it.id }) { recipe ->
                RecipeRow(recipe = recipe, onClick = { onOpen(recipe) })
            }
        }
    }
}

@Composable
private fun RecipeRow(recipe: Recipe, onClick: () -> Unit) {
    val totals = RecipeMath.totals(recipe.items)
    val basis = RecipeMath.weightBasis(totals, recipe.cookedWeightG)
    val perServing = RecipeMath.perServing(totals, basis, recipe.servings)

    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.a11y_open), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
    ) {
        Text(text = recipe.name, style = MaterialTheme.typography.bodyLarge)
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = perServing?.kcal?.let { stringResource(R.string.recipe_portion_kcal, it) }
                    ?: stringResource(R.string.product_no_energy),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.recipe_ingredients) + ": " + recipe.items.size,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}