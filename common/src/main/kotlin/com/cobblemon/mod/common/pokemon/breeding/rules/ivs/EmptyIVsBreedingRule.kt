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

class EmptyIVsBreedingRule(
    override val order: Int = 0
) : IVsBreedingRule {
    override fun calculateOffspringIVs(input: PokemonBreedingRule.Input, data: IVsBreedingRule.IVInheritanceData) {}
}
