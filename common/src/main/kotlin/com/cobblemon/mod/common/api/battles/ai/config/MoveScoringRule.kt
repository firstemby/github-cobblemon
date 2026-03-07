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

/** A rule that modifies the score of moves during AI evaluation. */
data class MoveScoringRule(
    val id: String,
    val condition: ExpressionLike,
    @SerializedName("move_condition")
    val moveCondition: ExpressionLike? = null,
    @SerializedName("score_modifier")
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
