/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.SlotType
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.gui.Renderer
import me.shedaniel.rei.api.client.gui.widgets.Widget
import me.shedaniel.rei.api.client.gui.widgets.Widgets
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.util.EntryIngredients
import net.minecraft.network.chat.Component

class CobblemonReiCategory<T: RecipeViewerRecipe>(val provider: CobblemonReiProvider<T>): DisplayCategory<CobblemonReiDisplay<T>> {
    companion object {
        private const val PADDING = 4
    }

    val theIcon = CobblemonReiIcon(provider.originalProvider.getIconResource(), provider.originalProvider.getIconDimensions().x, provider.originalProvider.getIconDimensions().y)

    override fun getCategoryIdentifier(): CategoryIdentifier<CobblemonReiDisplay<T>> = provider.categoryIdentifier
    override fun getIcon(): Renderer = theIcon
    override fun getTitle(): Component = provider.originalProvider.getName()

    // Add Padding so insides fit perfectly
    override fun getDisplayWidth(display: CobblemonReiDisplay<T>): Int = provider.originalProvider.getWidth() + PADDING * 2
    override fun getDisplayHeight(): Int = provider.originalProvider.getHeight() + PADDING * 2

    override fun setupDisplay(display: CobblemonReiDisplay<T>, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        val startX = bounds.minX + PADDING
        val startY = bounds.minY + PADDING

        widgets.add(Widgets.createCategoryBase(bounds))

        widgets.add(CobblemonReiSimpleWidget(startX, startY, display.recipe))

        display.recipe.getSlots().forEach { slot ->
            val reiSlot = Widgets.createSlot(Point(startX + slot.x, startY + slot.y))
            reiSlot.entries(EntryIngredients.ofItemStacks(slot.ingredient.ingredient.items.map { it.copyWithCount(slot.ingredient.count) }))
            reiSlot.disableBackground()
            when (slot.type) {
                SlotType.INPUT -> reiSlot.markInput()
                SlotType.CATALYST -> reiSlot.markInput()
                SlotType.OUTPUT -> reiSlot.markOutput()
            }
            widgets.add(reiSlot)
        }

        return widgets
    }
}