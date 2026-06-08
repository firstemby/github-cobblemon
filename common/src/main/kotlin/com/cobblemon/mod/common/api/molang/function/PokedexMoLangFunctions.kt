/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager
import com.cobblemon.mod.common.api.pokedex.CaughtCount
import com.cobblemon.mod.common.api.pokedex.CaughtPercent
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.PokedexManager
import com.cobblemon.mod.common.api.pokedex.SeenCount
import com.cobblemon.mod.common.api.pokedex.SeenPercent
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.getStringOrNull

object PokedexMoLangFunctions : AbstractMoLangFunctionHolder<AbstractPokedexManager>() {
    override fun AbstractPokedexManager.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val pokedex = this
        val map = mutableMapOf<String, (MoParams) -> Any>()
        map["get_species_record"] = { params ->
            val speciesId = params.getString(0).asIdentifierDefaultingNamespace()
            pokedex.speciesRecords[speciesId]?.struct ?: QueryStruct(hashMapOf())
        }

        map["has_seen"] = put@{ params ->
            val speciesId = params.getString(0).asIdentifierDefaultingNamespace()
            val formName = params.getStringOrNull(1)

            if (formName == null) {
                return@put DoubleValue(pokedex.getHighestKnowledgeForSpecies(speciesId).ordinal >= PokedexEntryProgress.SEEN.ordinal)
            } else {
                return@put DoubleValue(
                    (pokedex.getSpeciesRecord(speciesId)?.getFormRecord(formName)?.knowledge?.ordinal
                        ?: 0) >= PokedexEntryProgress.SEEN.ordinal
                )
            }
        }

        map["has_caught"] = put@{ params ->
            val speciesId = params.getString(0).asIdentifierDefaultingNamespace()
            val formName = params.getStringOrNull(1)
            if (formName == null) {
                return@put DoubleValue(pokedex.getHighestKnowledgeForSpecies(speciesId) == PokedexEntryProgress.OWNED)
            } else {
                return@put DoubleValue(
                    pokedex.getSpeciesRecord(speciesId)
                        ?.getFormRecord(formName)?.knowledge == PokedexEntryProgress.OWNED
                )
            }
        }
        map["caught_count"] = { DoubleValue(pokedex.getGlobalCalculatedValue(CaughtCount)) }
        map["seen_count"] = { DoubleValue(pokedex.getGlobalCalculatedValue(SeenCount)) }
        map["caught_percent"] = { DoubleValue(pokedex.getGlobalCalculatedValue(CaughtPercent)) }
        map["seen_percent"] = { DoubleValue(pokedex.getGlobalCalculatedValue(SeenPercent)) }

        if (pokedex is PokedexManager) {
            map["player_id"] = { StringValue(pokedex.uuid.toString()) }
            map["see"] = put@{ params ->
                val pokemon = params.get<ObjectValue<Pokemon>>(0).obj
                pokedex.encounter(pokemon)
                return@put DoubleValue.ONE
            }
            map["catch"] = put@{ params ->
                val pokemon = params.get<ObjectValue<Pokemon>>(0).obj
                pokedex.obtain(pokemon)
                return@put DoubleValue.ONE
            }
        }

        return map
    }
}
