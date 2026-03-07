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

/** Groups multiple moves under a single AI category for datapack loading. */
data class MoveCategoryGroup(
    val category: MoveCategory,
    val replace: Boolean = false,
    val moves: List<String> = emptyList()
) {
    companion object {
        val CODEC: Codec<MoveCategoryGroup> = RecordCodecBuilder.create { instance ->
            instance.group(
                MoveCategory.CODEC.fieldOf("category").forGetter { it.category },
                Codec.BOOL.optionalFieldOf("replace", false).forGetter { it.replace },
                Codec.STRING.listOf().optionalFieldOf("moves", emptyList()).forGetter { it.moves }
            ).apply(instance) { category, replace, moves ->
                MoveCategoryGroup(
                    category = category,
                    replace = replace,
                    moves = moves.map { it.lowercase() }
                )
            }
        }
    }
}
