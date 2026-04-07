package com.cobblemon.mod.common

import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.Cobblemon.implementation
import com.cobblemon.mod.common.duck.biome.BiomeSourceDuck
import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.*
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.RegistryLayer
import net.minecraft.world.level.biome.*
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver
import net.minecraft.world.level.levelgen.placement.PlacedFeature
import java.util.*

object CobblemonBiomes : PlatformRegistry<Registry<Biome>, ResourceKey<Registry<Biome>>, Biome>() {
	override var registry: Registry<Biome>? = null
	override val resourceKey: ResourceKey<Registry<Biome>> = Registries.BIOME

	@JvmField
	val TEST_BIOME: ResourceKey<Biome> = registerKey("test_biome")

	fun bootstrap() {
		create("test_biome", createTestBiome())
	}

	@JvmStatic
	fun init(server: MinecraftServer) {
		val biomeRegistryKey: ResourceKey<out Registry<Biome>> = Registries.BIOME
		val biomeRegistry = server
			.registryAccess()
			.registryOrThrow(biomeRegistryKey)
		registry = biomeRegistry
		implementation.registerBiomes()
		val registryAccess: RegistryAccess = server.registryAccess()
		biomeRegistry.keySet()
			.filter { it.namespace == "cobblemon" }
			.forEach { LOGGER.info("COBBLEMON BIOMES: {}", it) }

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
		val biomeSourceDuck = biomeSource as? BiomeSourceDuck ?: return
		val biome: Holder<Biome> = getBiome(cobblemonResource("test_biome"))

		biomeSourceDuck.addPossibleBiomes(listOf(biome))
	}

	fun getBiome(key: ResourceLocation): Holder<Biome> {
		val biomeKey = ResourceKey.create(Registries.BIOME, key)
		val biomeRegistry = registry ?: throw IllegalStateException("Biome registry not initialized")

		return biomeRegistry.getHolder(biomeKey).orElseGet {
			LOGGER.warn(
				"Biome {} is not registered yet; using minecraft:plains as a temporary hardcoded fallback.",
				key
			)
			biomeRegistry.getHolderOrThrow(Biomes.PLAINS)
		}
	}

	@JvmStatic
	fun createTestBiome(
	): Biome {
		return Biome.BiomeBuilder()
			.hasPrecipitation(false)
			.temperature(2.0F)
			.downfall(0.0F)
			.specialEffects(
				BiomeSpecialEffects.Builder()
					.fogColor(12638463)
					.skyColor(8103167)
					.waterColor(4159204)
					.waterFogColor(329011)
					.ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
					.build()
			)
			.mobSpawnSettings(MobSpawnSettings.Builder().build())
			.generationSettings(BiomeGenerationSettings.EMPTY)
			.build()
	}

	private fun registerKey(key: String): ResourceKey<Biome> {
		return ResourceKey.create(Registries.BIOME, cobblemonResource(key))
	}
}
