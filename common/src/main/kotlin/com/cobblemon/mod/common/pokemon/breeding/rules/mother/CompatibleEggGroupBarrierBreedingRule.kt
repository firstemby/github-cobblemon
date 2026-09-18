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

/**
 * A mother breeding rule which nullifies the mother decision (therefore
 * making the pair incompatible) if the egg groups of the parents
 * don't have any overlap.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
class CompatibleEggGroupBarrierBreedingRule : MotherBreedingRule {
    override val order: Int = 0

    override fun getMother(input: MotherBreedingRule.Input, mother: Pokemon?): Pokemon? {
        val parent1EggGroups = input.parent1.form.eggGroups
        val parent2EggGroups = input.parent2.form.eggGroups

        return if (parent1EggGroups.intersect(parent2EggGroups).isEmpty()) {
            null
        } else {
            mother
        }
    }
}