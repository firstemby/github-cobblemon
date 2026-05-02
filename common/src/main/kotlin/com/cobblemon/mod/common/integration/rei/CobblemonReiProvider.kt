package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.util.EntryStacks

class CobblemonReiProvider<T: RecipeViewerRecipe>(val originalProvider: RecipeViewerCategoryProvider<T>) {
    val categoryIdentifier: CategoryIdentifier<CobblemonReiDisplay<T>> = CategoryIdentifier.of<CobblemonReiDisplay<T>>(originalProvider.id)

    fun registerDisplay(registry: DisplayRegistry) {
        originalProvider.getRecipes().forEach {
            registry.add(CobblemonReiDisplay(it, categoryIdentifier))
        }
    }

    fun registerCategory(registry: CategoryRegistry) {
        registry.add(CobblemonReiCategory(this))
        registry.addWorkstations(categoryIdentifier, *originalProvider.getWorkstations().map { EntryStacks.of(it) }.toTypedArray())
    }
}