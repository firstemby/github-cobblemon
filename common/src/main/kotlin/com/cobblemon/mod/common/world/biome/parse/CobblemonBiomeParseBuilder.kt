package com.cobblemon.mod.common.world.biome.parse

import com.cobblemon.mod.common.world.biome.CobblemonBiome
import com.google.gson.JsonElement
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate

class CobblemonBiomeParseBuilder {
	var biome: Biome? = null
	var parameters: Climate.ParameterPoint? = null
	val customData: MutableMap<String, JsonElement> = linkedMapOf()

	fun buildOrNull(): CobblemonBiome? {
		val parsedBiome = biome ?: return null
		val parsedParameters = parameters ?: return null
		return CobblemonBiome(parsedBiome, parsedParameters, customData.toMap())
	}
}

