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
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.builder.IRecipeSlotBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.registration.IRecipeCategoryRegistration
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

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
                RecipeViewerCategoryProvider.SlotType.INPUT -> RecipeIngredientRole.INPUT
                RecipeViewerCategoryProvider.SlotType.OUTPUT -> RecipeIngredientRole.OUTPUT
                RecipeViewerCategoryProvider.SlotType.CATALYST -> RecipeIngredientRole.CATALYST
            }, slot.x, slot.y)
            jeiSlot.addIngredients(slot.ingredient)
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
}