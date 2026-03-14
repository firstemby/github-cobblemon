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
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.breeding.rule.BreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Nature
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.resources.ResourceLocation

interface NatureBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out NatureBreedingRule>>(
            cobblemonResource("expression") to ExpressionNatureBreedingRule::class.java
        )
    }

    open class Input(
        runtime: MoLangRuntime,
        mother: Pokemon,
        father: Pokemon,
        offspringSpecies: Species,
        val formModifier: PokemonProperties
    ): FormBreedingRule.Input(runtime = runtime, mother = mother, father = father, offspringSpecies = offspringSpecies) {
        override fun addToStruct(struct: QueryStruct) {
            super.addToStruct(struct)
            struct.addFunction("form_modifier") { StringValue(formModifier.asString()) }
        }
    }

    fun getOffspringNature(input: SpeciesBreedingRule.Input, nature: Nature?): Nature?

    class ExpressionNatureBreedingRule(
        val expression: ExpressionLike,
        override val order: Int = 0
    ) : NatureBreedingRule {
        override fun getOffspringNature(input: SpeciesBreedingRule.Input, nature: Nature?): Nature? {
            input.runtime.withQueryValue("nature") { nature?.let { StringValue(it.name.toString()) } ?: DoubleValue.ZERO }
            return (expression.resolve(input.runtime) as? ObjectValue<Nature>)?.obj
        }
    }
}
