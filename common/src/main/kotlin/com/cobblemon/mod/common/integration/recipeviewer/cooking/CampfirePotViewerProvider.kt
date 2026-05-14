/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer.cooking

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.client.gui.cookingpot.CookingPotScreen
import com.cobblemon.mod.common.integration.recipeviewer.RecipeClickArea
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import com.cobblemon.mod.common.integration.recipeviewer.berry.BerryMutationProvider
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike
import org.joml.Vector2i

class CampfirePotViewerProvider: RecipeViewerCategoryProvider<CampfirePotViewerRecipe, CookingPotMenu> {
    override val id = ID
    override val recipeClass = CampfirePotViewerRecipe::class.java

    override fun getIconResource(): ResourceLocation = cobblemonResource("textures/item/campfire_pots/campfire_pot_red.png")
    override fun getIconDimensions(): Vector2i = Vector2i(16, 16)

    override fun getWidth(): Int = WIDTH
    override fun getHeight(): Int = HEIGHT

    override fun getRecipes(): List<CampfirePotViewerRecipe> {
        val recipeManger = Minecraft.getInstance().level?.recipeManager ?: throw IllegalStateException("Recipe manager not found")

        val recipeList = mutableListOf<CampfirePotViewerRecipe>()
        recipeManger.getAllRecipesFor(CobblemonRecipeTypes.COOKING_POT_SHAPELESS).forEach {
            recipeList.add(CampfirePotViewerRecipe(it.id, it.value))
        }
        recipeManger.getAllRecipesFor(CobblemonRecipeTypes.COOKING_POT_COOKING).forEach {
            recipeList.add(CampfirePotViewerRecipe(it.id, it.value))
        }

        return recipeList
    }

    override fun getWorkstations(): List<ItemStack> {
        return CobblemonItems.campfire_pots.map { it.defaultInstance }
    }

    override fun getClickArea(): RecipeClickArea = RecipeClickArea(CookingPotScreen::class.java as Class<AbstractContainerScreen<AbstractContainerMenu>>, 96, 39 ,
        CookingPotScreen.COOK_PROGRESS_WIDTH, CookingPotScreen.COOK_PROGRESS_HEIGHT)

    override fun recipeHandler(): RecipeViewerRecipeHandler<CookingPotMenu> = CampfirePotViewerRecipeHandler()

    companion object {
        private const val WIDTH = 146
        private const val HEIGHT = 59

        val ID = cobblemonResource("cooking_pot_recipe")
    }
}