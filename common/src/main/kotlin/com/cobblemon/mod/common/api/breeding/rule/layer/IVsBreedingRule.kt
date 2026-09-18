/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule.layer

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.breeding.rule.BreedingRule
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.breeding.rules.ivs.EmptyIVsBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.ivs.ExpressionIVsBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.ivs.InheritRandomIVsBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.ivs.SetInheritedIVCountBreedingRule
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.getIntOrNull

/**
 * A [BreedingRule] that affects IV inheritance. See [BreedingRule] for ordering.
 *
 * For Molang expressions:
 * - q.mother,
 * - q.father,
 * - q.offspring,
 * - q.ivs,
 * - q.inherited_count,
 * - q.inherited_stats,
 * - q.remaining_count,
 * - q.stat_types,
 * - q.inherit(parent, stat)
 * - q.set_iv(stat, value)
 * - q.reset()
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface IVsBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf(
            cobblemonResource("expression") to ExpressionIVsBreedingRule::class.java,
            cobblemonResource("set_inherited_iv_count") to SetInheritedIVCountBreedingRule::class.java,
            cobblemonResource("inherit_random_ivs") to InheritRandomIVsBreedingRule::class.java,
            cobblemonResource("empty") to EmptyIVsBreedingRule::class.java
        )
    }

    class IVInheritanceData(
        val ivs: IVs,
        var statsToInherit: Int = 3,
        var inheritedStats: MutableSet<Stat> = mutableSetOf()
    ) {
        fun inherit(pokemon: Pokemon, stat: Stat) {
            statsToInherit--
            inheritedStats.add(stat)
            ivs[stat] = pokemon.ivs[stat]!!
        }

        fun addToStruct(struct: QueryStruct) {
            struct.addFunction("ivs") { ivs.struct }
            struct.addFunction("inherited_count") { DoubleValue(inheritedStats.size.toDouble()) }
            struct.addFunction("inherited_stats") { inheritedStats.asArrayValue { StringValue(it.showdownId) } }
            struct.addFunction("remaining_count") { DoubleValue(statsToInherit.toDouble()) }
            struct.addFunction("stat_types") { Stats.PERMANENT.asArrayValue { StringValue(it.showdownId) } }
            struct.addFunction("inherit") { params ->
                val parent = params.get<ObjectValue<Pokemon>>(0)!!.obj
                val statType = params.get<StringValue>(1)!!.value.let { name ->
                    Stats.PERMANENT.first { it.showdownId == name || it.identifier.path == name }
                }
                inherit(parent, statType)
                DoubleValue.ONE
            }
            struct.addFunction("set_iv") { params ->
                val statType = params.get<StringValue>(0)!!.value.let { name ->
                    Stats.PERMANENT.first { it.showdownId == name || it.identifier.path == name }
                }
                val value = params.getIntOrNull(1) ?: 0
                ivs[statType] = value
                DoubleValue.ONE
            }
            struct.addFunction("reset") { params ->
                val minPerfectIVs = params.getIntOrNull(0) ?: 0
                ivs.copyFrom(IVs.createRandomIVs(minPerfectIVs = minPerfectIVs))
                inheritedStats.clear()
                DoubleValue.ONE
            }
        }
    }

    fun calculateOffspringIVs(input: PokemonBreedingRule.Input, data: IVInheritanceData)
}