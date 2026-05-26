/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/PartyMemberRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
========
package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/PartyMemberRequirement.kt
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.pokemon.Pokemon

/**
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/PartyMemberRequirement.kt
 * A [TransformationRequirement] for when the party needs to either contain or not a specific match for [target] based on the [contains] property.
========
 * An [Requirement] for when the party needs to either contain or not a specific match for [target] based on the [contains] property.
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/PartyMemberRequirement.kt
 *
 * @property target The matcher for the party members.
 * @property contains If this requirement will need the [target] to be present or not.
 * @property fuse If this [Transformation] will fuse the [target] into this Pokemon.
 *
 * @author Licious
 * @since March 21st, 2022
 */
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/PartyMemberRequirement.kt
class PartyMemberRequirement(
    val target: PokemonProperties = PokemonProperties(),
    val contains: Boolean = true,
    val fuse: Boolean = false
) : TransformationRequirement {
========
class PartyMemberRequirement : Requirement {
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/PartyMemberRequirement.kt
    companion object {
        const val ADAPTER_VARIANT = "party_member"
    }

    override fun fulfill(pokemon: Pokemon) {
        // TODO: fusion (kyurem, calyrex)
    }

    override fun check(pokemon: Pokemon): Boolean {
        val party = pokemon.storeCoordinates.get()?.store as? PartyStore ?: return false
        val has = party.any { member -> member.uuid != pokemon.uuid && this.target.matches(member) }
        return this.contains == has
    }
}