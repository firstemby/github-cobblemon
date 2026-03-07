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
 * Defines conditions under which the AI should consider switching Pokémon.
 *
 * @property id Unique identifier for this switch condition
 * @property condition Molang condition evaluated for the current active Pokémon
 * @property targetCondition Optional condition to filter which Pokémon to switch to
 * @property priority Higher priority conditions are evaluated first (0-100 typical range)
 * @property biasMultiplier Multiplier applied to switch action score when this triggers
 *
 * @author Amo Aster
 * @since December 2025
 */
data class SwitchCondition(
    val id: String,
    val condition: ExpressionLike,
    @SerializedName("target_condition")
    val targetCondition: ExpressionLike? = null,
    val priority: Int = 0,
    @SerializedName("bias_multiplier")
    val biasMultiplier: Double = 1.2
) {
    companion object {
        val CODEC: Codec<SwitchCondition> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                ExpressionLike.CODEC.fieldOf("condition").forGetter { it.condition },
                ExpressionLike.CODEC.optionalFieldOf("target_condition").forGetter { Optional.ofNullable(it.targetCondition) },
                Codec.INT.optionalFieldOf("priority", 0).forGetter { it.priority },
                Codec.DOUBLE.optionalFieldOf("bias_multiplier", 1.2).forGetter { it.biasMultiplier }
            ).apply(instance) { id, condition, targetCondition, priority, biasMultiplier ->
                SwitchCondition(id, condition, targetCondition.orElse(null), priority, biasMultiplier)
            }
        }
    }
}
