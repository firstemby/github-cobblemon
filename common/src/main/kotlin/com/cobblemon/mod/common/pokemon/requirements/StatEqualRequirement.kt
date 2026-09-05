/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon

class StatEqualRequirement : Requirement {
    companion object {
        const val ADAPTER_VARIANT = "stat_equal"
    }

    val statOne = Stats.ATTACK.identifier.path
    val statTwo = Stats.DEFENCE.identifier.path

    override fun check(pokemon: Pokemon): Boolean {
        val one = Cobblemon.statProvider.permanentFromName(statOne) ?: return false
        val two = Cobblemon.statProvider.permanentFromName(statTwo) ?: return false
        return pokemon.getStat(one) == pokemon.getStat(two)
    }
}