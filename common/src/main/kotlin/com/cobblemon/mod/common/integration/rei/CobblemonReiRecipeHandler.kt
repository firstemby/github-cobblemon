/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler
import me.shedaniel.rei.api.common.entry.InputIngredient
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

class CobblemonReiRecipeHandler<E: AbstractContainerMenu>(val provider: CobblemonReiProvider<*, E>, val originalHandler: RecipeViewerRecipeHandler<E>): SimpleTransferHandler {
    override fun checkApplicable(context: TransferHandler.Context): TransferHandler.ApplicabilityResult {
        val category = (context.display as? CobblemonReiDisplay<*>)?.categoryId
        if (category == null || category != provider.categoryIdentifier || context.menu == null || !originalHandler.getMenuClass().isInstance(context.menu))
            return TransferHandler.ApplicabilityResult.createNotApplicable()
        return TransferHandler.ApplicabilityResult.createApplicable()
    }

    override fun getInputSlots(context: TransferHandler.Context): Iterable<SlotAccessor> {
        return originalHandler.getInputSlots(context.menu!! as E).map { SlotAccessor.fromSlot(it) }
    }

    override fun getInventorySlots(context: TransferHandler.Context): Iterable<SlotAccessor> {
        return originalHandler.getInventorySlots(context.menu!! as E).map { SlotAccessor.fromSlot(it) }
    }
}