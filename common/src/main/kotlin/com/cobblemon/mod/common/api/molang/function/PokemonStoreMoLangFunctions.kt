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

object PokemonStoreMoLangFunctions: AbstractMoLangFunctionHolder<PokemonStore<*>> {
    override val holder: MutableList<(PokemonStore<*>) -> MutableMap<String, (MoParams) -> Any>> = mutableListOf(
        { store ->
            val map = hashMapOf<String, (MoParams) -> Any>()
            map["uuid"] = { StringValue(store.uuid.toString()) }
            map["add"] = put@{ params ->
                val pokemon = params.get<ObjectValue<Pokemon>>(0)
                return@put DoubleValue(store.add(pokemon.obj))
            }
            map["add_by_properties"] = put@{ params ->
                val props = params.getString(0).toProperties()
                val player = (store as? PlayerPartyStore)?.playerUUID?.getPlayer() // Only really know for sure when it's a player party store
                val pokemon = props.create(player = player)
                return@put DoubleValue(store.add(pokemon))
            }
            map["find_by_properties"] = put@{ params ->
                val props = params.getString(0).toProperties()
                val pokemon = store.find { props.matches(it) }
                return@put pokemon?.asStruct() ?: DoubleValue.ZERO
            }
            map["find_all_by_properties"] = put@{ params ->
                val props = params.getString(0).toProperties()
                val pokemon = store.filter { props.matches(it) }
                return@put ArrayStruct(pokemon.mapIndexed { index, value -> "$index" to value.asStruct() }.toMap())
            }
            map["find_by_id"] = put@{ params ->
                val id = params.getString(0).asUUID
                val pokemon = store.find { it.uuid == id }
                return@put pokemon?.asStruct() ?: DoubleValue.ZERO
            }
            map["remove_by_id"] = put@{ params ->
                val id = params.getString(0).asUUID
                val pokemon = store.find { it.uuid == id } ?: return@put DoubleValue.ZERO
                return@put DoubleValue(store.remove(pokemon))
            }
            map["average_level"] = put@{ _ ->
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
            map["count"] = { _ -> DoubleValue(store.count()) }
            map["count_by_properties"] = put@{ params ->
                val props = params.getString(0).toProperties()
                return@put DoubleValue(store.count { props.matches(it) })
            }
            map["highest_level"] = put@{
                val highest = store.maxOfOrNull { it.level } ?: 0
                return@put DoubleValue(highest)
            }
            map["lowest_level"] = put@{
                val lowest = store.minOfOrNull { it.level } ?: 0
                return@put DoubleValue(lowest)
            }
            map["heal"] = put@{
                for (pokemon in store) {
                    pokemon.heal()
                }
                return@put DoubleValue.ONE
            }
            map["healing_remainder_percent"] = { _ ->
                var totalPercent = 0.0f
                for (pokemon in store) {
                    totalPercent += (1.0f - (pokemon.currentHealth.toFloat() / pokemon.maxHealth))
                }
                DoubleValue(totalPercent)
            }
            map["has_usable_pokemon"] = { _ -> DoubleValue(store.any { !it.isFainted() }) }
            map["pokemon"] = put@{
                return@put store.map { it.asStruct() }.asArrayValue()
            }
            map
        }
    )
}
