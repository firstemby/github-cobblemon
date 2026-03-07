/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config.effects

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
