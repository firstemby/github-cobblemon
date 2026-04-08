package com.cobblemon.mod.common.util.biome

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import java.util.*

object RegistryExtensions {
	@JvmStatic
	fun <T> getRegistryLookup(
		registryKey: ResourceKey<out Registry<T>>
	): Optional<HolderLookup.RegistryLookup<T>> {
		return Cobblemon.registryManager.getRegistryLookup(registryKey)
	}
}

fun <T> RegistryAccess?.getRegistryLookup(
	registryKey: ResourceKey<out Registry<T>>
): Optional<HolderLookup.RegistryLookup<T>> {
	if (this == null || this == RegistryAccess.EMPTY) {
		return Optional.empty()
	}

	return this.lookup(registryKey)
}