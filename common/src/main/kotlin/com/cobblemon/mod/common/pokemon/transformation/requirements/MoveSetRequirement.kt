/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] for when a certain [MoveTemplate] is expected in the [Pokemon.moveSet].
 *
 * @property move The required [MoveTemplate].
 *
 * @author Licious
 * @since March 21st, 2022
 */
class MoveSetRequirement(val move: MoveTemplate = Moves.getByNameOrDummy("tackle")) : TransformationRequirement {
    override fun check(pokemon: Pokemon) = pokemon.moveSet.getMoves().any { move -> move.name.equals(this.move.name, true) }
    companion object {
        const val ADAPTER_VARIANT = "has_move"
    }
}