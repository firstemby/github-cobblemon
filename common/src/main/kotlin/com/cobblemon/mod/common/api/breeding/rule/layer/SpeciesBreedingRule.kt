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
import com.cobblemon.mod.common.api.breeding.rule.BreedingRuleInputs
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asStruct
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.breeding.rules.species.ExpressionSpeciesBreedingRule
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

/**
 * A breeding rule that decides what the offspring species will be.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface SpeciesBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out SpeciesBreedingRule>>(
            cobblemonResource("expression") to ExpressionSpeciesBreedingRule::class.java
        )
    }

    open class Input(
        override val runtime: MoLangRuntime,
        override val isSimulated: Boolean,
        val mother: Pokemon,
        val father: Pokemon,
    ): BreedingRuleInputs {
        override fun addToStruct(struct: QueryStruct) {
            super.addToStruct(struct)
            struct.addFunction("mother") { mother.asStruct() }
            struct.addFunction("father") { father.asStruct() }
        }
    }

    fun getOffspringSpecies(input: Input, offspringSpecies: Species?): Species?
}