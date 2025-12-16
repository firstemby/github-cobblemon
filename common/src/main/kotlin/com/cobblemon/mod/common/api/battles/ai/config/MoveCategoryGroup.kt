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
 * A category-based group that assigns multiple moves to a single AI category.
 * This is the inverse of the old MoveCategoryMapping format - instead of one move
 * with multiple categories, we have one category with multiple moves.
 *
 * Supports datapack merge behavior via the [replace] field:
 * - `replace = false` (default): Moves are added to existing category
 * - `replace = true`: All existing moves for this category are cleared first
 *
 * Example JSON:
 * ```json
 * {
 *   "category": "heal",
 *   "replace": false,
 *   "moves": ["recover", "roost", "softboiled", "moonlight"]
 * }
 * ```
 *
 * @property category The AI category these moves belong to
 * @property replace If true, replaces all existing moves for this category instead of merging
 * @property moves List of move names (Showdown ID format, e.g., "swordsdance", "flamethrower")
 *
 * @author Amo Aster
 * @since December 2025
 */
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
