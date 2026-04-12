/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.shiny

import com.cobblemon.mod.common.api.breeding.rule.layer.PokemonBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.ShinyBreedingRule

class EmptyShinyBreedingRule(
    override val order: Int = 0
) : ShinyBreedingRule {
    override fun getShinyChance(input: PokemonBreedingRule.Input, shinyRate: Float) = shinyRate
}
