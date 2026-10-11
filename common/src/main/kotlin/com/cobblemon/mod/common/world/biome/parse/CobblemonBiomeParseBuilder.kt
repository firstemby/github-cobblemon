/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.biome.parse

import com.cobblemon.mod.common.world.biome.CobblemonBiome
import com.cobblemon.mod.common.world.biome.CobblemonBiomePlacement
import com.google.gson.JsonElement
import net.minecraft.world.level.biome.Biome

class CobblemonBiomeParseBuilder {
	var biome: Biome? = null
	val placements: MutableList<CobblemonBiomePlacement> = mutableListOf()
	val customData: MutableMap<String, JsonElement> = linkedMapOf()

	fun buildOrNull(): CobblemonBiome? {
		val parsedBiome = biome ?: return null
		return CobblemonBiome(parsedBiome, placements.toList(), customData.toMap())
	}
}
