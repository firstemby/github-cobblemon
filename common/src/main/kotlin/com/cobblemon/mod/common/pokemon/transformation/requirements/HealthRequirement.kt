/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] that succeeds when [Pokemon.currentHealth] is greater than [ratio] of [Pokemon.maxHealth].
 *
 * @property ratio The minimum proportion of maxHealth needed to pass this requirement.
 *
 * @author Segfault Guy
 * @since April 29th, 2025
 */
class HealthRequirement(val ratio: Float = 1.0f) : TransformationRequirement {
    override fun check(pokemon: Pokemon) = pokemon.currentHealth > (pokemon.maxHealth * ratio)
    companion object {
        const val ADAPTER_VARIANT = "health"
    }
}