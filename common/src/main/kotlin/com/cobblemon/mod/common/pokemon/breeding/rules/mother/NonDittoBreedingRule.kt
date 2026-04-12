/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.mother

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.breeding.rule.layer.MotherBreedingRule
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Decides the logical mother of a pairing by making sure that whichever one is not a Ditto is the mother.
 *
 * Cases:
 * 1: Both are Ditto and config.canBreedDitto is false -> No logical mother
 * 2: Both are Ditto and config.canBreedDitto is true -> Randomly choose logical mother
 * 2: Parent 1 is Ditto -> Parent 2 is logical mother
 * 3: Parent 2 is Ditto -> Parent 1 is logical mother
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
class NonDittoBreedingRule : MotherBreedingRule {
    override val order = 0
    override fun getMother(input: MotherBreedingRule.Input, mother: Pokemon?): Pokemon? {
        val ditto = EggGroup.DITTO
        val parent1IsDitto = ditto in input.parent1.form.eggGroups
        val parent2IsDitto = ditto in input.parent2.form.eggGroups

        return when {
            parent1IsDitto && !parent2IsDitto -> input.parent2
            !parent1IsDitto && parent2IsDitto -> input.parent1
            parent1IsDitto && parent2IsDitto -> if (Cobblemon.config.canBreedDitto) listOf(input.parent1, input.parent2).random() else null
            else -> mother
        }
    }
}