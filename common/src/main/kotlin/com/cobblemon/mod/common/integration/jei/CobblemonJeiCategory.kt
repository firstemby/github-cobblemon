/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.jei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.SlotType
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.builder.IRecipeSlotBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.registration.IRecipeCategoryRegistration
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

class CobblemonJeiCategory<T: RecipeViewerRecipe>(private val registration: IRecipeCategoryRegistration, val categoryProvider: CobblemonJeiProvider<T>):
    IRecipeCategory<T> {
    private val icon: IDrawable

    init {
        val guiHelper = registration.jeiHelpers.guiHelper
        val dimensions = categoryProvider.originalProvider.getIconDimensions()
        icon = guiHelper.drawableBuilder(categoryProvider.originalProvider.getIconResource(), 0, 0,
            dimensions.x, dimensions.y)
            .setTextureSize(dimensions.x, dimensions.y)
            .build()
    }

    override fun getIcon(): IDrawable = icon
    override fun getRecipeType(): RecipeType<T> = categoryProvider.recipeType
    override fun getTitle(): Component = categoryProvider.originalProvider.getName()

    override fun getWidth(): Int = categoryProvider.originalProvider.getWidth()
    override fun getHeight(): Int = categoryProvider.originalProvider.getHeight()

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: T, focuses: IFocusGroup) {
        recipe.getSlots().forEach { slot ->
            val jeiSlot: IRecipeSlotBuilder = builder.addSlot(when (slot.type) {
                SlotType.INPUT -> RecipeIngredientRole.INPUT
                SlotType.OUTPUT -> RecipeIngredientRole.OUTPUT
                SlotType.CATALYST -> RecipeIngredientRole.CATALYST
            }, slot.x, slot.y)
            val items = slot.ingredient.ingredient.items.map { it.copyWithCount(slot.ingredient.count) }
            jeiSlot.addIngredients(VanillaTypes.ITEM_STACK, items)
        }
    }

    override fun draw(
        recipe: T,
        recipeSlotsView: IRecipeSlotsView,
        guiGraphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        val delta = Minecraft.getInstance().timer.realtimeDeltaTicks
        recipe.renderRecipe( guiGraphics, mouseX.toInt(), mouseY.toInt(), delta)
    }

    override fun onDisplayedIngredientsUpdate(
        recipe: T,
        recipeSlots: List<IRecipeSlotDrawable?>,
        focuses: IFocusGroup
    ) {
        val watchedItems = mutableListOf<ItemStack>()
        val baseSlots = recipe.getSlots()
        baseSlots.forEachIndexed { index, slot ->
            if (slot.watchIngredient && slot.generatedStack == null) {
                val ingredient = recipeSlots[index]!!.displayedIngredient
                if (ingredient.isEmpty) {
                    watchedItems.add(ItemStack.EMPTY)
                } else {
                    watchedItems.add(ingredient.get().itemStack.getOrDefault(ItemStack.EMPTY))
                }
            }
        }

        //Apply generations
        baseSlots.forEachIndexed { index, slot ->
            if (slot.generatedStack != null) {
                val stack = slot.generatedStack(watchedItems)
                recipeSlots[index]!!.let {
                    it.createDisplayOverrides().addItemStack(stack)
                }
            }
        }
    }
}