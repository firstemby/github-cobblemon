/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer.tm

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import org.joml.Vector2i

class TMViewerProvider: RecipeViewerCategoryProvider<TMViewerRecipe> {
    override val id = ID
    override val recipeClass = TMViewerRecipe::class.java

    override fun getIconResource(): ResourceLocation = cobblemonResource("textures/item/tms/blank_disc.png")
    override fun getIconDimensions(): Vector2i = Vector2i(16, 16)

    override fun getWidth(): Int = WIDTH
    override fun getHeight(): Int = HEIGHT

    override fun getRecipes(): List<TMViewerRecipe> {
        return TechnicalMachines.tmMap.values.map { TMViewerRecipe(it) }
    }

    override fun getWorkstations(): List<ItemStack> {
        return listOf(CobblemonItems.TM_MACHINE.defaultInstance)
    }

    override fun recipeHandler(): List<RecipeViewerRecipeHandler<TMViewerRecipe, AbstractContainerMenu>> = listOf(TMViewerRecipeHandler() as RecipeViewerRecipeHandler<TMViewerRecipe, AbstractContainerMenu>)

    companion object {
        private const val WIDTH = 125
        private const val HEIGHT = 30

        val ID = cobblemonResource("tm_recipe")
    }
}