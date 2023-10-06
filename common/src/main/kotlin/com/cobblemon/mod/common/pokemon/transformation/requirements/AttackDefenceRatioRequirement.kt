/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A [TransformationRequirement] for when a [Pokemon]'s attack or defence stats are higher or lower.
 *
 * @property ratio The [AttackDefenceRatio] a pokemon's stats currently have.
 *
 * @author Paul
 * @since August 13th, 2022
 */
class AttackDefenceRatioRequirement(val ratio: AttackDefenceRatio = AttackDefenceRatio.ATTACK_HIGHER) : TransformationRequirement {
    companion object {
        const val ADAPTER_VARIANT = "attack_defence_ratio"
    }
    enum class AttackDefenceRatio {
        ATTACK_HIGHER,
        DEFENCE_HIGHER,
        EQUAL
    }

    override fun check(pokemon: Pokemon): Boolean {
        return when (ratio) {
            AttackDefenceRatio.ATTACK_HIGHER -> pokemon.attack > pokemon.defence
            AttackDefenceRatio.DEFENCE_HIGHER -> pokemon.defence > pokemon.attack
            AttackDefenceRatio.EQUAL -> pokemon.attack == pokemon.defence
        }
    }
}