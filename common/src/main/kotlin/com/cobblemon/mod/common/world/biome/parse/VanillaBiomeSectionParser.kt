package com.cobblemon.mod.common.world.biome.parse

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
		val biome = Biome.DIRECT_CODEC
			.parse(ops, root)
			.result()
			.orElse(null)
			?: return false

		builder.biome = biome
		return true
	}
}

