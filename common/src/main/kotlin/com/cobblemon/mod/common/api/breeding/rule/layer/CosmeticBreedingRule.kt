/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule.layer

import com.cobblemon.mod.common.api.breeding.rule.BreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.resources.ResourceLocation

interface CosmeticBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out CosmeticBreedingRule>>(
            cobblemonResource("expression") to ExpressionCosmeticBreedingRule::class.java
        )
    }

    fun applyCosmeticChanges(input: NatureBreedingRule.Input, offspring: Pokemon)

    class ExpressionCosmeticBreedingRule(
        val expression: ExpressionLike,
        override val order: Int = 0
    ) : CosmeticBreedingRule {
        override fun applyCosmeticChanges(input: NatureBreedingRule.Input, offspring: Pokemon) {
            input.runtime.withQueryValue("offspring") { offspring.struct }
            expression.resolve(input.runtime)
        }
    }
}
