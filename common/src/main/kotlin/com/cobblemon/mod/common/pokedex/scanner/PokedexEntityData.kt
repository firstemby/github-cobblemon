/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.scanner

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asArrayValue
import java.util.UUID

data class PokedexEntityData(
    val species: Species,
    val form: FormData,
    val gender: Gender,
    val aspects: Set<String>,
    val shiny: Boolean,
    val level: Int,
    val ownerUUID: UUID?
) {
    val struct = QueryStruct(hashMapOf())
        .addFunction("species") { species.struct }
        .addFunction("gender") { StringValue(gender.name.lowercase()) }
        .addFunction("aspects") { aspects.asArrayValue(::StringValue) }
        .addFunction("shiny") { DoubleValue(shiny) }
        .addFunction("level") { DoubleValue(level.toDouble()) }
        .addFunction("has_owner") { DoubleValue(ownerUUID != null) }
}