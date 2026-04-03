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
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.breeding.rules.mother.BannedEggGroupsBarrierBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.mother.CompatibleEggGroupBarrierBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.mother.ExpressionMotherBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.mother.NonDittoBreedingRule
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.cobblemonResource

/**
 * A breeding rule which takes two Pokémon and decides on which is a valid logical mother for the pairing.
 * These rules are also used as a proxy for deciding if the pairing is compatible in the first place.
 *
 * The result of the previous rule is passed into the next, so some rules may 'skip' and not make any
 * changes to the mother if the rule doesn't apply to the given inputs.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface MotherBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf(
            cobblemonResource("expression") to ExpressionMotherBreedingRule::class.java,
            cobblemonResource("compatible_egg_group_barrier") to CompatibleEggGroupBarrierBreedingRule::class.java,
            cobblemonResource("non_ditto") to NonDittoBreedingRule::class.java,
            cobblemonResource("banned_egg_groups_barrier") to BannedEggGroupsBarrierBreedingRule::class.java
        )
    }

    class Input(
        override val runtime: MoLangRuntime,
        override val isSimulated: Boolean,
        val parent1: Pokemon,
        val parent2: Pokemon,
    ) : BreedingRuleInputs {
        override fun addToStruct(struct: QueryStruct) {
            super.addToStruct(struct)
            struct.addFunction("parents") { listOf(parent1.struct, parent2.struct).asArrayValue() }
            struct.addFunction("parent_1") { parent1.struct }
            struct.addFunction("parent_2") { parent2.struct }
        }
    }

    fun getMother(input: Input, mother: Pokemon?): Pokemon?
}