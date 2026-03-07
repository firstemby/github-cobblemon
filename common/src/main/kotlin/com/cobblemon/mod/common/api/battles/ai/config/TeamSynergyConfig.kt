/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.google.gson.annotations.SerializedName
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

/** Configuration for team synergy awareness in doubles/multi battles. */
data class TeamSynergyConfig(
    val enabled: Boolean = true,
    @SerializedName("spread_move_bonus")
    val spreadMoveBonus: Double = 1.2,
    @SerializedName("ally_protection_bonus")
    val allyProtectionBonus: Double = 1.5,
    @SerializedName("weather_setup_bonus")
    val weatherSetupBonus: Double = 1.3,
    @SerializedName("terrain_setup_bonus")
    val terrainSetupBonus: Double = 1.3,
    @SerializedName("avoid_hitting_ally_penalty")
    val avoidHittingAllyPenalty: Double = 0.3,
    @SerializedName("synergy_rules")
    val synergyRules: List<SynergyRule> = emptyList()
) {
    companion object {
        val CODEC: Codec<TeamSynergyConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter { it.enabled },
                Codec.DOUBLE.optionalFieldOf("spread_move_bonus", 1.2).forGetter { it.spreadMoveBonus },
                Codec.DOUBLE.optionalFieldOf("ally_protection_bonus", 1.5).forGetter { it.allyProtectionBonus },
                Codec.DOUBLE.optionalFieldOf("weather_setup_bonus", 1.3).forGetter { it.weatherSetupBonus },
                Codec.DOUBLE.optionalFieldOf("terrain_setup_bonus", 1.3).forGetter { it.terrainSetupBonus },
                Codec.DOUBLE.optionalFieldOf("avoid_hitting_ally_penalty", 0.3).forGetter { it.avoidHittingAllyPenalty },
                SynergyRule.CODEC.listOf().optionalFieldOf("synergy_rules", emptyList()).forGetter { it.synergyRules }
            ).apply(instance, ::TeamSynergyConfig)
        }

        val DEFAULT = TeamSynergyConfig()
        val DISABLED = TeamSynergyConfig(enabled = false)
    }
}

/** A custom synergy rule that applies a bias multiplier when conditions are met. */
data class SynergyRule(
    val id: String,
    val condition: ExpressionLike,
    @SerializedName("move_condition")
    val moveCondition: ExpressionLike? = null,
    @SerializedName("bias_multiplier")
    val biasMultiplier: Double = 1.0
) {
    companion object {
        val CODEC: Codec<SynergyRule> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                ExpressionLike.CODEC.fieldOf("condition").forGetter { it.condition },
                ExpressionLike.CODEC.optionalFieldOf("move_condition").forGetter { Optional.ofNullable(it.moveCondition) },
                Codec.DOUBLE.optionalFieldOf("bias_multiplier", 1.0).forGetter { it.biasMultiplier }
            ).apply(instance) { id, condition, moveCondition, biasMultiplier ->
                SynergyRule(id, condition, moveCondition.orElse(null), biasMultiplier)
            }
        }
    }
}
