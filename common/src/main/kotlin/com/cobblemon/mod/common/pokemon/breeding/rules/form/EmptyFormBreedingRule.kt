/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.form

import com.cobblemon.mod.common.api.breeding.rule.layer.FormBreedingRule
import com.cobblemon.mod.common.api.pokemon.PokemonProperties

class EmptyFormBreedingRule(
    override val order: Int = 0
) : FormBreedingRule {
    override fun getOffspringFormModifier(input: FormBreedingRule.Input, modifier: PokemonProperties) = modifier
}
