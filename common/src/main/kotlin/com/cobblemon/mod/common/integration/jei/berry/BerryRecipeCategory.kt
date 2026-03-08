/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.jei.berry

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.util.cobblemonResource
import mezz.jei.api.constants.ModIds
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.recipe.types.IRecipeType
import mezz.jei.api.registration.IRecipeCategoryRegistration
import net.minecraft.network.chat.Component
import net.minecraft.world.item.crafting.Ingredient

class BerryRecipeCategory(private val registration: IRecipeCategoryRegistration) : IRecipeCategory<BerryMutationRecipe> {
    private var background: IDrawable
    private var icon: IDrawable
    init {
        val guiHelper = registration.jeiHelpers.guiHelper
        background = guiHelper.createDrawable(GUI_TEXTURE_ID, 0, 0, WIDTH, HEIGHT)
        icon = guiHelper.createDrawableItemStack(CobblemonItems.SURPRISE_MULCH.defaultInstance)
    }
    override fun getRecipeType(): IRecipeType<BerryMutationRecipe> {
        return RECIPE_TYPE
    }

    override fun getTitle(): Component {
        return Component.literal("Berry Mutation")
    }

    override fun getWidth(): Int {
        return 0
    }

    override fun getHeight(): Int {
        return 0
    }

    override fun getIcon(): IDrawable {
        return icon
    }

    override fun setRecipe(p0: IRecipeLayoutBuilder, p1: BerryMutationRecipe, p2: IFocusGroup) {
        //These magic numbers are where the items should be positioned on the gui texture
        p0.addSlot(RecipeIngredientRole.INPUT, 1, 1).add(Ingredient.of(p1.berryOne))
        p0.addSlot(RecipeIngredientRole.INPUT, 50, 1).add(Ingredient.of(p1.berryTwo))
        p0.addSlot(RecipeIngredientRole.OUTPUT, 108, 1).add(Ingredient.of(p1.berryResult))
    }

    companion object {
        val RECIPE_TYPE = IRecipeType.create("cobblemon", "berry_recipe", BerryMutationRecipe::class.java)!!
        val GUI_TEXTURE_ID = cobblemonResource("textures/gui/jei/berry_mutation.png")
        const val WIDTH = 124
        const val HEIGHT = 17
    }
}
