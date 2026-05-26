/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/FriendshipRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] for when a certain amount of [Pokemon.friendship] is expected.
========
package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * An [Requirement] for when a certain amount of [Pokemon.friendship] is expected.
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/FriendshipRequirement.kt
 *
 * @property amount The required [Pokemon.friendship] amount, expects between 0 & 255.
 * @author Licious
 * @since March 21st, 2022
 */
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/FriendshipRequirement.kt
class FriendshipRequirement(val amount: Int = 0) : TransformationRequirement {
========
class FriendshipRequirement : Requirement {
    val amount = 0
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/FriendshipRequirement.kt
    override fun check(pokemon: Pokemon) = pokemon.friendship >= this.amount

    companion object {
        const val ADAPTER_VARIANT = "friendship"
    }
}