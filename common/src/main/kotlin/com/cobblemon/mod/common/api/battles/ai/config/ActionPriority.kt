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

/**
 * Defines a priority rule for AI action selection.
 * When the condition is met, the specified action type receives a bias boost.
 *
 * @property id Unique identifier for this priority rule
 * @property condition Molang condition that triggers this priority
 * @property actionType The type of action to prioritize
 * @property moveFilter Optional filter for which moves this applies to (only for MOVE action type)
 * @property priority Higher priority rules are evaluated first (0-100 typical range)
 * @property biasMultiplier Multiplier applied to action scores when this rule triggers
 *
 * @author Amo Aster
 * @since December 2025
 */
data class ActionPriority(
    val id: String,
    val condition: ExpressionLike,
    @SerializedName("action_type")
    val actionType: ActionType,
    @SerializedName("move_filter")
    val moveFilter: MoveFilter? = null,
    val priority: Int = 0,
    @SerializedName("bias_multiplier")
    val biasMultiplier: Double = 1.5
) {
    companion object {
        val CODEC: Codec<ActionPriority> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                ExpressionLike.CODEC.fieldOf("condition").forGetter { it.condition },
                ActionType.CODEC.fieldOf("action_type").forGetter { it.actionType },
                MoveFilter.CODEC.optionalFieldOf("move_filter").forGetter { Optional.ofNullable(it.moveFilter) },
                Codec.INT.optionalFieldOf("priority", 0).forGetter { it.priority },
                Codec.DOUBLE.optionalFieldOf("bias_multiplier", 1.5).forGetter { it.biasMultiplier }
            ).apply(instance) { id, condition, actionType, moveFilter, priority, biasMultiplier ->
                ActionPriority(id, condition, actionType, moveFilter.orElse(null), priority, biasMultiplier)
            }
        }
    }
}
