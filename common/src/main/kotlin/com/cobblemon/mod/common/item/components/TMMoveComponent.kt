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
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack

data class TMMoveComponent(val moveName: String) {
    companion object {
        // Codec to serialize/deserialize TMMoveComponent
        val CODEC: Codec<TMMoveComponent> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("move").forGetter { it.moveName } // Serialize the move's name
            ).apply(instance) { moveName -> TMMoveComponent(moveName) }
        }

        val PACKET_CODEC: StreamCodec<ByteBuf, TMMoveComponent> = ByteBufCodecs.fromCodec(CODEC)

        fun getTMMove(stack: ItemStack): MoveTemplate? {
            return stack.get(CobblemonItemComponents.TM_MOVE)?.moveName?.let(Moves::getByNameOrDummy)
        }

        fun setTMMove(stack: ItemStack, move: MoveTemplate): ItemStack {
            stack.set(CobblemonItemComponents.TM_MOVE, TMMoveComponent(move.name))
            return stack
        }

        fun removeTMMove(stack: ItemStack): ItemStack {
            stack.remove(CobblemonItemComponents.TM_MOVE)
            return stack
        }

        fun createStack(move: MoveTemplate?): ItemStack {
            if (move == null) {
                Cobblemon.LOGGER.error("Attempted to create TM stack with null move!")
                throw IllegalArgumentException("MoveTemplate cannot be null in createStack.")
            }
            val stack = ItemStack(CobblemonItems.TECHNICAL_MACHINE)
            return setTMMove(stack, move)
        }

    }

    constructor(move: TMMoveComponent): this(move.moveName)

    val move: MoveTemplate
        get() = Moves.getByNameOrDummy(moveName)
}
