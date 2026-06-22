/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer.berry

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.item.berry.BerryItem
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Vector2i

class BerryMutationProvider: RecipeViewerCategoryProvider<BerryMutationRecipe, CookingPotMenu> {
    override val id = ID
    override val recipeClass = BerryMutationRecipe::class.java

    override fun getIconResource(): ResourceLocation = cobblemonResource("textures/item/mulches/surprise_mulch.png")
    override fun getIconDimensions(): Vector2i = Vector2i(16, 16)

    override fun getWidth(): Int = WIDTH
    override fun getHeight(): Int = HEIGHT

    override fun getRecipes(): List<BerryMutationRecipe> {
        val berryList = CobblemonItems.berries().map { it.value }
        val berryMutations = mutableSetOf<Triple<BerryItem, BerryItem, BerryItem>>()
        berryList.forEach {berry ->
            for (mut in berry.berry()?.mutations?.entries ?: emptySet()) {
                val berryTwo = CobblemonItems.berries()[mut.key] ?: continue
                val berryThree = CobblemonItems.berries()[mut.value] ?: continue
                val mutation = Triple(berry, berryTwo, berryThree)
                val altMutation = Triple(berryTwo, berry, berryThree)
                if (!(berryMutations.contains(mutation) || berryMutations.contains(altMutation))) {
                    berryMutations.add(mutation)
                }
            }
        }
        return berryMutations.map { values ->
            BerryMutationRecipe(values.first, values.second, values.third) }
    }

    override fun getWorkstations(): List<ItemStack> {
        return emptyList()
    }

    companion object {
        private const val WIDTH = 125
        private const val HEIGHT = 18

        val ID = cobblemonResource("berry_recipe")
    }
}