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
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveInt

/**
 * Sets the amount of IVs to inherit from the parents.
 *
 * @param condition An optional molang expression that determines whether this rule should be applied.
 * @param count The amount of IVs to inherit.
 * @param order The order in which this rule should be applied.
 */
class SetInheritedIVCountBreedingRule(
    val condition: ExpressionLike = "true".asExpressionLike(),
    val count: ExpressionLike = "1".asExpressionLike(),
    override val order: Int = 0
) : IVsBreedingRule {
    override fun calculateOffspringIVs(input: PokemonBreedingRule.Input, data: IVsBreedingRule.IVInheritanceData) {
        val meetsCondition = input.runtime.resolveBoolean(condition)
        if (!meetsCondition) {
            return
        }
        val count = input.runtime.resolveInt(count)
        data.statsToInherit = count
    }
}