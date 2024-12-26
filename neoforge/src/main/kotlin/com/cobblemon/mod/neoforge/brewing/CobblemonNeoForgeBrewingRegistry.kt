/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.neoforge.brewing

import com.cobblemon.mod.common.brewing.BrewingRecipes
import com.cobblemon.mod.common.brewing.ingredient.CobblemonItemIngredient
import com.cobblemon.mod.common.brewing.ingredient.CobblemonPotionIngredient
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.crafting.Ingredient
import net.neoforged.neoforge.common.brewing.BrewingRecipe
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent

internal object CobblemonNeoForgeBrewingRegistry {

    fun register(e: RegisterBrewingRecipesEvent) {
        this.registerRecipes(e)
    }

    private fun registerRecipes(e: RegisterBrewingRecipesEvent) {
        BrewingRecipes.recipes.forEach { (input, ingredient, output) ->
            val inputIngredient = if (input is CobblemonItemIngredient) {
                Ingredient.of(input.item)
            } else {
                input as CobblemonPotionIngredient
                Ingredient.of(
                    ItemStack(Items.POTION).also { it.set(DataComponents.POTION_CONTENTS, PotionContents(input.potion)) },
                    ItemStack(Items.SPLASH_POTION).also { it.set(DataComponents.POTION_CONTENTS, PotionContents(input.potion)) },
                    ItemStack(Items.LINGERING_POTION).also { it.set(DataComponents.POTION_CONTENTS, PotionContents(input.potion)) }
                )
            }
            e.builder.addRecipe(
                BrewingRecipe(
                    inputIngredient,
                    Ingredient.of(ingredient),
                    ItemStack(output)
                )
            )
        }
    }


}