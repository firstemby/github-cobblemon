/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages

import net.minecraft.network.RegistryFriendlyByteBuf

sealed class SerializedBinding {
    abstract val actorId: String

    data class Pokemon(
        override val actorId: String,
        val propertiesSpec: String,
        val aspects: List<String>
    ) : SerializedBinding()

    data class Bedrock(
        override val actorId: String,
        val category: String,
        val aspects: List<String>,
        val scale: Float
    ) : SerializedBinding()

    companion object {
        private const val TAG_POKEMON: Byte = 0
        private const val TAG_BEDROCK: Byte = 1
        const val MAX_ASPECTS = 64

        private fun readAspects(buffer: RegistryFriendlyByteBuf): List<String> {
            val count = buffer.readVarInt()
            require(count in 0..MAX_ASPECTS) { "aspect count $count exceeds $MAX_ASPECTS" }
            return List(count) { buffer.readUtf() }
        }

        fun encode(buffer: RegistryFriendlyByteBuf, binding: SerializedBinding) {
            buffer.writeUtf(binding.actorId)
            when (binding) {
                is Pokemon -> {
                    buffer.writeByte(TAG_POKEMON.toInt())
                    buffer.writeUtf(binding.propertiesSpec)
                    buffer.writeVarInt(binding.aspects.size)
                    binding.aspects.forEach(buffer::writeUtf)
                }
                is Bedrock -> {
                    buffer.writeByte(TAG_BEDROCK.toInt())
                    buffer.writeUtf(binding.category)
                    buffer.writeVarInt(binding.aspects.size)
                    binding.aspects.forEach(buffer::writeUtf)
                    buffer.writeFloat(binding.scale)
                }
            }
        }

        fun decode(buffer: RegistryFriendlyByteBuf): SerializedBinding {
            val actorId = buffer.readUtf()
            return when (val tag = buffer.readByte()) {
                TAG_POKEMON -> {
                    val spec = buffer.readUtf()
                    val aspects = readAspects(buffer)
                    Pokemon(actorId, spec, aspects)
                }
                TAG_BEDROCK -> {
                    val category = buffer.readUtf()
                    val aspects = readAspects(buffer)
                    val scale = buffer.readFloat()
                    Bedrock(actorId, category, aspects, scale)
                }
                else -> throw IllegalArgumentException("Unknown SerializedBinding tag: $tag")
            }
        }
    }
}
