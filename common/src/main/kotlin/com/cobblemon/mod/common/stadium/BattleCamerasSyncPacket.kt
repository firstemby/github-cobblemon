package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.net.messages.client.data.DataRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class BattleCamerasSyncPacket(cameras: Collection<BattleCameraEntry>) :
    DataRegistrySyncPacket<BattleCameraEntry, BattleCamerasSyncPacket>(cameras) {
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: BattleCameraEntry) {
        entry.encode(buffer)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): BattleCameraEntry? {
        return BattleCameraEntry.decode(buffer)
    }

    override fun synchronizeDecoded(entries: Collection<BattleCameraEntry>) {
        BattleCameras.reload(entries.associateBy { it.id })
    }

    override val id = ID

    companion object {
        val ID = cobblemonResource("stadium_cameras_sync")
        fun decode(buffer: RegistryFriendlyByteBuf) = BattleCamerasSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }
}