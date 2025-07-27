/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cooking

import com.cobblemon.mod.common.item.components.FoodComponent
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

data class Food(
    val hunger: Int = 0,
    val saturation: Float = 0f,
    val mobEffects: List<SerializableMobEffectInstance>? = null
) {
    companion object {
        val CODEC: Codec<Food> = RecordCodecBuilder.create { builder ->
            builder.group(
                Codec.INT.optionalFieldOf("hunger", 0).forGetter { it.hunger },
                Codec.FLOAT.optionalFieldOf("saturation", 0f).forGetter { it.saturation },
                SerializableMobEffectInstance.CODEC.listOf().optionalFieldOf("mobEffects").forGetter { Optional.ofNullable(it.mobEffects) }
            ).apply(builder) { hunger, saturation, mobEffects ->
                Food(hunger, saturation, mobEffects.getOrNull())
            }
        }
    }

    fun toComponent(): FoodComponent = FoodComponent(hunger, saturation, mobEffects?.map { it.toInstance() })
}