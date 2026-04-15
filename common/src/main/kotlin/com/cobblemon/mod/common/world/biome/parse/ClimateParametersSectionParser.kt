package com.cobblemon.mod.common.world.biome.parse

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
		val params = root.getAsJsonObject("parameters") ?: return false
		val parameterPoint = Climate.ParameterPoint.CODEC
			.parse(JsonOps.INSTANCE, params)
			.result()
			.orElse(null)
			?: return false

		builder.parameters = parameterPoint
		return true
	}
}

