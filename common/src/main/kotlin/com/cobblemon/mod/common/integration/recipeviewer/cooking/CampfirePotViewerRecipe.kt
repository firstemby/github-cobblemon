/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer.cooking

import com.cobblemon.mod.common.api.cooking.Seasonings
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.item.crafting.CookingPotRecipeBase
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.CRAFTING_GRID_WIDTH
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.SEASONING_SLOTS
import com.cobblemon.mod.common.integration.recipeviewer.RecipeIngredient
import com.cobblemon.mod.common.integration.recipeviewer.RecipeSlot
import com.cobblemon.mod.common.integration.recipeviewer.SlotType
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics

class CampfirePotViewerRecipe(val theId: ResourceLocation, val originalRecipe: CookingPotRecipeBase): RecipeViewerRecipe {
    companion object {
        val EXAMPLE_SEASONINGS = BuiltInRegistries.ITEM.map { it.defaultInstance }.filter { Seasonings.isSeasoning(it) }

        val BACKGROUND = cobblemonResource("textures/gui/recipeviewer/campfire_pot.png")
    }

    override fun getId(): ResourceLocation = theId

    override fun getInputs(): List<RecipeIngredient> {
        return originalRecipe.ingredients.map { RecipeIngredient(it) }
    }

    override fun getOutputs(): List<ItemStack> {
        val registryAccess = Minecraft.getInstance().level?.registryAccess() ?: throw IllegalStateException("Registry access not found")
        return listOf(originalRecipe.getResultItem(registryAccess))
    }

    override fun getOptionalInputs(): List<RecipeIngredient> {
        return listOf(RecipeIngredient(Ingredient.of(*generateProperSeasoningList().toTypedArray())))
    }

    override fun getSlots(): List<RecipeSlot> {
        val registryAccess = Minecraft.getInstance().level?.registryAccess() ?: throw IllegalStateException("Registry access not found")
        val slots = mutableListOf<RecipeSlot>()

        originalRecipe.ingredients.forEachIndexed { index, ingredient ->
            val row = index / CRAFTING_GRID_WIDTH
            val column = index % CRAFTING_GRID_WIDTH
            slots.add(RecipeSlot(16 + column * 18, 1 + row * 18,
                SlotType.INPUT, RecipeIngredient(ingredient)))
        }

        if (originalRecipe.seasoningProcessors.isNotEmpty()) {
            val processedSeasonings = generateProperSeasoningList()

            for ((index, _) in SEASONING_SLOTS.withIndex()) {
                val x = 93 + index * 18
                val y = 1

                slots.add(RecipeSlot(x, y,
                    SlotType.CATALYST, RecipeIngredient(Ingredient.of(*processedSeasonings.shuffled().toTypedArray()))))
            }
        }

        slots.add(RecipeSlot(111, 38,
            SlotType.OUTPUT, RecipeIngredient(Ingredient.of(originalRecipe.getResultItem(registryAccess)))))

        return slots
    }

    override fun renderRecipe(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        blitk(
            matrixStack = context.pose(),
            texture = BACKGROUND,
            x = 0,
            y = 0,
            width = 146,
            height = 59
        )
    }

    private fun generateProperSeasoningList(): List<ItemStack> {
        if (originalRecipe.seasoningProcessors.isEmpty()) return emptyList()
        val processedSeasonings = EXAMPLE_SEASONINGS.filter { item ->
            var valid = true
            if (!item.`is`(originalRecipe.seasoningTag)) return@filter false
            originalRecipe.seasoningProcessors.forEach {
                if (!it.consumesItem(item)) {
                    valid = false
                    return@forEach
                }
            }
            valid
        }
        return processedSeasonings
    }
}