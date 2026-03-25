package com.cobblemon.mod.common

import com.cobblemon.mod.common.platform.PlatformRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.biome.Biome

object CobblemonBiomes : PlatformRegistry<Registry<Biome>, ResourceKey<Registry<Biome>>, Biome>() {
	override var registry: Registry<Biome>? = null 
	override val resourceKey: ResourceKey<Registry<Biome>> = Registries.BIOME
}
