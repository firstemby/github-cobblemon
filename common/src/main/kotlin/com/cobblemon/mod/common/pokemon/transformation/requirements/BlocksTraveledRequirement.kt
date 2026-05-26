import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/BlocksTraveledRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
========
package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/BlocksTraveledRequirement.kt
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon

/**
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/BlocksTraveledRequirement.kt
 * A [TransformationRequirement] that requires a specific [amount] of [PokemonEntity.blocksTraveled] to pass.
========
 * An [Requirement] that requires a specific [amount] of [PokemonEntity.blocksTraveled] to pass.
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/BlocksTraveledRequirement.kt
 *
 * @property amount The amount of blocks the entity must have traversed.
 *
 * @author Licious
 * @since January 28th, 2023
 */
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/BlocksTraveledRequirement.kt
class BlocksTraveledRequirement(val amount: Int = 0) : TransformationRequirement {
========
class BlocksTraveledRequirement(amount: Int) : Requirement {

    constructor() : this(0)

    val amount: Int = amount
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/BlocksTraveledRequirement.kt

    override fun check(pokemon: Pokemon): Boolean {
        return pokemon.getBlocksTraveled() >= this.amount
    }

    companion object {
        const val ADAPTER_VARIANT = "blocks_traveled"
    }

}
