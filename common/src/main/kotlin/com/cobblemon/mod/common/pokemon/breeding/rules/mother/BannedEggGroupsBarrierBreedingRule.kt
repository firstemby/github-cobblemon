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
    val bannedLabels = mutableSetOf<String>()
    val allowSameLine: ExpressionLike = "false".asExpressionLike()
    val allowDitto: ExpressionLike = "false".asExpressionLike()

    override fun getMother(input: MotherBreedingRule.Input, mother: Pokemon?): Pokemon? {
        if (!input.runtime.resolveBoolean(condition)) {
            return mother
        }

        return if (bannedEggGroups.any { it in input.parent1.form.eggGroups || it in input.parent2.form.eggGroups } || bannedLabels.any { it in input.parent1.species.labels || it in input.parent2.species.labels }) {
            // I think this is the best place to do this, it looks ugly but best I got for now.... We need a way so that we can have the config allow for same species that share certain egg groups (usually restricted) to breed
            if ((input.runtime.resolveBoolean(allowDitto) && (EggGroup.DITTO in input.parent1.form.eggGroups || EggGroup.DITTO in input.parent2.form.eggGroups)) || (input.runtime.resolveBoolean(allowSameLine) && generateSequence(input.parent1.species) { it.preEvolution?.species }.last() == generateSequence(input.parent2.species) { it.preEvolution?.species }.last())) mother else null
        } else {
            mother
        }
    }
}