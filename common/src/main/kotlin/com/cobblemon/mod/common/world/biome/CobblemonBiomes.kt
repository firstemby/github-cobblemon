package com.cobblemon.mod.common.world.biome

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.level.biome.Biome
import java.util.Optional

/* TODO: To Future Aram: READ THESE
net.minecraft.data.worldgen.biome.OverworldBiomes
net.minecraft.world.level.biome.OverworldBiomeBuilder
net.minecraft.world.level.biome.Biome.BiomeBuilder

And otherwise look further
 */
object CobblemonBiomes : JsonDataRegistry<CobblemonBiome> {
	override val gson: Gson = GsonBuilder()
		.setPrettyPrinting()
		.create()
	override val typeToken: TypeToken<CobblemonBiome> = TypeToken.get(CobblemonBiome::class.java)
	override val resourcePath: String = "biomes"
	override val id: ResourceLocation = cobblemonResource(resourcePath)
	override val type = PackType.SERVER_DATA
	override val observable = SimpleObservable<CobblemonBiomes>()

	override fun reload(data: Map<ResourceLocation, CobblemonBiome>) {
		TODO("Not yet implemented")
	}

	override fun sync(player: ServerPlayer) {}
}