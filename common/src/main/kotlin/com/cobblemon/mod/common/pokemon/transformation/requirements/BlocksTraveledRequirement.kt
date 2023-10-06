/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] that requires a specific [amount] of [PokemonEntity.blocksTraveled] to pass.
 *
 * @property amount The amount of blocks the entity must have traversed.
 *
 * @author Licious
 * @since January 28th, 2023
 */
class BlocksTraveledRequirement(val amount: Int = 0) : TransformationRequirement {

    override fun check(pokemon: Pokemon): Boolean {
        val pokemonEntity = pokemon.entity ?: return false
        return pokemonEntity.blocksTraveled >= this.amount
    }

    companion object {
        const val ADAPTER_VARIANT = "blocks_traveled"
    }

}