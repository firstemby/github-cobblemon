/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.color

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.cooking.getColourMixFromColors
import com.mojang.serialization.MapCodec
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

object PokeSnackItemColorProvider : ItemTintSource {

    override fun calculate(itemStack: ItemStack, clientLevel: ClientLevel?, livingEntity: LivingEntity?): Int {
        val colorComponent = itemStack.get(CobblemonItemComponents.FOOD_COLOUR) ?: return -1

        val colorMix = getColourMixFromColors(colorComponent.getColoursAsARGB())
        return colorMix ?: -1
    }

    override fun type(): MapCodec<out ItemTintSource> {
        return MapCodec.unit(this)
    }
}
