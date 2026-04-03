/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.species

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.breeding.rule.layer.SpeciesBreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.withQueryValue

class ExpressionSpeciesBreedingRule(
    val expression: ExpressionLike = "0".asExpressionLike(),
    override val order: Int = 0
) : SpeciesBreedingRule {
    override fun getOffspringSpecies(input: SpeciesBreedingRule.Input, offspringSpecies: Species?): Species? {
        input.runtime.withQueryValue("offspring_species", offspringSpecies?.struct ?: DoubleValue.ZERO)
        return (expression.resolve(input.runtime) as? ObjectValue<Species>)?.obj ?: offspringSpecies
    }
}
