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

/** Defines conditions and preferences for using battle gimmicks. */
data class GimmickRule(
    @SerializedName("gimmick_type")
    val gimmickType: GimmickType,
    val condition: ExpressionLike,
    val priority: Int = 0,
    @SerializedName("bias_multiplier")
    val biasMultiplier: Double = 1.0,
    @SerializedName("move_filter")
    val moveFilter: MoveFilter? = null
) {
    companion object {
        val CODEC: Codec<GimmickRule> = RecordCodecBuilder.create { instance ->
            instance.group(
                GimmickType.CODEC.fieldOf("gimmick_type").forGetter { it.gimmickType },
                ExpressionLike.CODEC.fieldOf("condition").forGetter { it.condition },
                Codec.INT.optionalFieldOf("priority", 0).forGetter { it.priority },
                Codec.DOUBLE.optionalFieldOf("bias_multiplier", 1.0).forGetter { it.biasMultiplier },
                MoveFilter.CODEC.optionalFieldOf("move_filter").forGetter { java.util.Optional.ofNullable(it.moveFilter) }
            ).apply(instance) { gimmickType, condition, priority, biasMultiplier, moveFilter ->
                GimmickRule(gimmickType, condition, priority, biasMultiplier, moveFilter.orElse(null))
            }
        }
    }
}
