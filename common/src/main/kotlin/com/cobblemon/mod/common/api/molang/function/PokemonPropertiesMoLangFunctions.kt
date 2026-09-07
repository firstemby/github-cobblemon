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

object PokemonPropertiesMoLangFunctions : AbstractMoLangFunctionHolder<PokemonProperties>() {
    override fun PokemonProperties.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val props = this
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["level"] = { DoubleValue(props.level?.toDouble() ?: 0) }
        functions["set_level"] = { params ->
            props.level = params.getIntOrNull(0)
            DoubleValue.ONE
        }
        functions["shiny"] = { DoubleValue(props.shiny) }
        functions["set_shiny"] = { params ->
            props.shiny = params.getBooleanOrNull(0)
            DoubleValue.ONE
        }
        functions["species"] = { props.species?.let { StringValue(it) } ?: DoubleValue.ZERO }
        functions["set_species"] = { params ->
            props.species = params.getStringOrNull(0)
            DoubleValue.ONE
        }
        functions["gender"] = { props.gender?.let { StringValue(it.name) } ?: DoubleValue.ZERO }
        functions["set_gender"] = { params ->
            props.gender = params.getStringOrNull(0)?.let { Gender.valueOf(it) }
            DoubleValue.ONE
        }
        functions["form"] = { props.form?.let { StringValue(it) } ?: DoubleValue.ZERO }
        functions["ivs"] = ivs@{
            val ivs = props.ivs ?: return@ivs DoubleValue.ZERO

            ivs.struct
        }
        functions["evs"] = evs@{
            val evs = props.evs ?: return@evs DoubleValue.ZERO

            evs.struct
        }
        functions["friendship"] = { DoubleValue(props.friendship?.toDouble() ?: DoubleValue.ZERO) }
        functions["set_friendship"] = { params ->
            props.friendship = params.getIntOrNull(0)
            DoubleValue.ONE
        }
        functions["create"] = { (props.create()).struct }
        functions["to_string"] = { params ->
            val separator = params.getStringOrNull(0) ?: " "
            StringValue(props.asString(separator = separator))
        }

        return functions
    }
}
