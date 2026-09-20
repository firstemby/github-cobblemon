/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.datafixer.fix

import com.cobblemon.mod.common.util.DataKeys
import com.mojang.datafixers.schemas.Schema
import com.mojang.serialization.Dynamic

class EvolutionProgressFix(output: Schema) : PokemonFix(output) {
    override fun fixPokemonData(dynamic: Dynamic<*>): Dynamic<*> {
        var baseDynamic = dynamic
        val evolutionsProgress = baseDynamic.get(DataKeys.POKEMON_EVOLUTIONS).get("progress").orElseEmptyList()
        baseDynamic = baseDynamic.set(DataKeys.POKEMON_PROGRESS, evolutionsProgress)
        val evolutionsPending = baseDynamic.get(DataKeys.POKEMON_EVOLUTIONS).get("pending").orElseEmptyList()
        baseDynamic = baseDynamic.set(DataKeys.POKEMON_EVOLUTIONS, evolutionsPending)

        // This is just from stuff from when the form_changes branch was still in progress so don't worry about this too much
        val formsPending = baseDynamic.get(DataKeys.POKEMON_FORMS).get("pending").orElseEmptyList()
        baseDynamic = baseDynamic.set(DataKeys.POKEMON_FORMS, formsPending)

        return baseDynamic
    }
}