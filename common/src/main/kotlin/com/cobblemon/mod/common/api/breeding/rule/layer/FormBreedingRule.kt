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
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.breeding.rule.BreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.resolveString
import com.cobblemon.mod.common.util.toProperties
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.resources.ResourceLocation

interface FormBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out FormBreedingRule>>(
            cobblemonResource("expression") to ExpressionFormBreedingRule::class.java,
        )
    }

    open class Input(
        runtime: MoLangRuntime,
        mother: Pokemon,
        father: Pokemon,
        val offspringSpecies: Species
    ) : SpeciesBreedingRule.Input(runtime, mother, father) {
        override fun addToStruct(struct: QueryStruct) {
            super.addToStruct(struct)
            struct.addFunction("offspring_species") { offspringSpecies.struct }
        }
    }

    fun getOffspringFormModifier(input: Input, modifier: PokemonProperties): PokemonProperties

    class ExpressionFormBreedingRule(
        val expression: ExpressionLike,
        override val order: Int = 0
    ) : FormBreedingRule {
        override fun getOffspringFormModifier(input: Input, modifier: PokemonProperties): PokemonProperties {
            input.runtime.withQueryValue("modifier") { StringValue(modifier.asString()) }
            return input.runtime.resolveString(expression).toProperties()
        }
    }
}