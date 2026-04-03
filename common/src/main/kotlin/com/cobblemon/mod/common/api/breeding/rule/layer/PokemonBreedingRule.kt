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
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.breeding.rules.pokemon.ExpressionPokemonBreedingRule
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

/**
 * A breeding rule that can do arbitrary changes to the offspring. This is unlike [IVsBreedingRule]s
 * and [MovesBreedingRule] only in the sense that there's no data being passed from one rule to another
 * except for the offspring [Pokemon] itself.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface PokemonBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out PokemonBreedingRule>>(
            cobblemonResource("expression") to ExpressionPokemonBreedingRule::class.java
        )
    }

    open class Input(
        runtime: MoLangRuntime,
        isSimulated: Boolean,
        mother: Pokemon,
        father: Pokemon,
        val offspring: Pokemon
    ) : SpeciesBreedingRule.Input(runtime = runtime, isSimulated = isSimulated, mother = mother, father = father) {
        override fun addToStruct(struct: QueryStruct) {
            super.addToStruct(struct)
            struct.addFunction("offspring") { offspring.struct }
        }
    }

    fun apply(input: Input)
}