/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.biome.parse

import com.cobblemon.mod.common.Cobblemon
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.minecraft.resources.RegistryOps
import net.minecraft.world.level.biome.Climate

object ClimateParametersSectionParser : CobblemonBiomeSectionParser {
	override val id = "parameters"

	override fun parse(
		root: JsonObject,
		ops: RegistryOps<JsonElement>,
		builder: CobblemonBiomeParseBuilder
	): Boolean {
		val params = root.getAsJsonObject("parameters")
		if (params == null) {
			Cobblemon.LOGGER.error("MEALAM Missing 'parameters' section in biome JSON")
			return false
		}

		val result = Climate.ParameterPoint.CODEC.parse(JsonOps.INSTANCE, params)
		val parameterPoint = result.result().orElse(null)
		if (parameterPoint == null) {
			val errorMessage = result.error().map { it.message() }.orElse("unknown error")
			Cobblemon.LOGGER.error("MEALAM Failed to parse climate parameters: {}", errorMessage)
			return false
		}

		builder.parameters = parameterPoint
		return true
	}
}