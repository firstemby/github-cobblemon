package com.cobblemon.mod.common.integration.recipeviewer

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider.SlotType

data class RecipeSlot(
    val x: Int,
    val y: Int,
    val type: SlotType,
    val ingredient: RecipeIngredient
)
