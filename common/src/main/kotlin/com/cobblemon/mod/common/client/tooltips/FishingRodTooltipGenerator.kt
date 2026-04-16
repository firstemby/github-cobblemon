/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.tooltips

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.fishing.SpawnBaitEffects
import com.cobblemon.mod.common.api.fishing.PokeRods
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.text.gray
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.util.asTranslated
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object FishingRodTooltipGenerator : TooltipGenerator() {
    override fun generateTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        val resultLines = mutableListOf<Component>()

        val rod = (stack.item as? PokerodItem)?.pokeRodId?.let { PokeRods.getPokeRod(it) } ?: return null
        val ball = PokeBalls.getPokeBall(rod.pokeBallId) ?: return null
        val baitComponent = stack.get(CobblemonItemComponents.BAIT)

        // Add the description of the Poke Ball used in the rod
        val bobberName = ball.item.getName(ItemStack(ball.item)).copy().gray()
        resultLines.add(Component.translatable("cobblemon.pokerod.bobber", bobberName))

        baitComponent?.stack?.let { baitStack ->
            val baitName = baitStack.item.getName(baitStack).copy().gray()
            val baitDescription = "cobblemon.pokerod.bait".asTranslated(baitName, PokerodItem.getBaitStackOnRod(stack).count)
            resultLines.add(baitDescription)
        }

        // grey text for context for players on how to apply/remove bait to/from rod
        val greyText =
            if (baitComponent != null && SpawnBaitEffects.isFishingBait(baitComponent.stack)) {
                Component.translatable("cobblemon.pokerod.remove").gray()
            } else {
                Component.translatable("cobblemon.pokerod.apply").gray()
            }

        resultLines.addLast(greyText)

        return resultLines
    }

    override fun generateAdditionalTooltip(
        stack: ItemStack,
        lines: MutableList<Component>
    ): MutableList<Component>? {
        val resultLines = mutableListOf<Component>()

        val baitComponent = stack.get(CobblemonItemComponents.BAIT)
        baitComponent?.let { component ->
            val effectLines = SeasoningTooltipGenerator.generateAdditionalTooltip(component.stack, lines)
            effectLines?.let { lines ->
                resultLines.addAll(lines)
            }
        }

        return resultLines
    }
}
