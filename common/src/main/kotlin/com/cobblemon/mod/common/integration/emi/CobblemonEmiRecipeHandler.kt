/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.emi

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.handler.EmiCraftContext
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

class CobblemonEmiRecipeHandler<E: AbstractContainerMenu, R: RecipeViewerRecipe>(val provider: CobblemonEmiProvider<*>, val originalHandler: RecipeViewerRecipeHandler<R, E>): StandardRecipeHandler<E> {
    override fun getCraftingSlots(menu: E): List<Slot> {
        return originalHandler.getInputSlots(menu)
    }

    override fun getInputSources(menu: E): List<Slot> {
        val theList = originalHandler.getInventorySlots(menu).toMutableList()
        theList.addAll(originalHandler.getInputSlots(menu))
        return theList
    }

    override fun supportsRecipe(recipe: EmiRecipe): Boolean {
        return recipe.category == provider.category
    }

    override fun canCraft(recipe: EmiRecipe?, context: EmiCraftContext<E?>?): Boolean {
        return super.canCraft(recipe, context) && (recipe as? CobblemonEmiRecipe<R>)?.let { originalHandler.isApplicable(it.recipe) } ?: false
    }

    override fun craft(recipe: EmiRecipe, context: EmiCraftContext<E>): Boolean {
        (recipe as? CobblemonEmiRecipe<R>)?.let { originalHandler.additionalHandle(recipe.recipe) }
        return super.craft(recipe, context)
    }
}