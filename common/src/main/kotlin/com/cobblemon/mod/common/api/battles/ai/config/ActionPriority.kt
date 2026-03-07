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

/** Defines a priority rule for AI action selection. */
data class ActionPriority(
    val id: String,
    val condition: ExpressionLike,
    @SerializedName("action_type")
    val actionType: ActionType,
    @SerializedName("move_filter")
    val moveFilter: MoveFilter? = null,
    val priority: Int = 0,
    @SerializedName("bias_multiplier")
    val biasMultiplier: Double = 1.5,
    @SerializedName("score_bonus")
    val scoreBonus: Double = 0.0
) {
    companion object {
        val CODEC: Codec<ActionPriority> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                ExpressionLike.CODEC.fieldOf("condition").forGetter { it.condition },
                ActionType.CODEC.fieldOf("action_type").forGetter { it.actionType },
                MoveFilter.CODEC.optionalFieldOf("move_filter").forGetter { Optional.ofNullable(it.moveFilter) },
                Codec.INT.optionalFieldOf("priority", 0).forGetter { it.priority },
                Codec.DOUBLE.optionalFieldOf("bias_multiplier", 1.5).forGetter { it.biasMultiplier },
                Codec.DOUBLE.optionalFieldOf("score_bonus", 0.0).forGetter { it.scoreBonus }
            ).apply(instance) { id, condition, actionType, moveFilter, priority, biasMultiplier, scoreBonus ->
                ActionPriority(id, condition, actionType, moveFilter.orElse(null), priority, biasMultiplier, scoreBonus)
            }
        }
    }
}
