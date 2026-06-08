package com.cobblemon.mod.common.api.pokedex.scan

import com.cobblemon.mod.common.pokedex.scanner.block.BlockScanProvider
import net.minecraft.resources.ResourceLocation

object BlockScanProviderRegistry {
    private val providers = mutableMapOf<ResourceLocation, BlockScanProvider>()

    fun register(typeId: ResourceLocation, provider: BlockScanProvider) {
        providers[typeId] = provider
    }

    fun get(typeId: ResourceLocation): BlockScanProvider? = providers[typeId]
}
