/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.jei

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.integration.jei.brewing.JeiBrewingStandRecipe
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerComponentRegistry
import com.cobblemon.mod.common.integration.recipeviewer.brewing.CobblemonBrewingRecipeProvider
import com.cobblemon.mod.common.util.cobblemonResource
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.registration.IGuiHandlerRegistration
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import mezz.jei.api.registration.IRecipeTransferRegistration
import mezz.jei.api.registration.ISubtypeRegistration
import net.minecraft.resources.ResourceLocation

@JeiPlugin
class CobblemonJeiPlugin : IModPlugin {
    override fun getPluginUid(): ResourceLocation {
        return ID
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        jeiProviders.forEach { it.registerCategory(registration) }
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        jeiProviders.forEach { it.registerRecipes(registration) }

        // Cobblemon Brewing Recipes
        val jeiRecipes = CobblemonBrewingRecipeProvider.getRecipes().map { entry ->
            JeiBrewingStandRecipe(entry.value, entry.id)
        }
        registration.addRecipes(RecipeTypes.BREWING, jeiRecipes)
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        jeiProviders.forEach { it.registerRecipeCatalsysts(registration) }
    }

    override fun registerGuiHandlers(registration: IGuiHandlerRegistration) {
        jeiProviders.forEach {
            it.registerGuiHandlers(registration)
        }
    }

    override fun registerRecipeTransferHandlers(registration: IRecipeTransferRegistration) {
        jeiProviders.forEach {
            it.registerRecipeTransferHandlers(registration)
        }
    }

    override fun registerItemSubtypes(registration: ISubtypeRegistration) {
        RecipeViewerComponentRegistry.register { item, component ->
            registration.registerSubtypeInterpreter(item, CobblemonJeiComponentComparator(component))
        }
    }

    companion object {
        private val jeiProviders = RecipeViewerCategoryProvider.categoriesProviders.map {
            CobblemonJeiProvider(it)
        }

        val ID = cobblemonResource("jei_plugin")
    }
}
