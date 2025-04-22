package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.net.messages.client.data.DataRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class BattleCameraManagersSyncPacket(managers: Collection<BattleCameraManagerEntry>) :
    DataRegistrySyncPacket<BattleCameraManagerEntry, BattleCameraManagersSyncPacket>(managers) {
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: BattleCameraManagerEntry) {
        entry.encode(buffer)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): BattleCameraManagerEntry? {
        return BattleCameraManagerEntry.decode(buffer)
    }

    override fun synchronizeDecoded(entries: Collection<BattleCameraManagerEntry>) {
        BattleCameraManagers.reload(entries.associateBy { it.id })
    }

    override val id = ID

    companion object {
        val ID = cobblemonResource("stadium_camera_managers_sync")
        fun decode(buffer: RegistryFriendlyByteBuf) = BattleCameraManagersSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }
}