/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer.brewing

import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.item.crafting.brewingstand.BrewingStandRecipe
import net.minecraft.client.Minecraft
import net.minecraft.world.item.crafting.RecipeHolder

object CobblemonBrewingRecipeProvider { // This has custom implementation
    fun getRecipes(): List<RecipeHolder<BrewingStandRecipe>> {
        val recipeManger =
            Minecraft.getInstance().level?.recipeManager ?: throw IllegalStateException("Recipe manager not found")

        return recipeManger.getAllRecipesFor(CobblemonRecipeTypes.BREWING_STAND)
    }
}