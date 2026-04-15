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

