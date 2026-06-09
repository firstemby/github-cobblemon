package com.cobblemon.mod.common.api.storage.player.client

import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.net.messages.client.SetClientPlayerDataPacket
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class ClientScannedBlocksManager(
    val scannedKeys: MutableSet<ResourceLocation>
) : ClientInstancedPlayerData {

    fun hasScanned(key: ResourceLocation): Boolean = key in scannedKeys

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeCollection(scannedKeys) { _, id -> buf.writeString(id.toString()) }
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): SetClientPlayerDataPacket {
            val keys = buf.readCollection(
                { mutableSetOf<ResourceLocation>() },
                { buf.readString().let(ResourceLocation::tryParse) ?: ResourceLocation("minecraft", "empty") }
            )
            return SetClientPlayerDataPacket(
                PlayerInstancedDataStoreTypes.SCANNED_BLOCKS,
                ClientScannedBlocksManager(keys)
            )
        }

        fun runAction(data: ClientInstancedPlayerData) {
            if (data !is ClientScannedBlocksManager) return
            CobblemonClient.clientScannedBlocksData = data
        }

        fun runIncremental(data: ClientInstancedPlayerData) {
            if (data !is ClientScannedBlocksManager) return
            CobblemonClient.clientScannedBlocksData.scannedKeys.addAll(data.scannedKeys)
        }
    }
}
