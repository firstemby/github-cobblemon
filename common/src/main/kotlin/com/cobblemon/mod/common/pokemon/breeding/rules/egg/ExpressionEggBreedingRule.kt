/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.egg

import com.cobblemon.mod.common.api.breeding.rule.layer.EggBreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolve

class ExpressionEggBreedingRule(
    val expression: ExpressionLike = "0".asExpressionLike(),
    override val order: Int = 0
): EggBreedingRule {
    override fun apply(input: EggBreedingRule.Input) {
        input.runtime.resolve(expression)
    }
}