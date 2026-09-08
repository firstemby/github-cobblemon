package com.cobblemon.mod.common.world.biome.parse

import com.cobblemon.mod.common.Cobblemon
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.resources.RegistryOps
import net.minecraft.world.level.biome.Biome

object VanillaBiomeSectionParser : CobblemonBiomeSectionParser {
	override val id = "vanilla_biome"

	override fun parse(
		root: JsonObject,
		ops: RegistryOps<JsonElement>,
		builder: CobblemonBiomeParseBuilder
	): Boolean {
		val result = Biome.DIRECT_CODEC.parse(ops, root)
		val biome = result.result().orElse(null)
		if (biome == null) {
			val errorMessage = result.error().map { it.message() }.orElse("unknown error")
			Cobblemon.LOGGER.error("MEALAM Failed to parse vanilla biome: {}", errorMessage)
			return false
		}

		builder.biome = biome
		return true
	}
}