/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.crafting.brewingstand

import com.cobblemon.mod.common.CobblemonRecipeDisplayTypes
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay

class BrewingStandRecipeDisplay(var bottles: SlotDisplay,
                                       var inputItem: SlotDisplay,
                                       var resultDisplay: SlotDisplay) // Implements RecipeDisplay#result
     : RecipeDisplay {
    override fun result() = this.resultDisplay

    override fun craftingStation() = SlotDisplay.ItemSlotDisplay(Items.BREWING_STAND)

    override fun type(): RecipeDisplay.Type<out RecipeDisplay> = CobblemonRecipeDisplayTypes.BREWING

    companion object {
        val CODEC = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                SlotDisplay.CODEC.fieldOf("inputState").forGetter(BrewingStandRecipeDisplay::bottles),
                SlotDisplay.CODEC.fieldOf("inputState").forGetter(BrewingStandRecipeDisplay::inputItem),
                SlotDisplay.CODEC.fieldOf("result").forGetter(BrewingStandRecipeDisplay::resultDisplay)
            ).apply(instance, ::BrewingStandRecipeDisplay)
        }

        val STREAM_CODEC = StreamCodec.composite(
            SlotDisplay.STREAM_CODEC,
            BrewingStandRecipeDisplay::bottles,
            SlotDisplay.STREAM_CODEC,
            BrewingStandRecipeDisplay::inputItem,
            SlotDisplay.STREAM_CODEC,
            BrewingStandRecipeDisplay::result,
            ::BrewingStandRecipeDisplay
        )
    }
}