package com.cobblemon.mod.common

import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.crafting.RecipePropertySet

object CobblemonRecipePropertySets {
    val BREWING_INPUT = ResourceKey.create(RecipePropertySet.TYPE_KEY, cobblemonResource("brewing_input"))
    val BREWING_BOTTLE = ResourceKey.create(RecipePropertySet.TYPE_KEY, cobblemonResource("brewing_bottle"))
}