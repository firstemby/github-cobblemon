/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.emi

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.SlotWidget
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.Ingredient

class CobblemonEmiRecipe<T: RecipeViewerRecipe>(val provider: CobblemonEmiProvider<T, *>, val recipe: T): EmiRecipe {
    override fun getCategory(): EmiRecipeCategory = provider.category
    override fun getId(): ResourceLocation = recipe.getId()
    override fun getDisplayWidth(): Int = provider.originalProvider.getWidth()
    override fun getDisplayHeight(): Int = provider.originalProvider.getHeight()

    private val ingredientHash = hashMapOf<Ingredient, EmiIngredient>() // This fixes the issue with some stuff

    override fun getInputs(): List<EmiIngredient> {
        return recipe.getInputs().map { ingredientHash.getOrPut(it) { EmiIngredient.of(it) } }
    }

    override fun getOutputs(): List<EmiStack?> {
        return recipe.getOutputs().map { EmiStack.of(it) }
    }

    override fun addWidgets(widgets: WidgetHolder) {
        widgets.add(CobblemonEmiSimpleWidget(provider, recipe))

        recipe.getSlots().forEach {
            val ingredient = ingredientHash.getOrPut(it.ingredient) { EmiIngredient.of(it.ingredient) }
            val emiSlot = SlotWidget(ingredient, it.x - 1, it.y - 1) //X, Y coordinate system slightly offset
            emiSlot.drawBack(false)
            if (it.type == RecipeViewerCategoryProvider.SlotType.OUTPUT) emiSlot.recipeContext(this)
            widgets.add(emiSlot)
        }
    }

    override fun supportsRecipeTree(): Boolean = recipe.supportsRecipeTree()

    override fun getCatalysts(): List<EmiIngredient> {
        return recipe.getOptionalInputs().map { EmiIngredient.of(it) }
    }
}