/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule.layer

import com.cobblemon.mod.common.api.breeding.rule.BreedingRule
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.resources.ResourceLocation

/**
 * Fulfils the role of:
 *
 * Nature Layer
 * Cosmetic layer?
 * Ability layer?
 */
interface PokemonBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out PokemonBreedingRule>>(
            cobblemonResource("expression") to ExpressionPokemonBreedingRule::class.java
        )
    }

    fun apply(input: NatureBreedingRule.Input, offspring: Pokemon)

    class ExpressionPokemonBreedingRule(
        val expression: com.cobblemon.mod.common.api.molang.ExpressionLike,
        override val order: Int = 0
    ) : PokemonBreedingRule {
        override fun apply(input: NatureBreedingRule.Input, offspring: Pokemon) {
            input.runtime.withQueryValue("offspring") { offspring.struct }
            expression.resolve(input.runtime)
        }
    }
}