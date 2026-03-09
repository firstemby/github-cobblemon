/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.util.getBooleanOrNull
import com.cobblemon.mod.common.util.getIntOrNull
import com.cobblemon.mod.common.util.getStringOrNull

object PokemonPropertiesMoLangFunctions {
    val holder: MutableList<(PokemonProperties) -> HashMap<String, (MoParams) -> Any>> =
    mutableListOf(
    { props ->
        val map = hashMapOf<String, (MoParams) -> Any>()

        map.put("level") { DoubleValue(props.level?.toDouble() ?: 0) }
        map.put("set_level") { params ->
            props.level = params.getIntOrNull(0)
            return@put DoubleValue.ONE
        }
        map.put("shiny") { DoubleValue(props.shiny) }
        map.put("set_shiny") { params ->
            props.shiny = params.getBooleanOrNull(0)
            return@put DoubleValue.ONE
        }
        map.put("species") { props.species?.let { StringValue(it) } ?: DoubleValue.ZERO }
        map.put("set_species") { params ->
            props.species = params.getStringOrNull(0)
            return@put DoubleValue.ONE
        }
        map.put("gender") { props.gender?.let { StringValue(it.name) } ?: DoubleValue.ZERO }
        map.put("set_gender") { params ->
            props.gender = params.getStringOrNull(0)?.let { Gender.valueOf(it) }
            return@put DoubleValue.ONE
        }
        map.put("form") { props.form?.let { StringValue(it) } ?: DoubleValue.ZERO }
        map.put("ivs") {
            val ivs = props.ivs
            if (ivs == null) {
                return@put DoubleValue.ZERO
            } else {
                return@put ivs.struct
            }
        }
        map.put("evs") {
            val evs = props.evs
            if (evs == null) {
                return@put DoubleValue.ZERO
            } else {
                return@put evs.struct
            }
        }
        map.put("friendship") { DoubleValue(props.friendship?.toDouble() ?: DoubleValue.ZERO) }
        map.put("set_friendship") { params ->
            props.friendship = params.getIntOrNull(0)
            return@put DoubleValue.ONE
        }
        map.put("create") { params ->
            val pokemon = props.create()
            return@put pokemon.struct
        }
        map.put("to_string") { params ->
            val separator = params.getStringOrNull(0) ?: " "
            return@put StringValue(props.asString(separator = separator))
        }
        map
    }
    )
}
