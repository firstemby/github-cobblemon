/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/PokemonPropertiesRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] for when the [Pokemon] must match [PokemonProperties.matches].
========
package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * An [Requirement] for when the [Pokemon] must match [PokemonProperties.matches].
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/PokemonPropertiesRequirement.kt
 *
 * @property target The matcher for this requirement.
 *
 * @author Licious
 * @since March 26th, 2022
 */
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/PokemonPropertiesRequirement.kt
class PokemonPropertiesRequirement(val target: PokemonProperties = PokemonProperties()) : TransformationRequirement {
========
class PokemonPropertiesRequirement : Requirement {
    val target = PokemonProperties()
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/PokemonPropertiesRequirement.kt
    override fun check(pokemon: Pokemon) = this.target.matches(pokemon)

    companion object {
        const val ADAPTER_VARIANT = "properties"
    }
}