/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler
import me.shedaniel.rei.api.common.entry.InputIngredient
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

class CobblemonReiRecipeHandler<E: AbstractContainerMenu, R: RecipeViewerRecipe>(val provider: CobblemonReiProvider<*>, val originalHandler: RecipeViewerRecipeHandler<R, E>): SimpleTransferHandler {
    override fun checkApplicable(context: TransferHandler.Context): TransferHandler.ApplicabilityResult {
        val display = context.display as? CobblemonReiDisplay<R>
        if (display == null || display.categoryId != provider.categoryIdentifier || context.menu == null
            || !originalHandler.getMenuClass().isInstance(context.menu) || !originalHandler.isApplicable(display.recipe))
            return TransferHandler.ApplicabilityResult.createNotApplicable()
        return TransferHandler.ApplicabilityResult.createApplicable()
    }

    override fun getInputSlots(context: TransferHandler.Context): Iterable<SlotAccessor> {
        return originalHandler.getInputSlots(context.menu!! as E).map { SlotAccessor.fromSlot(it) }
    }

    override fun getInventorySlots(context: TransferHandler.Context): Iterable<SlotAccessor> {
        return originalHandler.getInventorySlots(context.menu!! as E).map { SlotAccessor.fromSlot(it) }
    }

    override fun handle(context: TransferHandler.Context): TransferHandler.Result {
        if (context.isActuallyCrafting) {
            val display = context.display as? CobblemonReiDisplay<R>
            display?.let {
                originalHandler.additionalHandle(display.recipe, context.menu as E, context.containerScreen as AbstractContainerScreen<E>)
            }
        }
        return super.handle(context)
    }
}