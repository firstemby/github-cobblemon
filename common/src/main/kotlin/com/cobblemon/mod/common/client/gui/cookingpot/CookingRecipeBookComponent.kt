/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.cookingpot

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonRecipeBookCategories
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import com.cobblemon.mod.common.item.crafting.CookingPotShapedRecipeDisplay
import com.cobblemon.mod.common.item.crafting.CookingPotShapelessRecipeDisplay
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.screens.recipebook.GhostSlots
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import net.minecraft.client.gui.screens.recipebook.RecipeCollection
import net.minecraft.network.chat.Component
import net.minecraft.recipebook.PlaceRecipeHelper
import net.minecraft.resources.Identifier
import net.minecraft.util.context.ContextMap
import net.minecraft.world.entity.player.StackedItemContents
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.crafting.display.RecipeDisplay
import kotlin.math.min

class CookingRecipeBookComponent(menu: CookingPotMenu) : RecipeBookComponent<CookingPotMenu>(menu, TABS) {
    companion object {
        val FILTER_BUTTON_SPRITES = WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_enabled"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_disabled"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_enabled_highlighted"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_disabled_highlighted")
        )

        val TABS = listOf(
            //TabInfo(Items.COMPASS, CobblemonRecipeBookCategories.COOKING_POT_SEARCH as RecipeBookCategory),
            TabInfo(CobblemonItems.PONIGIRI, CobblemonRecipeBookCategories.COOKING_POT_FOODS),
            TabInfo(CobblemonItems.POTION, CobblemonRecipeBookCategories.COOKING_POT_MEDICINES),
            TabInfo(CobblemonItems.OPEN_FACED_SANDWICH,  CobblemonRecipeBookCategories.COOKING_POT_COMPLEX_DISHES),
            TabInfo(CobblemonItems.POKE_SNACK, CobblemonRecipeBookCategories.COOKING_POT_MISC))

        val ONLY_CRAFTABLES_TOOLTIP: Component = Component.translatable("gui.recipebook.toggleRecipes.craftable")
    }
    override fun getFilterButtonTextures() = FILTER_BUTTON_SPRITES

    override fun isCraftingSlot(slot: Slot): Boolean {
        return this.menu.getResultSlotIndex() == slot.index || this.menu.getInputGridSlots().contains(slot)
    }

    override fun selectMatchingRecipes(
        collection: RecipeCollection,
        stackedContents: StackedItemContents
    ) {
        collection.selectRecipes(stackedContents) { it is CookingPotShapedRecipeDisplay || it is CookingPotShapelessRecipeDisplay }
    }

    override fun getRecipeFilterName() = ONLY_CRAFTABLES_TOOLTIP

    override fun fillGhostRecipe(
        ghostSlots: GhostSlots,
        recipe: RecipeDisplay,
        context: ContextMap
    ) {
        ghostSlots.setResult(this.menu.getResultSlot(), context, recipe.result())


        when (recipe) {
            is CookingPotShapedRecipeDisplay -> {
                val inputSlots = this.menu.getInputGridSlots()
                PlaceRecipeHelper.placeRecipe(
                    this.menu.getGridWidth(),
                    this.menu.getGridHeight(),
                    recipe.width,
                    recipe.height,
                    recipe.ingredients,
                    { ingredient, gridIndex, _, _ ->
                        val slot = inputSlots[gridIndex]
                        ghostSlots.setInput(slot, context, ingredient)
                    })
                CampfireBlockEntity.SEASONING_SLOTS.forEach { slot ->
                    val seasoningSlot = this.menu.getSlot(slot)
                    ghostSlots.setInput(seasoningSlot, context, recipe.seasonings)
                }
            }
            is CookingPotShapelessRecipeDisplay -> {
                val inputSlots = this.menu.getInputGridSlots()
                val slotCount = min(recipe.ingredients.size, inputSlots.size)

                for (i in 0..< slotCount) {
                    ghostSlots.setInput(inputSlots[i], context, recipe.ingredients[i])
                }
            }
        }
    }
}