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

// TODO: Cleanup + Add Comments
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
	private const val TEMPERATURE_KEY = "temperature"
	private const val MIN_KEY = "min"
	private const val MAX_KEY = "max"

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

		val biomeTemperature = extractBiomeTemperatureFromParameters(
			parametersElement = parametersElement,
			sourceIdentifier = sourceIdentifier,
			resolvedIdentifier = resolvedIdentifier
		) ?: return null

		val biomeJsonForCodec = json.asJsonObject.deepCopy().apply {
			addProperty(TEMPERATURE_KEY, biomeTemperature)
		}

		val biome = Biome.DIRECT_CODEC.parse(ops, biomeJsonForCodec)
			.resultOrPartial { error ->
				Cobblemon.LOGGER.error(
					"Failed to parse biome JSON {} ({}) with vanilla codec: {}",
					sourceIdentifier,
					resolvedIdentifier,
					error
				)
			}
			.orElse(null) ?: return null

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

	private fun extractBiomeTemperatureFromParameters(
		parametersElement: JsonElement,
		sourceIdentifier: ResourceLocation,
		resolvedIdentifier: ResourceLocation
	): Float? {
		if (!parametersElement.isJsonObject) {
			Cobblemon.LOGGER.error(
				"Failed to parse climate parameters in {} ({}): '{}' must be an object",
				sourceIdentifier,
				resolvedIdentifier,
				PARAMETERS_KEY
			)
			return null
		}

		val temperatureElement = parametersElement.asJsonObject.get(TEMPERATURE_KEY)
		if (temperatureElement == null) {
			Cobblemon.LOGGER.error(
				"Failed to parse climate parameters in {} ({}): missing '{}.{}'",
				sourceIdentifier,
				resolvedIdentifier,
				PARAMETERS_KEY,
				TEMPERATURE_KEY
			)
			return null
		}

		if (temperatureElement.isJsonPrimitive) {
			return temperatureElement.asFloat
		}

		if (!temperatureElement.isJsonObject) {
			Cobblemon.LOGGER.error(
				"Failed to parse climate parameters in {} ({}): '{}.{}' must be a number or an object with '{}'/'{}'",
				sourceIdentifier,
				resolvedIdentifier,
				PARAMETERS_KEY,
				TEMPERATURE_KEY,
				MIN_KEY,
				MAX_KEY
			)
			return null
		}

		val temperatureObject = temperatureElement.asJsonObject
		val minElement = temperatureObject.get(MIN_KEY)
		val maxElement = temperatureObject.get(MAX_KEY)
		if (minElement == null || maxElement == null) {
			Cobblemon.LOGGER.error(
				"Failed to parse climate parameters in {} ({}): '{}.{}' must include '{}' and '{}'",
				sourceIdentifier,
				resolvedIdentifier,
				PARAMETERS_KEY,
				TEMPERATURE_KEY,
				MIN_KEY,
				MAX_KEY
			)
			return null
		}

		val min = minElement.asFloat
		val max = maxElement.asFloat
		if (min != max) {
			Cobblemon.LOGGER.warn(
				"Biome {} ({}) uses a temperature range in '{}.{}' ({}..{}); using midpoint for biome temperature",
				sourceIdentifier,
				resolvedIdentifier,
				PARAMETERS_KEY,
				TEMPERATURE_KEY,
				min,
				max
			)
		}

		return (min + max) / 2F
	}
}