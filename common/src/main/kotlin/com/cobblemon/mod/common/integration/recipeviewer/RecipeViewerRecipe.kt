/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider.Slot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient

interface RecipeViewerRecipe {
    fun getId(): ResourceLocation

    fun getInputs(): List<Ingredient>
    fun getOutputs(): List<ItemStack>

    fun getOptionalInputs(): List<Ingredient> = emptyList()

    fun supportsRecipeTree(): Boolean = true

    fun renderRecipe(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {}

    fun getSlots(): List<Slot>
}