/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.npc

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Applies the premade NPC data to the given NPC.
 *
 * @author Hiroku
 * @since April 18th, 2026
 */
class ApplyPremadeNPCPacket(
    val npcId: Int,
    val premadeNPCId: ResourceLocation,
) : NetworkPacket<ApplyPremadeNPCPacket> {
    companion object {
        val ID = cobblemonResource("apply_premade")
        fun decode(buffer: RegistryFriendlyByteBuf): ApplyPremadeNPCPacket {
            val npcId = buffer.readInt()
            val premadeNPCId = buffer.readResourceLocation()
            return ApplyPremadeNPCPacket(npcId, premadeNPCId)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(npcId)
        buffer.writeResourceLocation(premadeNPCId)
    }
}