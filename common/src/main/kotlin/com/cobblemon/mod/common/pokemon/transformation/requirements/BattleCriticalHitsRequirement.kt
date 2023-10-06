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
import com.cobblemon.mod.common.pokemon.transformation.progress.LastBattleCriticalHitsTransformationProgress

/**
 * A [TransformationRequirement] for a certain amount of critical hits in a single battle.
 *
 * @property amount The amount of critical hits required.
 *
 * @author Licious
 * @since October 2nd, 2022
 */
class BattleCriticalHitsRequirement(val amount: Int = 0) : TransformationRequirement {

    override fun check(pokemon: Pokemon): Boolean = pokemon.evolutionProxy.current()
        .progress()
        .filterIsInstance<LastBattleCriticalHitsTransformationProgress>()
        .any { progress -> progress.currentProgress().amount >= this.amount }

    companion object {
        const val ADAPTER_VARIANT = "battle_critical_hits"
    }

}