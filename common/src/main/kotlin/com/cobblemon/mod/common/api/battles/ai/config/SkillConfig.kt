/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Configuration for AI skill and intelligence settings.
 * Controls how "smart" the AI plays and what advanced features it uses.
 *
 * @property level Skill level 0-5, affects decision accuracy (higher = smarter)
 * @property predictOpponentMoves Whether to try predicting opponent's moves
 * @property considerTypeMatchups Whether to factor in type effectiveness
 * @property useDamageCalc Whether to use full damage calculation for move scoring (expensive but accurate)
 *
 * @author Amo Aster
 * @since December 2025
 */
data class SkillConfig(
    val level: Int = 5,
    val predictOpponentMoves: Boolean = true,
    val considerTypeMatchups: Boolean = true,
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

        /** Low skill - makes mistakes, doesn't predict */
        val LOW = SkillConfig(
            level = 2,
            predictOpponentMoves = false,
            considerTypeMatchups = true,
            useDamageCalc = false
        )

        /** Medium skill - decent play */
        val MEDIUM = SkillConfig(
            level = 3,
            predictOpponentMoves = true,
            considerTypeMatchups = true,
            useDamageCalc = false
        )

        /** High skill - smart play with damage calc */
        val HIGH = SkillConfig(
            level = 5,
            predictOpponentMoves = true,
            considerTypeMatchups = true,
            useDamageCalc = true
        )
    }
}
