/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readNbt
import com.cobblemon.mod.common.util.writeNbt
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncShoulderEntityPacket(
    val entityId: Int,
    val leftShoulder: CompoundTag,
    val rightShoulder: CompoundTag
) : NetworkPacket<SyncShoulderEntityPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(entityId)
        buffer.writeNbt(leftShoulder)
        buffer.writeNbt(rightShoulder)
    }

    companion object {
        val ID = cobblemonResource("sync_shoulder_entity")

        fun decode(buffer: RegistryFriendlyByteBuf): SyncShoulderEntityPacket {
            val entityId = buffer.readVarInt()
            val leftShoulder = buffer.readNbt() as? CompoundTag ?: CompoundTag()
            val rightShoulder = buffer.readNbt() as? CompoundTag ?: CompoundTag()
            return SyncShoulderEntityPacket(entityId, leftShoulder, rightShoulder)
        }
    }
}
