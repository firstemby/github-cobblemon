/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer.berry

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.integration.recipeviewer.RecipeIngredient
import com.cobblemon.mod.common.integration.recipeviewer.RecipeSlot
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.SlotType
import com.cobblemon.mod.common.item.berry.BerryItem
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient

//Dont make these ItemStacks, they do not have consistent hash codes, breaks stuff
data class BerryMutationRecipe(
    val berryOne: BerryItem,
    val berryTwo: BerryItem,
    val berryResult: BerryItem
): RecipeViewerRecipe {
    companion object {
        val BACKGROUND = cobblemonResource("textures/gui/recipeviewer/berry_mutation.png")
    }

    override fun getId() = cobblemonResource("/berry_mutation_" +
            "${berryIdentifier(berryResult)}_${berryIdentifier(berryOne)}_${berryIdentifier(berryTwo)}")

    override fun getInputs(): List<RecipeIngredient> {
        return listOf(RecipeIngredient(Ingredient.of(berryOne)), RecipeIngredient(Ingredient.of(berryTwo)))
    }

    override fun getOutputs(): List<ItemStack> {
        return listOf(berryResult.defaultInstance)
    }

    override fun supportsRecipeTree(): Boolean = false

    override fun renderRecipe(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        blitk(
            matrixStack = context.pose(),
            texture = BACKGROUND,
            x = 0,
            y = 0,
            width = 125,
            height = 18
        )
    }

    override fun getSlots(): List<RecipeSlot> {
        val list = mutableListOf<RecipeSlot>()
        list.add(RecipeSlot(1, 1, SlotType.INPUT, RecipeIngredient(Ingredient.of(berryOne))))
        list.add(RecipeSlot(50, 1, SlotType.INPUT, RecipeIngredient(Ingredient.of(berryTwo))))
        list.add(RecipeSlot(108, 1, SlotType.OUTPUT, RecipeIngredient(Ingredient.of(berryResult))))
        return list
    }

    private fun berryIdentifier(berry: BerryItem): String {
        return berry.berry()?.identifier?.path ?: ""
    }
}