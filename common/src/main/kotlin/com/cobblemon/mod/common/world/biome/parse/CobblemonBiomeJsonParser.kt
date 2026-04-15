package com.cobblemon.mod.common.world.biome.parse

import com.cobblemon.mod.common.util.asJsonObjectOrNull
import com.cobblemon.mod.common.world.biome.CobblemonBiome
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.resources.RegistryOps
import java.io.Reader

object CobblemonBiomeJsonParser {
	private val sectionParsers = mutableListOf(
		VanillaBiomeSectionParser,
		ClimateParametersSectionParser,
		CustomDataSectionParser(setOf("parameters"))
	)

	fun parse(reader: Reader, ops: RegistryOps<JsonElement>): CobblemonBiome? {
		val root = JsonParser.parseReader(reader).asJsonObjectOrNull() ?: return null
		val builder = CobblemonBiomeParseBuilder()

		for (parser in getParsers()) {
			if (!parser.parse(root, ops, builder) && parser.required) {
				return null
			}
		}

		return builder.buildOrNull()
	}

	fun register(parser: CobblemonBiomeSectionParser) {
		sectionParsers.add(parser)
	}

	private fun getParsers(): List<CobblemonBiomeSectionParser> = sectionParsers.toList()
}


