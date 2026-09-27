/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon

class StatCompareRequirement : Requirement {
    companion object {
        const val ADAPTER_VARIANT = "stat_compare"
    }

    val highStat = Stats.ATTACK.identifier.path
    val lowStat = Stats.DEFENCE.identifier.path

    override fun check(pokemon: Pokemon): Boolean {
        val high = Stats.getStat(highStat)
        val low = Stats.getStat(lowStat)
        if (high.type != Stat.Type.PERMANENT || low.type != Stat.Type.PERMANENT) return false
        return pokemon.getStat(high) > pokemon.getStat(low)
    }
}