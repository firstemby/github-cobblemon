package com.cobblemon.mod.common.world.biome

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
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

object CobblemonBiomeLoader : JsonDataRegistry<Biome> {
	override val gson: Gson = GsonBuilder()
		.setPrettyPrinting()
		.create()
	override val typeToken: TypeToken<Biome> = TypeToken.get(Biome::class.java)
	override val resourcePath: String = "biomes"
	override val id: ResourceLocation = cobblemonResource(resourcePath)
	override val type = PackType.SERVER_DATA
	override val observable = SimpleObservable<CobblemonBiomeLoader>()

	@JvmStatic
	fun load(
		manager: ResourceManager,
		registryInfoLookup: RegistryOps.RegistryInfoLookup
	): Map<ResourceLocation, Biome> {
		val ops = RegistryOps.create(JsonOps.INSTANCE, registryInfoLookup)
		val parsedBiomes = loadJsonData(manager) { reader, sourceIdentifier, resolvedIdentifier ->
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
				.orElse(null)
			biome
		}

		reload(parsedBiomes)
		observable.emit(this)
		Cobblemon.LOGGER.info("Loaded {} biomes.", parsedBiomes.size)
		return parsedBiomes
	}

	override fun reload(manager: ResourceManager) {
		observable.emit(this)
	}

	override fun reload(data: Map<ResourceLocation, Biome>) {
	}

	override fun sync(player: ServerPlayer) {}
}