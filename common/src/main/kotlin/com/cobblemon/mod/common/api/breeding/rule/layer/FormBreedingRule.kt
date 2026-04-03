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
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.breeding.rules.form.EmptyFormBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.form.ExpressionFormBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.form.SpeciesFeatureInheritanceBreedingRule
import com.cobblemon.mod.common.util.cobblemonResource

/**
 * A [BreedingRule] that modifies the form of an offspring by creating a [PokemonProperties] modifier
 * that will be applied to the offspring.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface FormBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf(
            cobblemonResource("expression") to ExpressionFormBreedingRule::class.java,
            cobblemonResource("species_feature_inheritance") to SpeciesFeatureInheritanceBreedingRule::class.java,
            cobblemonResource("empty") to EmptyFormBreedingRule::class.java
        )
    }

    open class Input(
        runtime: MoLangRuntime,
        isSimulated: Boolean,
        mother: Pokemon,
        father: Pokemon,
        val offspringSpecies: Species
    ) : SpeciesBreedingRule.Input(runtime, isSimulated, mother, father) {
        override fun addToStruct(struct: QueryStruct) {
            super.addToStruct(struct)
            struct.addFunction("offspring_species") { offspringSpecies.struct }
        }
    }

    fun getOffspringFormModifier(input: Input, modifier: PokemonProperties): PokemonProperties
}