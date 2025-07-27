/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.components

import com.cobblemon.mod.common.api.cooking.SerializableMobEffectInstance
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.effect.MobEffectInstance
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

data class MobEffectsComponent(
    val mobEffects: List<MobEffectInstance>,
    val food: FoodComponent? = null
) {
    companion object {
        val CODEC: Codec<MobEffectsComponent> = RecordCodecBuilder.create { builder ->
            builder.group(
                MobEffectInstance.CODEC.listOf().fieldOf("mob_effects").forGetter { it.mobEffects },
                FoodComponent.CODEC.optionalFieldOf("food").forGetter { Optional.ofNullable(it.food) }
            ).apply(builder) { effects, food ->
                MobEffectsComponent(effects, food.getOrNull())
            }
        }

        val PACKET_CODEC: StreamCodec<ByteBuf, MobEffectsComponent> = ByteBufCodecs.fromCodec(CODEC)
    }

    override fun equals(other: Any?): Boolean =
        other is MobEffectsComponent &&
                mobEffects == other.mobEffects &&
                food == other.food

    override fun hashCode(): Int = 31 * mobEffects.hashCode() + (food?.hashCode() ?: 0)
}