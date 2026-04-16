/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.color

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.cooking.getColourMixFromRideStatBoosts
import com.mojang.serialization.MapCodec
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

object AprijuiceItemColorProvider : ItemTintSource {

    override fun calculate(itemStack: ItemStack, level: ClientLevel?, owner: LivingEntity?): Int {
        val rideBoostsComponent = itemStack.get(CobblemonItemComponents.RIDE_BOOST) ?: return -1
        if (rideBoostsComponent.boosts.isEmpty()) return -1
        val highestBoost = rideBoostsComponent.boosts.maxOf { it.value }
        val highestRidingStats = rideBoostsComponent.boosts.filter { it.value == highestBoost }.keys
        if (highestRidingStats.isEmpty()) return -1
        val colorMix = getColourMixFromRideStatBoosts(highestRidingStats)
        return colorMix ?: -1
    }

    override fun type(): MapCodec<out ItemTintSource> {
        return MapCodec.unit(this)
    }
}