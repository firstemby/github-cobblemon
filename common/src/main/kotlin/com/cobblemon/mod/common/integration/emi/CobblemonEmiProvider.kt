package com.cobblemon.mod.common.integration.emi

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.stack.EmiStack

class CobblemonEmiProvider<T: RecipeViewerRecipe>(val originalProvider: RecipeViewerCategoryProvider<T>) {
    val category = CobblemonEmiCategory(this)

    fun register(registry: EmiRegistry) {
        registry.addCategory(category)

        originalProvider.getWorkstations().forEach {
            registry.addWorkstation(category, EmiStack.of(it))
        }

        originalProvider.getRecipes().forEach {
            registry.addRecipe(CobblemonEmiRecipe(this, it))
        }
    }
}