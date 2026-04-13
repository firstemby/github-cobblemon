package com.cobblemon.mod.common.world.biome

import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate

// Add Comments
data class CobblemonBiome(
	val biome: Biome,
	val parameters: Climate.ParameterPoint
)