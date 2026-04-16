/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.cooking.getColourMixFromColors
import com.cobblemon.mod.common.api.cooking.getColourMixFromRideStatBoosts
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

object CobblemonItemTintSources {
    val FOOD_COLOUR_MIX = cobblemonResource("food_colour_mix")
    val FOOD_COLOUR_ENTRY = cobblemonResource("food_colour_entry")
    val RIDE_BOOST_MIX = cobblemonResource("ride_boost_mix")

    object FoodColourMixTintSource : ItemTintSource {
        val MAP_CODEC: MapCodec<FoodColourMixTintSource> = MapCodec.unit(this)

        override fun calculate(stack: ItemStack, level: ClientLevel?, entity: LivingEntity?): Int {
            val colorComponent = stack.get(CobblemonItemComponents.FOOD_COLOUR) ?: return -1
            return getColourMixFromColors(colorComponent.getColoursAsARGB()) ?: -1
        }

        override fun type() = MAP_CODEC
    }

    data class FoodColourEntryTintSource(val index: Int) : ItemTintSource {
        companion object {
            val MAP_CODEC: MapCodec<FoodColourEntryTintSource> = RecordCodecBuilder.mapCodec { instance: Instance<FoodColourEntryTintSource> ->
                instance.group(
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("index").forGetter(FoodColourEntryTintSource::index)
                ).apply(instance, ::FoodColourEntryTintSource)
            }
        }

        override fun calculate(stack: ItemStack, level: ClientLevel?, entity: LivingEntity?): Int {
            return stack.get(CobblemonItemComponents.FOOD_COLOUR)
                ?.colours
                ?.getOrNull(index)
                ?.textureDiffuseColor
                ?: -1
        }

        override fun type() = MAP_CODEC
    }

    object RideBoostMixTintSource : ItemTintSource {
        val MAP_CODEC: MapCodec<RideBoostMixTintSource> = MapCodec.unit(this)

        override fun calculate(stack: ItemStack, level: ClientLevel?, entity: LivingEntity?): Int {
            val rideBoostsComponent = stack.get(CobblemonItemComponents.RIDE_BOOST) ?: return -1
            if (rideBoostsComponent.boosts.isEmpty()) {
                return -1
            }

            val highestBoost = rideBoostsComponent.boosts.maxOf { it.value }
            val highestRidingStats = rideBoostsComponent.boosts.filterValues { it == highestBoost }.keys
            if (highestRidingStats.isEmpty()) {
                return -1
            }

            return getColourMixFromRideStatBoosts(highestRidingStats) ?: -1
        }

        override fun type() = MAP_CODEC
    }
}
