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
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.pokemon.Pokemon

object PartyMoLangFunctions: AbstractMoLangFunctionHolder<PartyStore> {
    override val holder = mutableListOf<(PartyStore) -> MutableMap<String, (MoParams) -> Any>>(
        { party ->
            val map = hashMapOf<String, (MoParams) -> Any>()
            map["get_pokemon"] = put@{ params ->
                val index = params.getInt(0)
                val pokemon = party.get(index) ?: return@put DoubleValue.ZERO
                return@put pokemon.struct
            }
            map["set_pokemon"] = put@{ params ->
                val index = params.getInt(0)
                val pokemon = params.get<ObjectValue<Pokemon>>(1).obj
                party.set(index, pokemon)
                return@put DoubleValue.ONE
            }
            return@mutableListOf map
        })
}
