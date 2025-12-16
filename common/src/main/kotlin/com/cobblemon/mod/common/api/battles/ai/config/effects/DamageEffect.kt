/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config.effects

/**
 * Damage-related effects for held items.
 *
 * @property multiplier Flat damage multiplier for all attacks (e.g., 1.3 for Life Orb).
 * @property typeBoosts Type-specific damage multipliers (e.g., {"fire": 1.2} for Charcoal).
 * @property superEffectiveMultiplier Extra multiplier for super effective hits.
 * @property condition Molang condition for when this effect applies.
 *
 * @author Amo Aster
 * @since December 2025
 */
data class DamageEffect(
    val multiplier: Double = 1.0,
    val typeBoosts: Map<String, Double> = emptyMap(),
    val superEffectiveMultiplier: Double? = null,
    val condition: String? = null
) {
    fun getTypeMultiplier(type: String): Double {
        return typeBoosts[type.lowercase()] ?: 1.0
    }

    fun boostsType(type: String): Boolean {
        val mult = typeBoosts[type.lowercase()] ?: return false
        return mult > 1.0
    }

    companion object {
        val NONE = DamageEffect()
    }
}
