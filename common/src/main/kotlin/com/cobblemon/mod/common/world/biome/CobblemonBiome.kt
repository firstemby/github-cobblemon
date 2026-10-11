/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.biome

import com.google.gson.JsonElement
import net.minecraft.world.level.biome.Biome

data class CobblemonBiome(
	val biome: Biome,
	val placements: List<CobblemonBiomePlacement> = emptyList(),
	val customData: Map<String, JsonElement> = emptyMap()
)
