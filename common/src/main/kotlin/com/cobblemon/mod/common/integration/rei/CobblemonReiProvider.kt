/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.client.gui.cookingpot.CookingPotScreen
import com.cobblemon.mod.common.client.gui.tmmachine.TMMachineScreen
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.world.inventory.AbstractContainerMenu

class CobblemonReiProvider<T: RecipeViewerRecipe, E: AbstractContainerMenu>(val originalProvider: RecipeViewerCategoryProvider<T, E>) {
    val categoryIdentifier: CategoryIdentifier<CobblemonReiDisplay<T>> = CategoryIdentifier.of<CobblemonReiDisplay<T>>(originalProvider.id)

    fun registerDisplay(registry: DisplayRegistry) {
        originalProvider.getRecipes().forEach {
            registry.add(CobblemonReiDisplay(it, categoryIdentifier))
        }
    }

    fun registerCategory(registry: CategoryRegistry) {
        registry.add(CobblemonReiCategory(this))
        registry.addWorkstations(categoryIdentifier, *originalProvider.getWorkstations().map { EntryStacks.of(it) }.toTypedArray())
    }

    fun registerScreens(registry: ScreenRegistry) {
        originalProvider.getClickArea()?.let { registry.registerContainerClickArea(Rectangle(it.x, it.y, it.width, it.height), it.containerScreen, categoryIdentifier) }
    }

    fun registerTransferHandlers(registry: TransferHandlerRegistry) {
        originalProvider.recipeHandler()?.let { registry.register(CobblemonReiRecipeHandler(this,it)) }
    }
}