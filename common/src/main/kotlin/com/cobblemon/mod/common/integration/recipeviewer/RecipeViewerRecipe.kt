package com.cobblemon.mod.common.integration.recipeviewer

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider.Slot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient

interface RecipeViewerRecipe {
    fun getId(): ResourceLocation

    fun getInputs(): List<Ingredient>
    fun getOutputs(): List<ItemStack>

    fun supportsRecipeTree(): Boolean = true

    fun renderRecipe(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {}

    fun getSlots(): List<Slot>
}