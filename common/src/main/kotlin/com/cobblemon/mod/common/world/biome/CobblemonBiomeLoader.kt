package com.cobblemon.mod.common.world.biome

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.mojang.serialization.JsonOps
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import java.io.BufferedReader

object CobblemonBiomeLoader : JsonDataRegistry<CobblemonBiome> {
	override val gson: Gson = GsonBuilder()
		.setPrettyPrinting()
		.create()
	override val typeToken: TypeToken<CobblemonBiome> = TypeToken.get(CobblemonBiome::class.java)
	override val resourcePath: String = "biomes"
	override val id: ResourceLocation = cobblemonResource(resourcePath)
	override val type = PackType.SERVER_DATA
	override val observable = SimpleObservable<CobblemonBiomeLoader>()
	private const val PARAMETERS_KEY = "parameters"

	@JvmStatic
	fun load(
		manager: ResourceManager,
		registryInfoLookup: RegistryOps.RegistryInfoLookup
	): Map<ResourceLocation, CobblemonBiome> {
		val ops = RegistryOps.create(JsonOps.INSTANCE, registryInfoLookup)
		val parsedBiomes = loadJsonData(manager) { reader, sourceIdentifier, resolvedIdentifier ->
			parseCobblemonBiomeWithCodecs(reader, sourceIdentifier, resolvedIdentifier, ops)
		}

		reload(parsedBiomes)
		observable.emit(this)
		Cobblemon.LOGGER.info("Loaded {} cobblemon biomes.", parsedBiomes.size)
		return parsedBiomes
	}

	override fun reload(manager: ResourceManager) {
		observable.emit(this)
	}

	override fun reload(data: Map<ResourceLocation, CobblemonBiome>) {
	}

	override fun sync(player: ServerPlayer) {}

	private fun parseCobblemonBiomeWithCodecs(
		reader: BufferedReader,
		sourceIdentifier: ResourceLocation,
		resolvedIdentifier: ResourceLocation,
		ops: RegistryOps<JsonElement>
	): CobblemonBiome? {
		val json = JsonParser.parseReader(reader)
		val biome = Biome.DIRECT_CODEC.parse(ops, json)
			.resultOrPartial { error ->
				Cobblemon.LOGGER.error(
					"Failed to parse biome JSON {} ({}) with vanilla codec: {}",
					sourceIdentifier,
					resolvedIdentifier,
					error
				)
			}
			.orElse(null) ?: return null

		if (!json.isJsonObject) {
			Cobblemon.LOGGER.error(
				"Failed to parse biome JSON {} ({}): root must be an object to decode '{}'",
				sourceIdentifier,
				resolvedIdentifier,
				PARAMETERS_KEY
			)
			return null
		}

		val parametersElement = json.asJsonObject.get(PARAMETERS_KEY)
		if (parametersElement == null) {
			Cobblemon.LOGGER.error(
				"Failed to parse biome JSON {} ({}): missing '{}' object for climate parameters",
				sourceIdentifier,
				resolvedIdentifier,
				PARAMETERS_KEY
			)
			return null
		}

		val parameters = Climate.ParameterPoint.CODEC.parse(JsonOps.INSTANCE, parametersElement)
			.resultOrPartial { error ->
				Cobblemon.LOGGER.error(
					"Failed to parse climate parameters in {} ({}): {}",
					sourceIdentifier,
					resolvedIdentifier,
					error
				)
			}
			.orElse(null) ?: return null

		return CobblemonBiome(
			biome = biome,
			parameters = parameters
		)
	}
}