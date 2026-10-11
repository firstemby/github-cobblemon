/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.emi

import com.cobblemon.mod.common.integration.recipeviewer.RecipeSlot
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.SlotType
import dev.emi.emi.EmiUtil
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.render.EmiTooltipComponents
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.GeneratedSlotWidget
import dev.emi.emi.api.widget.SlotWidget
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.Ingredient
import java.util.Random

class CobblemonEmiRecipe<T: RecipeViewerRecipe>(val provider: CobblemonEmiProvider<T>, val recipe: T): EmiRecipe {
    private val uniq = EmiUtil.RANDOM.nextInt()

    override fun getCategory(): EmiRecipeCategory = provider.category
    override fun getId(): ResourceLocation = recipe.getId()
    override fun getDisplayWidth(): Int = provider.originalProvider.getWidth()
    override fun getDisplayHeight(): Int = provider.originalProvider.getHeight()

    private val ingredientHash = hashMapOf<Ingredient, EmiIngredient>() // This fixes the issue with some stuff
    private val watchedData = mutableListOf<EmiIngredient>()

    override fun getInputs(): List<EmiIngredient> {
        return recipe.getInputs().map { ingredientHash.getOrPut(it.ingredient) { EmiIngredient.of(it.ingredient, it.count.toLong()) } }
    }

    override fun getOutputs(): List<EmiStack?> {
        return recipe.getOutputs().map { EmiStack.of(it) }
    }

    override fun addWidgets(widgets: WidgetHolder) {
        widgets.add(CobblemonEmiSimpleWidget(provider, recipe))

        watchedData.clear()
        recipe.getSlots().forEach {
            widgets.add(getSlot(it))
        }
    }

    override fun supportsRecipeTree(): Boolean = recipe.supportsRecipeTree()

    override fun getCatalysts(): List<EmiIngredient> {
        return recipe.getOptionalInputs().map { EmiIngredient.of(it.ingredient) }
    }

    private fun getSlot(mySlot: RecipeSlot): SlotWidget {
        val slot: SlotWidget
        val ingredient = ingredientHash.getOrPut(mySlot.ingredient.ingredient) { EmiIngredient.of(mySlot.ingredient.ingredient, mySlot.ingredient.count.toLong()) }

        if (mySlot.generatedStack != null) { // Mutually Exclusive
            slot = GeneratedSlotWidget({r -> EmiStack.of(mySlot.generatedStack(calculateWatchedData(r).map { it.displayed.itemStack }))},
                uniq, mySlot.x - 1, mySlot.y - 1)
        } else if (mySlot.watchIngredient) {
            watchedData.add(ingredient)
            val i = watchedData.size - 1
            slot = GeneratedSlotWidget({r -> getWatchedDataStack(r, i)}, uniq, mySlot.x - 1, mySlot.y - 1)
            val function = { EmiTooltipComponents.getIngredientTooltipComponent(ingredient.emiStacks) }
            slot.appendTooltip(function)
        } else {
            slot = SlotWidget(ingredient, mySlot.x - 1, mySlot.y - 1)
        }

        slot.drawBack(false)
        if (mySlot.type == SlotType.OUTPUT) slot.recipeContext(this)

        return slot
    }

    private fun calculateWatchedData(r: Random): List<CobblemonEmiIngredient<EmiStack>> {
        val list = mutableListOf<CobblemonEmiIngredient<EmiStack>>()
        watchedData.forEach {
            val stack = it.emiStacks[r.nextInt(it.emiStacks.size)]
            list.add(CobblemonEmiIngredient(it, stack))
        }
        return list
    }

    private fun getWatchedDataStack(r: Random, i: Int): CobblemonEmiIngredient<EmiStack> {
        return calculateWatchedData(r)[i]
    }
}