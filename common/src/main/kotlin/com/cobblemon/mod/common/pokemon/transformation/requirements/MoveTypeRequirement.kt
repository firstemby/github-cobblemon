/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] for when a [MoveTemplate] in the [Pokemon.moveSet] is expected to have a certain [ElementalType].
 *
 * @property type The required [ElementalType].
 *
 * @author Hiroku
 * @since August 13th, 2022
 */
class MoveTypeRequirement(val type: ElementalType = ElementalTypes.NORMAL) : TransformationRequirement {
    override fun check(pokemon: Pokemon) = pokemon.moveSet.getMoves().any { move -> move.type == type }
    companion object {
        const val ADAPTER_VARIANT = "has_move_type"
    }
}