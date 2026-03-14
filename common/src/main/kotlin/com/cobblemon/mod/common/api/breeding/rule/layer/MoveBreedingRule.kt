/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule.layer

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.cobblemon.mod.common.api.breeding.rule.BreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

interface MoveBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out MoveBreedingRule>>(
            cobblemonResource("expression") to ExpressionMoveBreedingRule::class.java
        )
    }

    open class Input(
        runtime: MoLangRuntime,
        mother: Pokemon,
        father: Pokemon,
        val offspring: Pokemon
    ) : SpeciesBreedingRule.Input(runtime = runtime, mother = mother, father = father) {
        override fun addToStruct(struct: QueryStruct) {
            super.addToStruct(struct)
            struct.addFunction("offspring") { offspring.struct }
        }
    }

    fun apply(input: Input)

    class ExpressionMoveBreedingRule(
        val expression: ExpressionLike,
        override val order: Int = 0
    ) : MoveBreedingRule {
        override fun apply(input: Input) {
            expression.resolve(input.runtime)
        }
    }
}
