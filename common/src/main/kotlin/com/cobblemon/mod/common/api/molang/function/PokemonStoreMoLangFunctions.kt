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
import java.util.HashMap
import java.util.function.Function

object PokemonStoreMoLangFunctions {
    val holder = mutableListOf<(PokemonStore<*>) -> HashMap<String, (MoParams) -> Any>>(
        { store ->
            val map = hashMapOf<String, (MoParams) -> Any>()
            map.put("uuid") { StringValue(store.uuid.toString()) }
            map.put("add") { params ->
                val pokemon = params.get<ObjectValue<Pokemon>>(0)
                return@put DoubleValue(store.add(pokemon.obj))
            }
            map.put("add_by_properties") { params ->
                val props = params.getString(0).toProperties()
                val player = (store as? PlayerPartyStore)?.playerUUID?.getPlayer() // Only really know for sure when it's a player party store
                val pokemon = props.create(player = player)
                return@put DoubleValue(store.add(pokemon))
            }
            map.put("find_by_properties") { params ->
                val props = params.getString(0).toProperties()
                val pokemon = store.find { props.matches(it) }
                return@put pokemon?.asStruct() ?: DoubleValue.ZERO
            }
            map.put("find_all_by_properties") { params ->
                val props = params.getString(0).toProperties()
                val pokemon = store.filter { props.matches(it) }
                return@put ArrayStruct(pokemon.mapIndexed { index, value -> "$index" to value.asStruct() }.toMap())
            }
            map.put("find_by_id") { params ->
                val id = params.getString(0).asUUID
                val pokemon = store.find { it.uuid == id }
                return@put pokemon?.asStruct() ?: DoubleValue.ZERO
            }
            map.put("remove_by_id") { params ->
                val id = params.getString(0).asUUID
                val pokemon = store.find { it.uuid == id } ?: return@put DoubleValue.ZERO
                return@put DoubleValue(store.remove(pokemon))
            }
            map.put("average_level") { _ ->
                var numberOfPokemon = 0
                var totalLevel = 0
                for (pokemon in store) {
                    totalLevel += pokemon.level
                    numberOfPokemon++
                }
                if (numberOfPokemon == 0) {
                    return@put DoubleValue.ZERO
                }
                return@put DoubleValue(totalLevel.toDouble() / numberOfPokemon)
            }
            map.put("count") { _ -> DoubleValue(store.count()) }
            map.put("count_by_properties") { params ->
                val props = params.getString(0).toProperties()
                return@put DoubleValue(store.count { props.matches(it) })
            }
            map.put("highest_level") {
                val highest = store.maxOfOrNull { it.level } ?: 0
                return@put DoubleValue(highest)
            }
            map.put("lowest_level") {
                val lowest = store.minOfOrNull { it.level } ?: 0
                return@put DoubleValue(lowest)
            }
            map.put("heal") {
                for (pokemon in store) {
                    pokemon.heal()
                }
                return@put DoubleValue.ONE
            }
            map.put("healing_remainder_percent") { _ ->
                var totalPercent = 0.0f
                for (pokemon in store) {
                    totalPercent += (1.0f - (pokemon.currentHealth.toFloat() / pokemon.maxHealth))
                }
                DoubleValue(totalPercent)
            }
            map.put("has_usable_pokemon") { _ -> DoubleValue(store.any { !it.isFainted() }) }
            map.put("pokemon") {
                return@put store.map { it.asStruct() }.asArrayValue()
            }
            map
        }
    )
}
