/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.biome

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.world.biome.parse.CobblemonBiomeJsonParser
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.mojang.serialization.JsonOps
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import java.io.Reader

object CobblemonBiomeLoader : JsonDataRegistry<CobblemonBiome> {

	override val gson: Gson = GsonBuilder().setPrettyPrinting().create()
	override val typeToken: TypeToken<CobblemonBiome> = TypeToken.get(CobblemonBiome::class.java)
	override val resourcePath = "biomes"
	override val id = cobblemonResource(resourcePath)
	override val type = PackType.SERVER_DATA
	override val observable = SimpleObservable<CobblemonBiomeLoader>()

	private var loadedBiomes: Map<ResourceLocation, CobblemonBiome> = emptyMap()

	@JvmStatic
	fun load(
		manager: ResourceManager,
		registryInfoLookup: RegistryOps.RegistryInfoLookup
	): Map<ResourceLocation, CobblemonBiome> {

		val ops = RegistryOps.create(JsonOps.INSTANCE, registryInfoLookup)

		val result = loadJsonData(manager) { reader, _, _ ->
			parseBiome(reader, ops)
		}

		loadedBiomes = result.toMap()

		observable.emit(this)
		Cobblemon.LOGGER.info("MEALAM Loaded {} cobblemon biomes.", result.size)

		return result
	}

	private fun parseBiome(
		reader: Reader,
		ops: RegistryOps<JsonElement>
	): CobblemonBiome? = CobblemonBiomeJsonParser.parse(reader, ops)

	override fun reload(manager: ResourceManager) = observable.emit(this)
	override fun reload(data: Map<ResourceLocation, CobblemonBiome>) = Unit
	override fun sync(player: ServerPlayer) = Unit
	
	@JvmStatic
	fun getLoadedBiomes(): Map<ResourceLocation, CobblemonBiome> = loadedBiomes

	@JvmStatic
	fun getLoadedBiome(location: ResourceLocation): CobblemonBiome =
		loadedBiomes[location]
			?: error("MEALAM Unknown location: $location")
}