/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.components

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokeball.PokeBall
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

data class PokeBallComponent(val name: ResourceLocation) {
    companion object {
        val CODEC: Codec<PokeBallComponent> = RecordCodecBuilder.create {
            it
                .group(ResourceLocation.CODEC.fieldOf("type").forGetter(PokeBallComponent::name))
                .apply(it, ::PokeBallComponent)
        }

        val PACKET_CODEC: StreamCodec<ByteBuf, PokeBallComponent> = ByteBufCodecs.fromCodec(CODEC)

        @JvmStatic
        fun getPokeBall(stack: ItemStack): PokeBall {
            return stack.get(CobblemonItemComponents.POKE_BALL)?.name?.let(PokeBalls::getPokeBall)
                ?: (stack.item as? PokeBallItem)?.pokeBall
                ?: PokeBalls.STRANGE_BALL
        }

        @JvmStatic
        fun setPokeBall(stack: ItemStack, ball: PokeBall): ItemStack {
            stack.set(CobblemonItemComponents.POKE_BALL, PokeBallComponent(ball.name))
            return stack
        }

        @JvmStatic
        fun createStack(ball: PokeBall?): ItemStack {
            var result = PokeBalls.STRANGE_BALL
            if (ball == null) {
                Cobblemon.LOGGER.error("Attempted to create poke ball stack with null config, falling to strange ball.")
            } else {
                result = ball
            }
            val stack = ItemStack(CobblemonItems.STRANGE_BALL)
            return setPokeBall(stack, result)
        }

    }

    val ball: PokeBall
        get() = PokeBalls.getPokeBall(name)
}
