package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.brewing.CobblemonBrewingRecipeProvider
import com.cobblemon.mod.common.integration.rei.brewing.ReiCobblemonBrewingDisplay
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry

class CobblemonReiClientPlugin: REIClientPlugin {
    companion object {
        private val reiProviders = RecipeViewerCategoryProvider.categoriesProviders.map {
            CobblemonReiProvider(it)
        }
    }

    override fun registerDisplays(registry: DisplayRegistry) {
        reiProviders.forEach {
            it.registerDisplay(registry)
        }

        // Cobblemon Brewing Recipes
        CobblemonBrewingRecipeProvider.getRecipes().forEach {
            registry.add(ReiCobblemonBrewingDisplay(it.value, it.id))
        }
    }

    override fun registerCategories(registry: CategoryRegistry) {
        reiProviders.forEach {
            it.registerCategory(registry)
        }
    }
}