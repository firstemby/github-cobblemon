package com.cobblemon.mod.common

import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.world.biome.CobblemonBiome
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.biome.Biome

object CobblemonBiomes : PlatformRegistry<Registry<Biome>, ResourceKey<Registry<Biome>>, Biome>() {
	override var registry: Registry<Biome>? = null
	override val resourceKey: ResourceKey<Registry<Biome>> = Registries.BIOME

	@JvmField
	val TEST_BIOME: ResourceKey<Biome> = registerKey("test_biome")

	@JvmStatic
	fun register(data: Map<ResourceLocation, CobblemonBiome>) {
		data.forEach { (identifier, cobblemonBiome) ->
			create(identifier, cobblemonBiome.biome)
		}
	}

	private fun registerKey(key: String): ResourceKey<Biome> {
		return ResourceKey.create(Registries.BIOME, cobblemonResource(key))
	}
}
