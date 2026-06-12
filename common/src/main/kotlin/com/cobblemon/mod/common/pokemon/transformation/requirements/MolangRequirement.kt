/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolve
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.withQueryValue

class MolangRequirement : TransformationRequirement {
    companion object {
        const val ADAPTER_VARIANT = "molang"
    }

    val condition: ExpressionLike = "true".asExpressionLike()
    val onFulfilled: ExpressionLike = "0".asExpressionLike()

    override fun check(pokemon: Pokemon): Boolean {
        mainThreadRuntime.withQueryValue("pokemon", pokemon.struct)
        return mainThreadRuntime.resolveBoolean(condition)
    }

    override fun fulfill(pokemon: Pokemon) {
        mainThreadRuntime.withQueryValue("pokemon", pokemon.struct)
        mainThreadRuntime.resolve(onFulfilled)
    }
}