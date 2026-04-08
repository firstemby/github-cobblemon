package com.cobblemon.mod.common

import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.biome.*

object CobblemonBiomes : PlatformRegistry<Registry<Biome>, ResourceKey<Registry<Biome>>, Biome>() {
	override var registry: Registry<Biome>? = null
	override val resourceKey: ResourceKey<Registry<Biome>> = Registries.BIOME

	@JvmField
	val TEST_BIOME: ResourceKey<Biome> = registerKey("test_biome")

	// TODO: The following methods will be migrated to JSON Loading, so its fine to stay here TBA.

	fun bootstrap() {
		create("test_biome", createTestBiome())
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

	// TODO: Until here

	private fun registerKey(key: String): ResourceKey<Biome> {
		return ResourceKey.create(Registries.BIOME, cobblemonResource(key))
	}
}
