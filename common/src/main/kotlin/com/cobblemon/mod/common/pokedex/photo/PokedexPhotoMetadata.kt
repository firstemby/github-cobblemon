/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.photo

import java.util.UUID

data class PokedexPhotoMetadata(
    val id: String,
    val owner: UUID,
    val index: Long,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val dataSize: Int,
    val detectedSpecies: List<String> = emptyList(),
    val biome: String = "minecraft:unknown",
    val dimension: String = "minecraft:overworld",
    val timeOfDayTicks: Long = 0L
)

