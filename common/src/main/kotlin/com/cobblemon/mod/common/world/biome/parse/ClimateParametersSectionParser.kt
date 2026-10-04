/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.biome.parse

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.world.biome.CobblemonBiomePlacement
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.biome.Climate

object ClimateParametersSectionParser : CobblemonBiomeSectionParser {
	override val id = "parameters"

	override val required = false

	private const val PRESET_KEY = "preset"
	private val DEFAULT_PRESET = ResourceLocation.withDefaultNamespace("overworld")

	override fun parse(
		root: JsonObject,
		ops: RegistryOps<JsonElement>,
		builder: CobblemonBiomeParseBuilder
	): Boolean {
		val element = root.get("parameters") ?: return true

		val entries: List<JsonElement> = when {
			element.isJsonArray -> element.asJsonArray.toList()
			element.isJsonObject -> listOf(element)
			else -> {
				Cobblemon.LOGGER.error("[BIOME] 'parameters' must be an object or an array of objects")
				return false
			}
		}

		var allValid = true
		for (entry in entries) {
			if (!entry.isJsonObject) {
				Cobblemon.LOGGER.error("[BIOME] Each 'parameters' entry must be an object")
				allValid = false
				continue
			}

			val copy = entry.asJsonObject.deepCopy()

			val presetElement = copy.remove(PRESET_KEY)
			val preset = if (presetElement == null) {
				DEFAULT_PRESET
			} else {
				ResourceLocation.tryParse(presetElement.asString) ?: run {
					Cobblemon.LOGGER.error("[BIOME] Invalid preset '{}' in climate parameters", presetElement.asString)
					allValid = false
					null
				}
			} ?: continue

			val result = Climate.ParameterPoint.CODEC.parse(JsonOps.INSTANCE, copy)
			val point = result.result().orElse(null)
			if (point == null) {
				val errorMessage = result.error().map { it.message() }.orElse("unknown error")
				Cobblemon.LOGGER.error("[BIOME] Failed to parse climate parameters: {}", errorMessage)
				allValid = false
				continue
			}

			builder.placements.add(CobblemonBiomePlacement(preset, point))
		}

		return allValid
	}
}
