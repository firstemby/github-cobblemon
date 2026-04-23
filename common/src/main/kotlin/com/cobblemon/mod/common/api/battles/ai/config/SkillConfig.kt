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

/** Configuration for AI skill and intelligence settings. */
data class SkillConfig(
    val level: Int = 5,
    @SerializedName("predict_opponent_moves")
    val predictOpponentMoves: Boolean = true,
    @SerializedName("consider_type_matchups")
    val considerTypeMatchups: Boolean = true,
    @SerializedName("use_damage_calc")
    val useDamageCalc: Boolean = false
) {
    companion object {
        val CODEC: Codec<SkillConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.optionalFieldOf("level", 5).forGetter { it.level },
                Codec.BOOL.optionalFieldOf("predict_opponent_moves", true).forGetter { it.predictOpponentMoves },
                Codec.BOOL.optionalFieldOf("consider_type_matchups", true).forGetter { it.considerTypeMatchups },
                Codec.BOOL.optionalFieldOf("use_damage_calc", false).forGetter { it.useDamageCalc }
            ).apply(instance, ::SkillConfig)
        }

        val DEFAULT = SkillConfig()

        val LOW = SkillConfig(
            level = 2,
            predictOpponentMoves = false,
            considerTypeMatchups = true,
            useDamageCalc = false
        )

        val MEDIUM = SkillConfig(
            level = 3,
            predictOpponentMoves = true,
            considerTypeMatchups = true,
            useDamageCalc = false
        )

        val HIGH = SkillConfig(
            level = 5,
            predictOpponentMoves = true,
            considerTypeMatchups = true,
            useDamageCalc = true
        )
    }
}
