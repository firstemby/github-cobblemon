/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.ivs

import com.cobblemon.mod.common.api.breeding.rule.layer.IVsBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.PokemonBreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolveInt

class InheritRandomIVsBreedingRule : IVsBreedingRule {
    override val order = 0
    var count: ExpressionLike = "1".asExpressionLike()
    override fun calculateOffspringIVs(input: PokemonBreedingRule.Input, data: IVsBreedingRule.IVInheritanceData) {
        var count = input.runtime.resolveInt(count)
        val heritableStats = Stats.PERMANENT.filter { it !in data.inheritedStats }.toMutableList()
        val parents = listOf(input.mother, input.father)
        while (count-- > 0 && heritableStats.isNotEmpty()) {
            val stat = heritableStats.random()
            data.inherit(parents.random(), stat)
            heritableStats.remove(stat)
        }
    }
}