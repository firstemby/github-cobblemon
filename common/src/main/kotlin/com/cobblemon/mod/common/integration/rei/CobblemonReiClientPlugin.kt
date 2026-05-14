/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.brewing.CobblemonBrewingRecipeProvider
import com.cobblemon.mod.common.integration.rei.brewing.ReiCobblemonBrewingDisplay
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.entry.CollapsibleEntryRegistry
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry

open class CobblemonReiClientPlugin: REIClientPlugin {
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

    override fun registerScreens(registry: ScreenRegistry) {
        reiProviders.forEach {
            it.registerScreens(registry)
        }
    }

    override fun registerTransferHandlers(registry: TransferHandlerRegistry) {
        reiProviders.forEach {
            it.registerTransferHandlers(registry)
        }
    }
}