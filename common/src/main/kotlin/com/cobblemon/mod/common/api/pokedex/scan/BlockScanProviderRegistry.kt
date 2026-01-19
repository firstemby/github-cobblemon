package com.cobblemon.mod.common.api.pokedex.scan

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokedex.scanner.block.BlockScanProvider
import net.minecraft.resources.ResourceLocation

object BlockScanProviderRegistry {
    private val providers = mutableMapOf<ResourceLocation, BlockScanProvider>()

    fun register(typeId: ResourceLocation, provider: BlockScanProvider) {
        providers[typeId] = provider

        /*Cobblemon.LOGGER.info(
            "[BlockScanProviderRegistry] register(): typeId={} provider={} totalProviders={}",
            typeId,
            provider::class.java.name,
            providers.size
        )*/
    }

    fun get(typeId: ResourceLocation): BlockScanProvider? {
        val found = providers[typeId]

        /*Cobblemon.LOGGER.info(
            "[BlockScanProviderRegistry] get(): typeId={} found={} knownKeys={}",
            typeId,
            found != null,
            if (providers.isEmpty()) "<none>" else providers.keys.joinToString()
        )*/

        return found
    }

    fun debugKeys(): String = providers.keys.joinToString(prefix = "[", postfix = "]")
}