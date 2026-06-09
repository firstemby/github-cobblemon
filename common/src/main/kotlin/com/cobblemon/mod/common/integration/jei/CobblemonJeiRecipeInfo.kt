/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.jei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import java.util.Optional

class CobblemonJeiRecipeInfo<E: AbstractContainerMenu, R: RecipeViewerRecipe>(val provider: CobblemonJeiProvider<R>, val originalHandler: RecipeViewerRecipeHandler<R, E>): IRecipeTransferInfo<E, R> {
    override fun getContainerClass(): Class<out E> = originalHandler.getMenuClass()
    override fun getMenuType(): Optional<MenuType<E>> = Optional.of(originalHandler.getMenuType())
    override fun getRecipeType(): RecipeType<R> = provider.recipeType

    override fun getRecipeSlots(container: E, recipe: R): List<Slot> {
        return originalHandler.getInputSlots(container)
    }

    override fun getInventorySlots(container: E, recipe: R): List<Slot> {
        return originalHandler.getInventorySlots(container)
    }

    override fun canHandle(container: E, recipe: R): Boolean {
        return originalHandler.isApplicable(recipe)
    }
}