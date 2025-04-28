/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.components

import com.mojang.serialization.Codec
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.DyeColor

/**
 * This component contains a list of [DyeColor] picked up from cooking.
 *
 * @author Hiroku
 * @since March 20th, 2025
 */
class FoodColourComponent(
    val colours: List<DyeColor>
) {
    companion object {
        val CODEC: Codec<FoodColourComponent> = DyeColor.CODEC.listOf().xmap(::FoodColourComponent, FoodColourComponent::colours)
        val PACKET_CODEC: StreamCodec<ByteBuf, FoodColourComponent> = ByteBufCodecs.fromCodec(CODEC)
    }

    override fun equals(other: Any?): Boolean {
        return other is FoodColourComponent &&
                colours.size == other.colours.size &&
                colours.mapIndexed { index, value -> value == other.colours[index] }.all { it }
    }

    override fun hashCode() = colours.hashCode()
}