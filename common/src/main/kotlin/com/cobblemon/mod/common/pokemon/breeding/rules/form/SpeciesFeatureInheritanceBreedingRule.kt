/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.form

import com.cobblemon.mod.common.api.breeding.rule.layer.FormBreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolveObject
import com.cobblemon.mod.common.util.toProperties

class SpeciesFeatureInheritanceBreedingRule : FormBreedingRule {
    override val order: Int = 0
    val feature = ""
    val conditionAspect = ""
    val modifier = ""
    val parent: ExpressionLike = "q.mother".asExpressionLike()

    override fun getOffspringFormModifier(
        input: FormBreedingRule.Input,
        modifier: PokemonProperties
    ): PokemonProperties {
        val parent = input.runtime.resolveObject(parent).obj as Pokemon
        val childFeatures = SpeciesFeatures.getFeaturesFor(input.offspringSpecies)
        return if (feature.isNotEmpty() && SpeciesFeatures.getFeature(feature) !in childFeatures) {
            modifier // Don't change the modifier, the child species hasn't got the feature.
        } else if (conditionAspect.isNotEmpty() && conditionAspect !in parent.aspects) {
            modifier // Parent doesn't have it, don't inherit shit
        } else {
            "${modifier.asString()} ${this.modifier}".toProperties()
        }
    }
}