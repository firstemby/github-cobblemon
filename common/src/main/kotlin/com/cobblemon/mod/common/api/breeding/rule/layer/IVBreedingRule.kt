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
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

interface IVBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf<ResourceLocation, Class<out IVBreedingRule>>(
            cobblemonResource("expression") to ExpressionIVBreedingRule::class.java
        )
    }

    fun getOffspringIVs(input: SpeciesBreedingRule.Input, ivs: IVs?): IVs?

    class ExpressionIVBreedingRule(
        val expression: ExpressionLike,
        override val order: Int = 0
    ) : IVBreedingRule {
        override fun getOffspringIVs(input: SpeciesBreedingRule.Input, ivs: IVs?): IVs? {
//            input.runtime.withQueryValue("ivs") { ivs?.toStruct() }
            return (expression.resolve(input.runtime) as? ObjectValue<IVs>)?.obj
        }
    }
}
