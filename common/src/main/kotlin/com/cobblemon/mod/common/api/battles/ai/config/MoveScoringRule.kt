/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

/**
 * A rule that modifies the score of moves during AI evaluation.
 * Rules are applied in order, with higher priority rules first.
 *
 * @property id Unique identifier for this scoring rule
 * @property condition Molang condition that must be true for this rule to apply
 * @property moveCondition Optional additional condition specific to the move being evaluated
 * @property scoreModifier How to modify the move's score when conditions are met
 * @property priority Higher priority rules are applied first (0-100 typical range)
 *
 * @author Amo Aster
 * @since December 2025
 */
data class MoveScoringRule(
    val id: String,
    val condition: ExpressionLike,
    val moveCondition: ExpressionLike? = null,
    val scoreModifier: ScoreModifier,
    val priority: Int = 0
) {
    companion object {
        val CODEC: Codec<MoveScoringRule> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                ExpressionLike.CODEC.fieldOf("condition").forGetter { it.condition },
                ExpressionLike.CODEC.optionalFieldOf("move_condition").forGetter { Optional.ofNullable(it.moveCondition) },
                ScoreModifier.CODEC.fieldOf("score_modifier").forGetter { it.scoreModifier },
                Codec.INT.optionalFieldOf("priority", 0).forGetter { it.priority }
            ).apply(instance) { id, condition, moveCondition, scoreModifier, priority ->
                MoveScoringRule(id, condition, moveCondition.orElse(null), scoreModifier, priority)
            }
        }
    }
}
