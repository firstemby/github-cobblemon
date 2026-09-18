/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.mother

import com.cobblemon.mod.common.api.breeding.rule.layer.MotherBreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolveBoolean

/**
 * Nullifies the mother if either parent is in a banned egg group, unless [condition] is false.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
class BannedEggGroupsBarrierBreedingRule : MotherBreedingRule {
    override val order = 0
    val condition: ExpressionLike = "true".asExpressionLike()
    val bannedEggGroups = mutableSetOf(EggGroup.UNDISCOVERED)

    override fun getMother(input: MotherBreedingRule.Input, mother: Pokemon?): Pokemon? {
        if (!input.runtime.resolveBoolean(condition)) {
            return mother
        }

        return if (bannedEggGroups.any { it in input.parent1.form.eggGroups || it in input.parent2.form.eggGroups }) {
            null
        } else {
            mother
        }
    }
}