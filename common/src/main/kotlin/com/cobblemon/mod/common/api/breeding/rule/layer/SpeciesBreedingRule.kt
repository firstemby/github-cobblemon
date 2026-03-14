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
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.breeding.rule.BreedingRule
import com.cobblemon.mod.common.api.breeding.rule.BreedingRuleInputs
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asStruct
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.resources.ResourceLocation

interface SpeciesBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out SpeciesBreedingRule>>(
            cobblemonResource("expression") to ExpressionSpeciesBreedingRule::class.java
        )
    }

    open class Input(
        override val runtime: MoLangRuntime,
        val mother: Pokemon,
        val father: Pokemon,
    ): BreedingRuleInputs {
        override fun addToStruct(struct: QueryStruct) {
            struct.addFunction("mother") { mother.asStruct() }
            struct.addFunction("father") { father.asStruct() }
        }
    }

    fun getOffspringSpecies(input: Input, offspringSpecies: Species?): Species?

    class ExpressionSpeciesBreedingRule(
        val expression: ExpressionLike = "0".asExpressionLike(),
        override val order: Int = 0
    ) : SpeciesBreedingRule {
        override fun getOffspringSpecies(input: Input, offspringSpecies: Species?): Species? {
            input.runtime.withQueryValue("offspring_species") { offspringSpecies?.struct ?: DoubleValue.ZERO }
            return (expression.resolve(input.runtime) as? ObjectValue<Species>)?.obj ?: offspringSpecies
        }
    }
}