import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.progress.UseMoveTransformationProgress

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/UseMoveRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
========
package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/UseMoveRequirement.kt
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.progress.UseMoveTransformationProgress

/**
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/UseMoveRequirement.kt
 * A [TransformationRequirement] meant to require a move to have been used a specific amount of times.
========
 * An [Requirement] meant to require a move to have been used a specific amount of times.
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/UseMoveRequirement.kt
 *
 * @property move The [MoveTemplate] expected to be used.
 * @property amount The amount of times it has been used.
 *
 * @author Licious
 * @since January 25th, 2023
 */
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/UseMoveRequirement.kt
class UseMoveRequirement(val move: MoveTemplate = Moves.getByNameOrDummy(""), val amount: Int = 1) : TransformationRequirement {
========
class UseMoveRequirement(move: MoveTemplate, amount: Int) : Requirement {

    constructor() : this(Moves.getByNameOrDummy(""), 1)

    val move: MoveTemplate = move
    val amount: Int = amount
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/UseMoveRequirement.kt

    override fun check(pokemon: Pokemon): Boolean = pokemon.evolutionProxy.current()
        .progress()
        .filterIsInstance<UseMoveTransformationProgress>()
        .any { progress -> progress.currentProgress().move == this.move && progress.currentProgress().amount >= this.amount }

    companion object {
        const val ADAPTER_VARIANT = "use_move"
    }

}