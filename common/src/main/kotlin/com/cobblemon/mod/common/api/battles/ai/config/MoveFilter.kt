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

/** Filter for selecting specific moves based on categories, types, or custom conditions. */
data class MoveFilter(
    val categories: Set<MoveCategory>? = null,
    val types: Set<String>? = null,
    @SerializedName("move_ids")
    val moveIds: Set<String>? = null,
    val names: Set<String>? = null,
    val condition: ExpressionLike? = null
) {
    companion object {
        val CODEC: Codec<MoveFilter> = RecordCodecBuilder.create { instance ->
            instance.group(
                MoveCategory.CODEC.listOf().optionalFieldOf("categories").forGetter {
                    Optional.ofNullable(it.categories?.toList())
                },
                Codec.STRING.listOf().optionalFieldOf("types").forGetter {
                    Optional.ofNullable(it.types?.toList())
                },
                Codec.STRING.listOf().optionalFieldOf("move_ids").forGetter {
                    Optional.ofNullable(it.moveIds?.toList())
                },
                ExpressionLike.CODEC.optionalFieldOf("condition").forGetter {
                    Optional.ofNullable(it.condition)
                }
            ).apply(instance) { categories, types, moveIds, condition ->
                MoveFilter(
                    categories = categories.orElse(null)?.toSet(),
                    types = types.orElse(null)?.toSet(),
                    moveIds = moveIds.orElse(null)?.toSet(),
                    condition = condition.orElse(null)
                )
            }
        }
    }

    fun matchesAny(): Boolean {
        return categories == null && types == null && moveIds == null && names == null && condition == null
    }

    fun getAllMoveNames(): Set<String>? {
        return when {
            moveIds != null && names != null -> moveIds + names
            moveIds != null -> moveIds
            names != null -> names
            else -> null
        }
    }
}
