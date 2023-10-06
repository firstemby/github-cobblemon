/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] that checks if one of a [Pokemon]'s stats is greater than another.
 *
 * @property highStat The [Stat] expecting to be greater.
 * @property lowStat The [Stat] comparing against.
 *
 * @author TimInc
 * @since May 14, 2024
 */
class StatCompareRequirement(val highStat: Stat = Stats.ATTACK, val lowStat: Stat = Stats.DEFENCE) : TransformationRequirement {
    companion object {
        const val ADAPTER_VARIANT = "stat_compare"
    }

    override fun check(pokemon: Pokemon): Boolean {
        return pokemon.getStat(highStat) > pokemon.getStat(lowStat)
    }
}