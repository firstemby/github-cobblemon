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
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.item.components.EggComponent
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.breeding.rules.egg.EmptyEggBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.egg.ExpressionEggBreedingRule
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.server
import net.minecraft.world.item.ItemStack

/**
 * A [BreedingRule] that can adjust the hatch time for an egg.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface EggBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf(
            cobblemonResource("expression") to ExpressionEggBreedingRule::class.java,
            cobblemonResource("empty") to EmptyEggBreedingRule::class.java
        )
    }

    class Input(
        override val runtime: MoLangRuntime,
        override val isSimulated: Boolean,
        val parent1: Pokemon?,
        val parent2: Pokemon?,
        val offspring: Pokemon,
        val egg: EggComponent,
        val stack: ItemStack
    ): BreedingRuleInputs {
        override fun addToStruct(struct: QueryStruct) {
            super.addToStruct(struct)
            struct.addFunction("parent_1") { parent1?.struct ?: DoubleValue.ZERO }
            struct.addFunction("parent_2") { parent2?.struct ?: DoubleValue.ZERO }
            struct.addFunction("offspring") { offspring.struct }
            struct.addFunction("egg") { egg.struct }
            struct.addFunction("stack") { stack.asMoLangValue(server()!!.registryAccess()) }
        }
    }

    fun apply(input: Input)
}