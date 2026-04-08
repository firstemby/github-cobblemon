package com.cobblemon.mod.common

import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.Cobblemon.implementation
import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.biome.*
import java.util.*

object CobblemonBiomes : PlatformRegistry<Registry<Biome>, ResourceKey<Registry<Biome>>, Biome>() {
	override var registry: Registry<Biome>? = null
	override val resourceKey: ResourceKey<Registry<Biome>> = Registries.BIOME

	@JvmField
	var registryManager: RegistryAccess.Frozen? = null

	@JvmField
	val TEST_BIOME: ResourceKey<Biome> = registerKey("test_biome")

	@JvmStatic
	fun getBiomeLookup(): Optional<HolderLookup.RegistryLookup<Biome>> {
		if (registryManager == null) {
			LOGGER.info("Registry manager is not set yet; cannot provide biome lookup.")
			return Optional.empty()
		}

		return registryManager!!.lookup(Registries.BIOME)
	}

	fun bootstrap() {
		create("test_biome", createTestBiome())
	}

	@JvmStatic
	fun init(loadedRegistry: Registry<Biome>) {
		LOGGER.info("Initializing Cobblemon Biomes...")
		registry = loadedRegistry
		implementation.registerBiomes()
		if (!loadedRegistry.containsKey(TEST_BIOME.location())) {
			LOGGER.warn("Biome {} is not registered in server biome registry.", TEST_BIOME.location())
		}
	}

	fun onServerStarted(server: MinecraftServer) {
		LOGGER.info("All Biome Registry Keys: " + registry?.keySet())
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
