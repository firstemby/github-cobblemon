/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config.effects

/**
 * Environment-related effects for held items.
 *
 * @property weatherBoost Weather type this item benefits from.
 * @property terrainBoost Terrain type this item benefits from.
 * @property weatherDamageImmunity Weather types whose damage this item negates.
 *
 * @author Amo Aster
 * @since December 2025
 */
data class EnvironmentEffect(
    val weatherBoost: String? = null,
    val terrainBoost: String? = null,
    val weatherDamageImmunity: List<String>? = null
) {
    companion object {
        val NONE = EnvironmentEffect()
    }
}
