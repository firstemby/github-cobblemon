/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.components

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.effect.MobEffectInstance
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

data class FoodComponent(
    val hunger: Int,
    val saturation: Float,
    val mobEffects: List<MobEffectInstance>? = null
) {
    companion object {
        val CODEC: Codec<FoodComponent> = RecordCodecBuilder.create { builder ->
            builder.group(
                Codec.INT.fieldOf("hunger").forGetter { it.hunger },
                Codec.FLOAT.fieldOf("saturation").forGetter { it.saturation },
                MobEffectInstance.CODEC.listOf().optionalFieldOf("mob_effects").forGetter { Optional.ofNullable(it.mobEffects) }
            ).apply(builder) { hunger, saturation, mobEffects ->
                FoodComponent(hunger, saturation, mobEffects.getOrNull())
            }
        }

        val PACKET_CODEC: StreamCodec<ByteBuf, FoodComponent> = ByteBufCodecs.fromCodec(CODEC)
    }

    override fun equals(other: Any?): Boolean =
        other is FoodComponent &&
                hunger == other.hunger &&
                saturation == other.saturation &&
                mobEffects == other.mobEffects

    override fun hashCode(): Int = 31 * hunger + saturation.hashCode() + (mobEffects?.hashCode() ?: 0)
}
