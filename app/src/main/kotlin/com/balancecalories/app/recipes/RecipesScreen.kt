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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.core.food.Recipe
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
    onBack: () -> Unit,
    onOpen: (Recipe) -> Unit,
    onNew: () -> Unit,
) {
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }

    LaunchedEffect(profileId) {
        recipes = runCatching { container.recipes.list(profileId) }.getOrDefault(emptyList())
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(text = stringResource(R.string.recipes_title), style = MaterialTheme.typography.titleLarge)
        }

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

    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(text = recipe.name, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
