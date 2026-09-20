/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] for a [Pokemon.level]. Satisfied if a Pokemon's level falls between [minLevel] and [maxLevel].
 *
 * @property minLevel The minimum level for this requirement to be satisfied.
 * @property maxLevel The maximum level that this requirement can be satisfied.
 *
 * @author Licious
 * @since March 21st, 2022
 */
class LevelRequirement(val minLevel: Int = 1, val maxLevel: Int = Cobblemon.config.maxPokemonLevel) : TransformationRequirement {
    companion object {
        const val ADAPTER_VARIANT = "level"
    }

    override fun check(pokemon: Pokemon) = pokemon.level in minLevel..maxLevel
}