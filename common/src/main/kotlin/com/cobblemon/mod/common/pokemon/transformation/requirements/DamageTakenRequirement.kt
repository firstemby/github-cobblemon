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
import com.cobblemon.mod.common.pokemon.transformation.progress.DamageTakenTransformationProgress

/**
 * A [TransformationRequirement] which requires a specific [amount] of damage taken in battle without fainting in order to pass.
 * It keeps track of progress through [DamageTakenRequirement].
 *
 * @property amount The required amount of damage.
 *
 * @author Licious
 * @since January 27th, 2022
 */
class DamageTakenRequirement(val amount: Int = 0) : TransformationRequirement {

    override fun check(pokemon: Pokemon): Boolean = pokemon.evolutionProxy.current()
        .progress()
        .filterIsInstance<DamageTakenTransformationProgress>()
        .any { progress -> progress.currentProgress().amount >= this.amount }

    companion object {
        const val ADAPTER_VARIANT = "damage_taken"
    }

}