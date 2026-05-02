/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer

import com.cobblemon.mod.common.integration.recipeviewer.berry.BerryMutationProvider
import com.cobblemon.mod.common.integration.recipeviewer.cooking.CampfirePotViewerProvider
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient

import org.joml.Vector2i

interface RecipeViewerCategoryProvider<T: RecipeViewerRecipe> {
    companion object {
        val categoriesProviders: Set<RecipeViewerCategoryProvider<*>> = setOf(
            BerryMutationProvider(),
            CampfirePotViewerProvider()
        )
    }

    val recipeClass: Class<T>
    val id: ResourceLocation

    fun getName(): Component = Component.translatable("${id.namespace}.recipeviewer.category.${id.path}")
    fun getIconResource(): ResourceLocation
    fun getIconDimensions(): Vector2i

    fun getRecipes(): List<T>
    fun getWorkstations(): List<ItemStack>

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