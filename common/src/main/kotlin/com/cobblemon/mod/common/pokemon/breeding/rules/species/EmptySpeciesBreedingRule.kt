/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.species

import com.cobblemon.mod.common.api.breeding.rule.layer.SpeciesBreedingRule
import com.cobblemon.mod.common.pokemon.Species

class EmptySpeciesBreedingRule(
    override val order: Int = 0
) : SpeciesBreedingRule {
    override fun getOffspringSpecies(input: SpeciesBreedingRule.Input, offspringSpecies: Species?) = offspringSpecies
}
