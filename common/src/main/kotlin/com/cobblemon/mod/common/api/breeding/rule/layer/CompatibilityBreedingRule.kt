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
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.resources.ResourceLocation

interface CompatibilityBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out CompatibilityBreedingRule>>(
            cobblemonResource("expression") to ExpressionCompatibilityBreedingRule::class.java
        )
    }

    class Input(
        override val runtime: MoLangRuntime,
        val parent1: Pokemon,
        val parent2: Pokemon,
    ) : BreedingRuleInputs {
        override fun addToStruct(struct: QueryStruct) {
            struct.addFunction("parents") { listOf(parent1.struct, parent2.struct).asArrayValue() }
            struct.addFunction("parent_1") { parent1.struct }
            struct.addFunction("parent_2") { parent2.struct }
        }
    }

    fun getCompatible(input: Input, compatible: Boolean): Boolean

    class ExpressionCompatibilityBreedingRule(
        val expression: ExpressionLike = "0".asExpressionLike(),
        override val order: Int = 0
    ) : CompatibilityBreedingRule {
        override fun getCompatible(input: Input, compatible: Boolean): Boolean {
            input.runtime.withQueryValue("compatible") { DoubleValue(compatible) }
            return expression.resolveBoolean(input.runtime)
        }
    }
}