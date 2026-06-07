package com.cobblemon.mod.common.integration.recipeviewer

data class RecipeSlot(
    val x: Int,
    val y: Int,
    val type: SlotType,
    val ingredient: RecipeIngredient
)

enum class SlotType {
    INPUT,
    OUTPUT,
    CATALYST
}
