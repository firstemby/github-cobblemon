/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.fishing

object SpawnBaitUtils {
    fun mergeEffects(effects: List<SpawnBait.Effect>): List<SpawnBait.Effect> {
        val grouped = effects.groupBy { Pair(it.type, it.subcategory) }

        return grouped.map { (key, groupEffects) ->
            val (type, subcategory) = key

            if (type == SpawnBait.Effects.RARITY_BUCKET) {
                // we want to keep the highest rarity bucket effect instead of merging them
                groupEffects.maxByOrNull { it.value }!!
            } else {
                val count = groupEffects.size
                val totalChance = groupEffects.sumOf { it.chance }
                val totalValue = groupEffects.sumOf { it.value }

                val multiplier = when (count) {
                    2 -> 0.8
                    3 -> 0.9
                    else -> 1.0
                }

                val adjustedChance = (totalChance * multiplier).coerceAtMost(1.0)
                val adjustedValue = totalValue * multiplier

                SpawnBait.Effect(type, subcategory, adjustedChance, adjustedValue)
            }
        }
    }
}