/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.color

import com.cobblemon.mod.common.CobblemonItemComponents
import com.mojang.serialization.MapCodec
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

object PokeBaitItemColorProvider : ItemTintSource {
    override fun calculate(
        itemStack: ItemStack,
        clientLevel: ClientLevel?,
        livingEntity: LivingEntity?
    ): Int {
        val colourComponent = itemStack.get(CobblemonItemComponents.FOOD_COLOUR) ?: return -1

        val primaryColor = colourComponent.colours.getOrNull(0)
        val secondaryColor = colourComponent.colours.getOrNull(1)
        val tertiaryColor = colourComponent.colours.getOrNull(2)

        val colour = when (layer) {
            0 -> primaryColor?.textureDiffuseColor
            1 -> secondaryColor?.textureDiffuseColor
            2 -> tertiaryColor?.textureDiffuseColor
            else -> -1
        } ?: -1

        return colour
    }

    override fun type(): MapCodec<out ItemTintSource> {
        TODO("Not yet implemented")
    }
}
