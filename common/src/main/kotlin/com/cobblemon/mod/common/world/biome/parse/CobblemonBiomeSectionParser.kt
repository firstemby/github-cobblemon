package com.cobblemon.mod.common.world.biome.parse

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.resources.RegistryOps

interface CobblemonBiomeSectionParser {
	val id: String
	val required: Boolean
		get() = true

	fun parse(
		root: JsonObject,
		ops: RegistryOps<JsonElement>,
		builder: CobblemonBiomeParseBuilder
	): Boolean
}

