package com.cobblemon.mod.common.integration.recipeviewer

import com.cobblemon.mod.common.integration.recipeviewer.berry.BerryMutationProvider
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.ItemLike

import org.joml.Vector2i

interface RecipeViewerCategoryProvider<T: RecipeViewerRecipe> {
    companion object {
        val categoriesProviders: Set<RecipeViewerCategoryProvider<*>> = setOf(
            BerryMutationProvider()
        )
    }

    val recipeClass: Class<T>
    val id: ResourceLocation

    fun getName(): Component
    fun getIconResource(): ResourceLocation
    fun getIconDimensions(): Vector2i

    fun getRecipes(): List<T>
    fun getWorkstations(): List<ItemLike>

    fun getHeight(): Int
    fun getWidth(): Int

    data class Slot(
        val x: Int,
        val y: Int,
        val type: SlotType,
        val ingredient: Ingredient
    )

    enum class SlotType {
        INPUT,
        OUTPUT
    }
}