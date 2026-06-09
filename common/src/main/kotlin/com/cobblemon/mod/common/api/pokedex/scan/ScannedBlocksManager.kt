package com.cobblemon.mod.common.api.pokedex.scan

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.storage.player.InstancedPlayerData
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.api.storage.player.client.ClientScannedBlocksManager
import com.cobblemon.mod.common.net.messages.client.SetClientPlayerDataPacket
import com.cobblemon.mod.common.util.getPlayer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.UUID
import net.minecraft.resources.ResourceLocation

class ScannedBlocksManager(
    override val uuid: UUID,
    val scannedKeys: MutableSet<ResourceLocation> = mutableSetOf()
) : InstancedPlayerData {

    val storeType = PlayerInstancedDataStoreTypes.SCANNED_BLOCKS

    override fun toClientData() = ClientScannedBlocksManager(scannedKeys.toMutableSet())

    fun record(key: ResourceLocation): Boolean {
        if (!scannedKeys.add(key)) return false
        syncClient(setOf(key), isIncremental = true)
        return true
    }

    fun syncClient(updateSet: Set<ResourceLocation> = scannedKeys, isIncremental: Boolean = true) {
        uuid.getPlayer()?.sendPacket(
            SetClientPlayerDataPacket(
                type = storeType,
                playerData = ClientScannedBlocksManager(updateSet.toMutableSet()),
                isIncremental = isIncremental
            )
        )
    }

    companion object {
        val CODEC: Codec<ScannedBlocksManager> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString)
                    .fieldOf("uuid")
                    .forGetter { it.uuid },
                Codec.list(ResourceLocation.CODEC)
                    .xmap({ it.toMutableSet() }, { it.toList() })
                    .fieldOf("scannedKeys")
                    .forGetter { it.scannedKeys }
            ).apply(instance, ::ScannedBlocksManager)
        }
    }
}
