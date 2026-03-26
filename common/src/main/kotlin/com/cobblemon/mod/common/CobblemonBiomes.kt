package com.cobblemon.mod.common

import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.Cobblemon.implementation
import com.cobblemon.mod.common.duck.biome.BiomeSourceDuck
import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.dimension.LevelStem

object CobblemonBiomes : PlatformRegistry<Registry<Biome>, ResourceKey<Registry<Biome>>, Biome>() {
	override var registry: Registry<Biome>? = null
	override val resourceKey: ResourceKey<Registry<Biome>> = Registries.BIOME

	fun init(server: MinecraftServer) {
		val biomeRegistryKey: ResourceKey<out Registry<Biome>> = Registries.BIOME
		val biomeRegistry = server
			.registryAccess()
			.registryOrThrow(biomeRegistryKey)
		registry = biomeRegistry
		implementation.registerBiomes()
		biomeRegistry.keySet()
			.filter { it.namespace == "cobblemon" }
			.forEach { LOGGER.info("COBBLEMON BIOMES: {}", it) }

		val registryAccess: RegistryAccess = server.registryAccess()
		val levelStemRegistry = registryAccess.registryOrThrow(Registries.LEVEL_STEM)
		//val seed = server.getWorldData().worldGenOptions().seed()

		for (entry in levelStemRegistry.entrySet()) {
			val stem: LevelStem = entry.value!!
			initializeBiomes(
				stem.generator
			)
		}
	}

	fun initializeBiomes(chunkGenerator: ChunkGenerator) {
		val biomeSource = chunkGenerator.biomeSource
		val biomeSourceDuck = biomeSource as BiomeSourceDuck
		val biome: Holder<Biome> = getBiome(cobblemonResource("test_biome"))
		
		biomeSourceDuck.addPossibleBiomes(listOf(biome))
	}

	fun getBiome(key: ResourceLocation): Holder<Biome> {
		val biomeKey = ResourceKey.create(Registries.BIOME, key)
		return registry?.getHolderOrThrow(biomeKey)
			?: throw IllegalStateException("Biome registry not initialized")
	}
}
