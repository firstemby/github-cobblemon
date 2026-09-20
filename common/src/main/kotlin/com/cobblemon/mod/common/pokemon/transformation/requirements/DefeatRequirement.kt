/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.progress.DefeatTransformationProgress

/**
 * A [TransformationRequirement] which requires a specific [amount] of [target]s defeated to pass.
 *
 * @property target The [PokemonProperties] of the target Pokémon.
 * @property amount The amount of [target]s to faint.
 *
 * @author Licious
 * @since January 28th, 2022
 */
class DefeatRequirement(val target: PokemonProperties = PokemonProperties(), val amount: Int = 0) : TransformationRequirement {
    override fun check(pokemon: Pokemon): Boolean = pokemon.requirementProgress
        .filterIsInstance<DefeatTransformationProgress>()
        .any { it.currentProgress().target.originalString.equals(this.target.originalString, true) && it.currentProgress().amount >= this.amount }

    companion object {
        const val ADAPTER_VARIANT = "defeat"
    }
}