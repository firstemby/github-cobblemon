/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.biome.parse

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.resources.RegistryOps

class CustomDataSectionParser(
	private val reservedKeys: Set<String>
) : CobblemonBiomeSectionParser {
	override val id = "custom_data"
	override val required = false

	override fun parse(
		root: JsonObject,
		ops: RegistryOps<JsonElement>,
		builder: CobblemonBiomeParseBuilder
	): Boolean {
		for ((key, value) in root.entrySet()) {
			if (key !in reservedKeys) {
				builder.customData[key] = value.deepCopy()
			}
		}
		return true
	}
}

