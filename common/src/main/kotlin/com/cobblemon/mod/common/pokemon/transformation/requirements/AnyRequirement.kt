/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] that succeeds when any of the [possibilities] are valid.
 *
 * @property possibilities A collection of possible [TransformationRequirement]s that can allow this requirement to be valid.
 *
 * @author Licious
 * @since March 28th, 2023
 */
class AnyRequirement(val possibilities: Collection<TransformationRequirement>) : TransformationRequirement {
    override fun check(pokemon: Pokemon) = this.possibilities.any { it.check(pokemon) }
    companion object {
        const val ADAPTER_VARIANT = "any"
    }
}