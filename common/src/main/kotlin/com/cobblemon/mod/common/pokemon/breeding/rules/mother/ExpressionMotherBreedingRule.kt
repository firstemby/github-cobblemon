/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.mother

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.breeding.rule.layer.MotherBreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.withQueryValue

class ExpressionMotherBreedingRule(
    val expression: ExpressionLike = "0".asExpressionLike(),
    override val order: Int = 0
) : MotherBreedingRule {
    override fun getMother(input: MotherBreedingRule.Input, mother: Pokemon?): Pokemon? {
        input.runtime.withQueryValue("mother", mother?.struct ?: DoubleValue.ZERO)
        return (expression.resolve(input.runtime) as? ObjectValue<Pokemon>)?.obj
    }
}
