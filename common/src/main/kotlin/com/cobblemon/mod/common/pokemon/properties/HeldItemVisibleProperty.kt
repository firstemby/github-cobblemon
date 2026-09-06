/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.properties

import com.cobblemon.mod.common.api.properties.CustomPokemonPropertyType

object HeldItemVisibleProperty : CustomPokemonPropertyType<BooleanProperty> {
    override val keys = setOf("held_item_visible")
    override val needsKey = true

    override fun fromString(value: String?) =
        when {
            value == null || value.lowercase() in listOf("true", "yes") -> heldItemVisible(true)
            value.lowercase() in listOf("false", "no") -> heldItemVisible(false)
            else -> null
        }

    fun heldItemVisible(value: Boolean) = BooleanProperty(
        key = keys.first(),
        value = value,
        pokemonApplicator = { pokemon, _ -> pokemon.heldItemVisible = value },
        entityApplicator = { pokemonEntity, _ -> pokemonEntity.pokemon.heldItemVisible = value },
        pokemonMatcher = { pokemon, _ -> pokemon.heldItemVisible == value },
        entityMatcher = { pokemonEntity, _ -> pokemonEntity.pokemon.heldItemVisible == value }
    )

    override fun examples() = setOf("yes", "no")
}
