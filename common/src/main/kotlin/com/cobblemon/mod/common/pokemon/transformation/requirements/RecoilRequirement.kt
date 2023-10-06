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
import com.cobblemon.mod.common.pokemon.transformation.progress.RecoilTransformationProgress

/**
 * A [TransformationRequirement] which requires a specific [amount] of recoil without fainting in order to pass.
 * It keeps track of progress through [RecoilTransformationProgress].
 *
 * @property amount The requirement amount of recoil.
 *
 * @author Licious
 * @since January 27th, 2022
 */
class RecoilRequirement(val amount: Int = 0) : TransformationRequirement {

    override fun check(pokemon: Pokemon): Boolean = pokemon.evolutionProxy.current()
        .progress()
        .filterIsInstance<RecoilTransformationProgress>()
        .any { progress -> progress.currentProgress().recoil >= this.amount }

    companion object {
        const val ADAPTER_VARIANT = "recoil"
    }

}