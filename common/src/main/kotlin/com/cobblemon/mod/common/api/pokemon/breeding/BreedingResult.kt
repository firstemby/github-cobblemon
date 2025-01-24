/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.breeding

import com.cobblemon.mod.common.pokemon.Pokemon

data class BreedingResult(
    val pokemon: EggPokemon? = null,
    val timeToHatch: Int = 20000,
    val successful: Boolean = pokemon != null
)
