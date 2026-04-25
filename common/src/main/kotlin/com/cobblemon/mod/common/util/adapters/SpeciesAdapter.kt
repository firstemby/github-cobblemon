/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * Deserializes a [Species] from a string property that is the identifier of the species.
 *
 * @author Hiroku
 * @since February 13th, 2026
 */
object SpeciesAdapter : JsonSerializer<Species>, JsonDeserializer<Species> {
    override fun serialize(src: Species, t: Type, ctx: JsonSerializationContext) = JsonPrimitive(src.resourceIdentifier.toString())
    override fun deserialize(json: JsonElement, t: Type, ctx: JsonDeserializationContext): Species {
        val speciesId = json.asString.asIdentifierDefaultingNamespace()
        return PokemonSpecies.getByIdentifier(speciesId) ?: throw IllegalArgumentException("No such species: $speciesId")
    }
}