/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.emi.brewing

import com.cobblemon.mod.common.item.crafting.brewingstand.BrewingStandRecipe
import dev.emi.emi.EmiPort
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items

class EmiCobblemonBrewingRecipe(val recipe: BrewingStandRecipe, val theId: ResourceLocation): EmiRecipe {
    //I would have loved to use the base recipe one, but it just has 1 little caveat, input is an EmiStack, this is basically 99% recreation without that issue

    private val input = EmiIngredient.of(recipe.input)
    private val bottle = EmiIngredient.of(recipe.bottle)
    private val output = EmiStack.of(recipe.result)

    override fun getCategory(): EmiRecipeCategory = VanillaEmiRecipeCategories.BREWING
    override fun getId(): ResourceLocation = theId
    override fun getDisplayWidth(): Int = 120
    override fun getDisplayHeight(): Int = 61

    override fun getInputs(): List<EmiIngredient> {
        return listOf(
            bottle.copy().setAmount(3),
            input
        )
    }

    override fun getOutputs(): List<EmiStack> {
        return listOf(
            output.copy().setAmount(3)
        )
    }

    override fun addWidgets(widgets: WidgetHolder) {
        widgets.addTexture(BACKGROUND, 0, 0, 103, 61, 16, 14)
        widgets.addAnimatedTexture(BACKGROUND, 81, 2, 9, 28, 176, 0, 1000 * 20, false, false, false).tooltip { mx, my ->
            listOf(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.translatable("emi.cooking.time", 20))))
        }
        widgets.addAnimatedTexture(BACKGROUND, 47, 0, 12, 29, 185, 0, 700, false, true, false)
        widgets.addSlot(BLAZE_POWDER, 0, 2).drawBack(false)
        widgets.addSlot(bottle, 39, 36).drawBack(false)
        widgets.addSlot(input, 62, 2).drawBack(false)
        widgets.addSlot(output, 85, 36).drawBack(false).recipeContext(this)
    }

    companion object {
        private val BLAZE_POWDER = EmiStack.of(Items.BLAZE_POWDER)
        private val BACKGROUND = EmiPort.id("minecraft", "textures/gui/container/brewing_stand.png")
    }
}