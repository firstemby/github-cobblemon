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
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.transfer.IRecipeTransferError
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler
import mezz.jei.api.registration.IRecipeTransferRegistration
import mezz.jei.api.runtime.IRecipesGui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import java.util.Optional

class CobblemonJeiRecipeHandler<E: AbstractContainerMenu, R: RecipeViewerRecipe>(val provider: CobblemonJeiProvider<R>, val originalHandler: RecipeViewerRecipeHandler<R, E>, registry: IRecipeTransferRegistration): IRecipeTransferHandler<E, R> {
    override fun getContainerClass(): Class<out E> = originalHandler.getMenuClass()
    override fun getMenuType(): Optional<MenuType<E>> = Optional.of(originalHandler.getMenuType())
    override fun getRecipeType(): RecipeType<R> = provider.recipeType

    val recipeInfoHandler = registry.transferHelper.createUnregisteredRecipeTransferHandler(CobblemonJeiRecipeInfo(provider, originalHandler))

    override fun transferRecipe(
        container: E,
        recipe: R,
        recipeSlots: IRecipeSlotsView,
        player: Player,
        maxTransfer: Boolean,
        doTransfer: Boolean
    ): IRecipeTransferError? {
        if (doTransfer) {
            val screen = Minecraft.getInstance().screen
            val containerScreen = when (screen) {
                is IRecipesGui -> if (screen.parentScreen.isPresent) screen.parentScreen.get() as? AbstractContainerScreen<E> else null
                is AbstractContainerScreen<*> -> screen as? AbstractContainerScreen<E>
                else -> null
            }
            originalHandler.additionalHandle(recipe, container, containerScreen)
        }
        return recipeInfoHandler.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer)
    }
}