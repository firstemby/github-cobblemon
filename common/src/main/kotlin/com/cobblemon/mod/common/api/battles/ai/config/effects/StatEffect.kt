/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config.effects

/**
 * Stat-related effects for held items.
 *
 * @property multipliers Stat name to multiplier mapping (e.g., {"attack": 1.5} for Choice Band).
 * @property accuracyMultiplier Accuracy modifier.
 * @property criticalHitModifier Critical hit stage modifier.
 * @property condition Molang condition for when this effect applies.
 *
 * @author Amo Aster
 * @since December 2025
 */
data class StatEffect(
    val multipliers: Map<String, Double> = emptyMap(),
    val accuracyMultiplier: Double? = null,
    val criticalHitModifier: Int? = null,
    val condition: String? = null
) {
    fun getStatMultiplier(stat: String): Double {
        return multipliers[stat.lowercase()] ?: 1.0
    }

    companion object {
        val NONE = StatEffect()
    }
}
