/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.emi

import com.cobblemon.mod.common.integration.emi.brewing.EmiCobblemonBrewingRecipe
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.brewing.CobblemonBrewingRecipeProvider
import dev.emi.emi.api.EmiEntrypoint
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry

@EmiEntrypoint
class CobblemonEmiPlugin: EmiPlugin {
    companion object {
        val emiProviders = RecipeViewerCategoryProvider.categoriesProviders.map {
            CobblemonEmiProvider(it)
        }
    }

    override fun register(registry: EmiRegistry) {
        emiProviders.forEach {
            it.register(registry)
        }

        // Cobblemon Brewing Recipes
        CobblemonBrewingRecipeProvider.getRecipes().forEach { entry ->
            registry.addRecipe(EmiCobblemonBrewingRecipe(entry.value, entry.id))
        }
    }
}