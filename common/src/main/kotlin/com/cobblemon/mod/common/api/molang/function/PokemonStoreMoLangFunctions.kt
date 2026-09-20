/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asStruct
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.storage.PokemonStore
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.asUUID
import com.cobblemon.mod.common.util.getPlayer
import com.cobblemon.mod.common.util.toProperties

object PokemonStoreMoLangFunctions : AbstractMoLangFunctionHolder<PokemonStore<*>>() {
    override fun PokemonStore<*>.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val store = this
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["uuid"] = { StringValue(store.uuid.toString()) }
        functions["add"] = { params ->
            val pokemon = params.get<ObjectValue<Pokemon>>(0)
            DoubleValue(store.add(pokemon.obj))
        }
        functions["add_by_properties"] = { params ->
            val props = params.getString(0).toProperties()
            val player =
                (store as? PlayerPartyStore)?.playerUUID?.getPlayer() // Only really know for sure when it's a player party store
            val pokemon = props.create(player = player)
            DoubleValue(store.add(pokemon))
        }
        functions["find_by_properties"] = { params ->
            val props = params.getString(0).toProperties()
            val pokemon = store.find { props.matches(it) }
            pokemon?.asStruct() ?: DoubleValue.ZERO
        }
        functions["find_all_by_properties"] = { params ->
            val props = params.getString(0).toProperties()
            val pokemon = store.filter { props.matches(it) }
            ArrayStruct(pokemon.mapIndexed { index, value -> "$index" to value.asStruct() }.toMap())
        }
        functions["find_by_id"] = { params ->
            val id = params.getString(0).asUUID
            val pokemon = store.find { it.uuid == id }
            pokemon?.asStruct() ?: DoubleValue.ZERO
        }
        functions["remove_by_id"] = removeById@{ params ->
            val id = params.getString(0).asUUID
            val pokemon = store.find { it.uuid == id } ?: return@removeById DoubleValue.ZERO
            DoubleValue(store.remove(pokemon))
        }
        functions["average_level"] = averageLevel@{
            var numberOfPokemon = 0
            var totalLevel = 0
            for (pokemon in store) {
                totalLevel += pokemon.level
                numberOfPokemon++
            }

            if (numberOfPokemon == 0) return@averageLevel DoubleValue.ZERO

            DoubleValue(totalLevel.toDouble() / numberOfPokemon)
        }
        functions["count"] = { DoubleValue(store.count()) }
        functions["count_by_properties"] = { params ->
            val props = params.getString(0).toProperties()
            DoubleValue(store.count { props.matches(it) })
        }
        functions["highest_level"] = {
            val highest = store.maxOfOrNull { it.level } ?: 0
            DoubleValue(highest)
        }
        functions["lowest_level"] = {
            val lowest = store.minOfOrNull { it.level } ?: 0
            DoubleValue(lowest)
        }
        functions["heal"] = {
            for (pokemon in store) {
                pokemon.heal()
            }
            DoubleValue.ONE
        }
        functions["healing_remainder_percent"] = {
            var totalPercent = 0.0f
            for (pokemon in store) {
                totalPercent += (1.0f - (pokemon.currentHealth.toFloat() / pokemon.maxHealth))
            }
            DoubleValue(totalPercent)
        }
        functions["has_usable_pokemon"] = { DoubleValue(store.any { !it.isFainted() }) }
        functions["pokemon"] = { store.map { it.asStruct() }.asArrayValue() }

        return functions
    }
}
