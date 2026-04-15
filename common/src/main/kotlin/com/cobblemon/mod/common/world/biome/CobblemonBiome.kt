package com.cobblemon.mod.common.world.biome

import com.google.gson.JsonElement
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate

data class CobblemonBiome(
	val biome: Biome,
	val parameters: Climate.ParameterPoint,
	val customData: Map<String, JsonElement> = emptyMap()
)