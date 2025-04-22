package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.writeIdentifier
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.*

class BattleCameraPacket(
    val joiningBattle: UUID,
    val cameraManager: ResourceLocation,
    val center: Vec3,
    val yRot: Float
): NetworkPacket<BattleCameraPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(this.joiningBattle)
        buffer.writeIdentifier(cameraManager)
        buffer.writeVec3(center)
        buffer.writeFloat(yRot)
    }
    companion object {
        val ID = cobblemonResource("stadium_join")
        fun decode(buffer: RegistryFriendlyByteBuf) = BattleCameraPacket(buffer.readUUID(), buffer.readIdentifier(), buffer.readVec3(), buffer.readFloat())
    }
}