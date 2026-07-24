/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.spawn

import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.projectile.FallingStarEntity
import com.cobblemon.mod.common.net.IntSize
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readSizedInt
import com.cobblemon.mod.common.util.writeSizedInt
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity

class SpawnFallingStarPacket(
    val poseType: PoseType,
    val horizontalDirection: Float,
    vanillaSpawnPacket: ClientboundAddEntityPacket
) : SpawnExtraDataEntityPacket<SpawnFallingStarPacket, FallingStarEntity>(vanillaSpawnPacket) {
    override val id: ResourceLocation = ID

    override fun applyData(entity: FallingStarEntity, level: ClientLevel) {
        entity.poseType = this.poseType
        entity.horizontalDirection = this.horizontalDirection
    }

    override fun checkType(entity: Entity): Boolean = entity is FallingStarEntity

    override fun encodeEntityData(buffer: RegistryFriendlyByteBuf) {
        buffer.writeSizedInt(size = IntSize.U_BYTE, poseType.ordinal)
        buffer.writeFloat(horizontalDirection)
    }

    companion object {
        val ID = cobblemonResource("spawn_falling_star_entity")
        fun decode(buffer: RegistryFriendlyByteBuf): SpawnFallingStarPacket {
            val poseType = PoseType.entries[buffer.readSizedInt(IntSize.U_BYTE)]
            val horizontalDirection = buffer.readFloat()
            val vanillaPacket = decodeVanillaPacket(buffer)
            return SpawnFallingStarPacket(poseType, horizontalDirection, vanillaPacket)
        }
    }
}
