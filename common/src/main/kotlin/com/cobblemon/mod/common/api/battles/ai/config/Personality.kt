/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.google.gson.annotations.SerializedName
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import kotlin.random.Random

/**
 * Personality traits of an AI trainer that influence battle decisions.
 * Each trait is a value from 0.0 to 1.0 representing a spectrum of behavior.
 */
data class Personality(
    val aggression: Double = 0.5,
    @SerializedName("risk_tolerance")
    val riskTolerance: Double = 0.5,
    @SerializedName("setup_preference")
    val setupPreference: Double = 0.5,
    val switchiness: Double = 0.5,
    @SerializedName("item_conservatism")
    val itemConservatism: Double = 0.5
) {
    companion object {
        val CODEC: Codec<Personality> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.DOUBLE.optionalFieldOf("aggression", 0.5).forGetter { it.aggression },
                Codec.DOUBLE.optionalFieldOf("risk_tolerance", 0.5).forGetter { it.riskTolerance },
                Codec.DOUBLE.optionalFieldOf("setup_preference", 0.5).forGetter { it.setupPreference },
                Codec.DOUBLE.optionalFieldOf("switchiness", 0.5).forGetter { it.switchiness },
                Codec.DOUBLE.optionalFieldOf("item_conservatism", 0.5).forGetter { it.itemConservatism }
            ).apply(instance, ::Personality)
        }

        val NEUTRAL = Personality()

        fun random(variance: Double = 0.25): Personality {
            fun randomTrait() = (0.5 + Random.nextDouble(-variance, variance)).coerceIn(0.0, 1.0)
            return Personality(
                aggression = randomTrait(),
                riskTolerance = randomTrait(),
                setupPreference = randomTrait(),
                switchiness = randomTrait(),
                itemConservatism = randomTrait()
            )
        }

        fun lerp(min: Double, max: Double, t: Double): Double = min + (max - min) * t
    }

    fun withVariance(variance: Double): Personality {
        fun vary(base: Double) = (base + Random.nextDouble(-variance, variance)).coerceIn(0.0, 1.0)
        return Personality(
            aggression = vary(aggression),
            riskTolerance = vary(riskTolerance),
            setupPreference = vary(setupPreference),
            switchiness = vary(switchiness),
            itemConservatism = vary(itemConservatism)
        )
    }
}
