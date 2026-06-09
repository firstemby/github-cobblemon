package com.cobblemon.mod.common.api.pokedex.scan

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.storage.player.InstancedPlayerData
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.api.storage.player.client.ClientScannedDataManager
import com.cobblemon.mod.common.net.messages.client.SetClientPlayerDataPacket
import com.cobblemon.mod.common.util.getPlayer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.UUID
import net.minecraft.resources.ResourceLocation

/**
 * Server-side store of everything a player has scanned, grouped by bucket path. A bucket is a
 * [ScannedDataCategory] id optionally narrowed by a sub-category, e.g. "berries", "structures",
 * "items/pokeballs". See [ScannedDataCategory.bucket].
 */
class ScannedDataManager(
    override val uuid: UUID,
    val scanned: MutableMap<String, MutableSet<ResourceLocation>> = mutableMapOf()
) : InstancedPlayerData {

    val storeType = PlayerInstancedDataStoreTypes.SCANNED_DATA

    override fun toClientData() = ClientScannedDataManager(deepCopy())

    fun has(bucket: String, key: ResourceLocation): Boolean = scanned[bucket]?.contains(key) == true

    fun has(category: ScannedDataCategory, sub: String?, key: ResourceLocation): Boolean =
        has(category.bucket(sub), key)

    fun entriesFor(bucket: String): Set<ResourceLocation> = scanned[bucket]?.toSet() ?: emptySet()

    /** Records a scan into a bucket, syncing just the new entry to the client. False if already known. */
    fun record(bucket: String, key: ResourceLocation): Boolean {
        val set = scanned.getOrPut(bucket) { mutableSetOf() }
        if (!set.add(key)) return false
        syncClient(mapOf(bucket to mutableSetOf(key)), isIncremental = true)
        return true
    }

    fun record(category: ScannedDataCategory, sub: String?, key: ResourceLocation): Boolean =
        record(category.bucket(sub), key)

    private fun deepCopy(): MutableMap<String, MutableSet<ResourceLocation>> {
        val copy = mutableMapOf<String, MutableSet<ResourceLocation>>()
        scanned.forEach { (bucket, set) -> copy[bucket] = set.toMutableSet() }
        return copy
    }

    fun syncClient(
        update: Map<String, MutableSet<ResourceLocation>> = scanned,
        isIncremental: Boolean = true
    ) {
        val payload = mutableMapOf<String, MutableSet<ResourceLocation>>()
        update.forEach { (bucket, set) -> payload[bucket] = set.toMutableSet() }
        uuid.getPlayer()?.sendPacket(
            SetClientPlayerDataPacket(
                type = storeType,
                playerData = ClientScannedDataManager(payload),
                isIncremental = isIncremental
            )
        )
    }

    companion object {
        private val ENTRY_SET_CODEC: Codec<MutableSet<ResourceLocation>> =
            Codec.list(ResourceLocation.CODEC).xmap({ it.toMutableSet() }, { it.toList() })

        val CODEC: Codec<ScannedDataManager> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString)
                    .fieldOf("uuid")
                    .forGetter { it.uuid },
                Codec.unboundedMap(Codec.STRING, ENTRY_SET_CODEC)
                    .xmap({ it.toMutableMap() }, { it.toMap() })
                    .fieldOf("scanned")
                    .forGetter { it.scanned }
            ).apply(instance, ::ScannedDataManager)
        }
    }
}
