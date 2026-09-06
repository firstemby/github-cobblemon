/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.mother

import com.cobblemon.mod.common.api.breeding.rule.layer.MotherBreedingRule
import com.cobblemon.mod.common.pokemon.Pokemon

class EmptyMotherBreedingRule(
    override val order: Int = 0
) : MotherBreedingRule {
    override fun getMother(input: MotherBreedingRule.Input, mother: Pokemon?) = mother
}
