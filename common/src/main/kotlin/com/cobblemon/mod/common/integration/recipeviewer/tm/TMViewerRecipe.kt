package com.cobblemon.mod.common.integration.recipeviewer.tm

import com.cobblemon.mod.common.api.tms.TechnicalMachine
import com.cobblemon.mod.common.integration.recipeviewer.RecipeIngredient
import com.cobblemon.mod.common.integration.recipeviewer.RecipeSlot
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient

class TMViewerRecipe(val originalTM: TechnicalMachine): RecipeViewerRecipe {
    override fun getId(): ResourceLocation = originalTM.id

    override fun getInputs(): List<RecipeIngredient> {
        return originalTM.getClampedRecipe()?.map { RecipeIngredient(it.ingredient, it.count) } ?: listOf()
    }

    override fun getOutputs(): List<ItemStack> {
         return listOf(originalTM.createItemStack())
    }

    override fun getSlots(): List<RecipeSlot> {
        return emptyList()
    }
}