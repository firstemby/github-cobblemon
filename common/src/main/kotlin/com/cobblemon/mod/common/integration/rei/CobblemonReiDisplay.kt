/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.InputIngredient
import me.shedaniel.rei.api.common.util.EntryIngredients
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import java.util.Optional

class CobblemonReiDisplay<T: RecipeViewerRecipe>(val recipe: T, val categoryId: CategoryIdentifier<*>): Display {
    override fun getCategoryIdentifier(): CategoryIdentifier<*> = categoryId

    override fun getInputEntries(): List<EntryIngredient> {
        val entries = recipe.getInputs().map { EntryIngredients.ofIngredient(it) }.toMutableList()
        entries.addAll(recipe.getOptionalInputs().map { EntryIngredients.ofIngredient(it) })
        return entries
    }

    override fun getOutputEntries(): List<EntryIngredient> {
        return recipe.getOutputs().map { EntryIngredients.of(it) }
    }

    override fun getRequiredEntries(): List<EntryIngredient> {
        return recipe.getInputs().map { EntryIngredients.ofIngredient(it) }
    }

    override fun getDisplayLocation(): Optional<ResourceLocation> = Optional.of(recipe.getId())

    override fun getInputIngredients(
        menu: AbstractContainerMenu?,
        player: Player?
    ): List<InputIngredient<EntryStack<*>>> {
        return recipe.getInputs().mapIndexed { index, ingredient -> InputIngredient.of(index, EntryIngredients.ofIngredient(ingredient)) }
    }
}