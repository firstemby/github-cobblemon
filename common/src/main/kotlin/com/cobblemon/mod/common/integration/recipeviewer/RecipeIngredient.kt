package com.cobblemon.mod.common.integration.recipeviewer

import net.minecraft.world.item.crafting.Ingredient

// Whole existence due to the fact we can have recipes with multiple count requirement
data class RecipeIngredient(
    val ingredient: Ingredient,
    val count: Int = 1
)