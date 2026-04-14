package com.cobblemon.mod.common.world.biome

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.asJsonObjectOrNull
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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

object CobblemonBiomeLoader : JsonDataRegistry<CobblemonBiome> {

	override val gson: Gson = GsonBuilder().setPrettyPrinting().create()
	override val typeToken: TypeToken<CobblemonBiome> = TypeToken.get(CobblemonBiome::class.java)
	override val resourcePath = "biomes"
	override val id = cobblemonResource(resourcePath)
	override val type = PackType.SERVER_DATA
	override val observable = SimpleObservable<CobblemonBiomeLoader>()

	private const val PARAMETERS = "parameters"
	private const val TEMPERATURE = "temperature"
	private const val MIN = "min"
	private const val MAX = "max"
	private var loadedBiomes: Map<ResourceLocation, CobblemonBiome> = emptyMap()

	@JvmStatic
	fun getLoadedBiomes(): Map<ResourceLocation, CobblemonBiome> = loadedBiomes

	@JvmStatic
	fun getLoadedBiome(location: ResourceLocation): CobblemonBiome {
		return loadedBiomes[location] ?: throw IllegalArgumentException("Unknown location: $location")
	}

	@JvmStatic
	fun load(
		manager: ResourceManager,
		registryInfoLookup: RegistryOps.RegistryInfoLookup
	): Map<ResourceLocation, CobblemonBiome> {
		val ops = RegistryOps.create(JsonOps.INSTANCE, registryInfoLookup)

		val result = loadJsonData(manager) { reader, _, _ ->
			val root = JsonParser.parseReader(reader).asJsonObjectOrNull() ?: return@loadJsonData null

			val params = root.getAsJsonObject(PARAMETERS) ?: return@loadJsonData null
			val tempEl = params.get(TEMPERATURE) ?: return@loadJsonData null

			val temp =
				if (tempEl.isJsonPrimitive) tempEl.asFloat
				else tempEl.asJsonObjectOrNull()?.let {
					val min = it.get(MIN)?.asFloat ?: return@loadJsonData null
					val max = it.get(MAX)?.asFloat ?: return@loadJsonData null
					(min + max) / 2F
				} ?: return@loadJsonData null

			val biomeJson = root.deepCopy().apply {
				addProperty(TEMPERATURE, temp)
			}

			val biome = Biome.DIRECT_CODEC.parse(ops, biomeJson)
				.result().orElse(null) ?: return@loadJsonData null

			val climate = Climate.ParameterPoint.CODEC.parse(JsonOps.INSTANCE, params)
				.result().orElse(null) ?: return@loadJsonData null

			CobblemonBiome(biome, climate)
		}

		loadedBiomes = result.toMap()
		observable.emit(this)
		Cobblemon.LOGGER.info("Loaded {} cobblemon biomes.", result.size)
		return result
	}

	override fun reload(manager: ResourceManager) = observable.emit(this)
	override fun reload(data: Map<ResourceLocation, CobblemonBiome>) = Unit
	override fun sync(player: ServerPlayer) = Unit
}