package com.cobblemon.mod.common.api.storage.player.client

import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataCategory
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.net.messages.client.SetClientPlayerDataPacket
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** Client mirror of [com.cobblemon.mod.common.api.pokedex.scan.ScannedDataManager], keyed by bucket path. */
class ClientScannedDataManager(
    val scanned: MutableMap<String, MutableSet<ResourceLocation>>
) : ClientInstancedPlayerData {

    fun has(bucket: String, key: ResourceLocation): Boolean = scanned[bucket]?.contains(key) == true

    fun has(category: ScannedDataCategory, sub: String?, key: ResourceLocation): Boolean =
        has(category.bucket(sub), key)

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(scanned.size)
        scanned.forEach { (bucket, set) ->
            buf.writeString(bucket)
            buf.writeCollection(set) { _, id -> buf.writeString(id.toString()) }
        }
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): SetClientPlayerDataPacket {
            val map = mutableMapOf<String, MutableSet<ResourceLocation>>()
            val size = buf.readVarInt()
            repeat(size) {
                val bucket = buf.readString()
                val set = buf.readCollection(
                    { mutableSetOf<ResourceLocation>() },
                    { buf.readString().let(ResourceLocation::tryParse) ?: ResourceLocation("minecraft", "empty") }
                )
                map.getOrPut(bucket) { mutableSetOf() }.addAll(set)
            }
            return SetClientPlayerDataPacket(
                PlayerInstancedDataStoreTypes.SCANNED_DATA,
                ClientScannedDataManager(map)
            )
        }

        fun runAction(data: ClientInstancedPlayerData) {
            if (data !is ClientScannedDataManager) return
            CobblemonClient.clientScannedData = data
        }

        fun runIncremental(data: ClientInstancedPlayerData) {
            if (data !is ClientScannedDataManager) return
            data.scanned.forEach { (bucket, set) ->
                CobblemonClient.clientScannedData.scanned.getOrPut(bucket) { mutableSetOf() }.addAll(set)
            }
        }
    }
}
