/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/MoveTypeRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
========
package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/MoveTypeRequirement.kt
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.pokemon.Pokemon

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/MoveTypeRequirement.kt
/**
 * A [TransformationRequirement] for when a [MoveTemplate] in the [Pokemon.moveSet] is expected to have a certain [ElementalType].
 *
 * @property type The required [ElementalType].
 *
 * @author Hiroku
 * @since August 13th, 2022
 */
class MoveTypeRequirement(val type: ElementalType = ElementalTypes.NORMAL) : TransformationRequirement {
========
class MoveTypeRequirement : Requirement {
    val type: ElementalType = ElementalTypes.NORMAL
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/MoveTypeRequirement.kt
    override fun check(pokemon: Pokemon) = pokemon.moveSet.getMoves().any { move -> move.type == type }
    companion object {
        const val ADAPTER_VARIANT = "has_move_type"
    }
}