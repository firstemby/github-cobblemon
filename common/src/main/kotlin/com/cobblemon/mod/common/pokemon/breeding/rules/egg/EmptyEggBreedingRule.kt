/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.egg

import com.cobblemon.mod.common.api.breeding.rule.layer.EggBreedingRule

class EmptyEggBreedingRule(
    override val order: Int = 0
) : EggBreedingRule {
    override fun getHatchTime(input: EggBreedingRule.Input, hatchTicks: Int) = hatchTicks
}
