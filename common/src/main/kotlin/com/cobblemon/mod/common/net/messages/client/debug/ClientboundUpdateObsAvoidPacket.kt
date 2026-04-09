/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.debug

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.render.debug.EntityDebugLine
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeCollection
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

/**
 * Packet sent to the client to update the obstacle avoidance lines to be rendered
 * while debugging
 *
 * @author Jackson
 * @since September 15th, 2025
 */
class ClientboundUpdateObsAvoidPacket(val entityId: Int, val lines: List<EntityDebugLine>) : NetworkPacket<ClientboundUpdateObsAvoidPacket> {
    override val id: ResourceLocation = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(entityId)
        buffer.writeCollection(lines) { _, linePair ->
            linePair.encode(buffer)
        }
    }

    companion object {
        val ID = cobblemonResource("obstacle_avoidance_lines")

        fun decode(buffer: RegistryFriendlyByteBuf): ClientboundUpdateObsAvoidPacket {
            val entityId = buffer.readInt()
            val lines = buffer.readList { buf ->
                EntityDebugLine.decode(buf as RegistryFriendlyByteBuf)
            }
            return ClientboundUpdateObsAvoidPacket(entityId, lines)
        }
    }
}