/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.npc

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.npc.premades.PremadeNPC
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation

/**
 * Packet to the server to tell it to take properties from the NPC associated with the [npcId] entity ID
 * and save them as a [PremadeNPC] against the given identifier. It will overwrite any premades that already
 * exist against that name.
 *
 * @author Hiroku
 * @since April 18th, 2026
 */
class SavePremadeFromNPCPacket(
    val npcId: Int,
    val premadeNPCId: ResourceLocation,
    val displayName: MutableComponent,
    val includeVariation: Boolean,
    val includeSettings: Boolean,
    val includeParty: Boolean,
    val includeClassification: Boolean,
) : NetworkPacket<SavePremadeFromNPCPacket> {
    companion object {
        val ID = cobblemonResource("save_premade_from_npc")
        fun decode(buffer: RegistryFriendlyByteBuf): SavePremadeFromNPCPacket {
            val npcId = buffer.readInt()
            val premadeNPCId = buffer.readResourceLocation()
            val displayName = buffer.readText().copy()
            val includeVariation = buffer.readBoolean()
            val includeSettings = buffer.readBoolean()
            val includeParty = buffer.readBoolean()
            val includeClassification = buffer.readBoolean()
            return SavePremadeFromNPCPacket(
                npcId = npcId,
                premadeNPCId = premadeNPCId,
                displayName = displayName,
                includeVariation = includeVariation,
                includeSettings = includeSettings,
                includeParty = includeParty,
                includeClassification = includeClassification
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(npcId)
        buffer.writeResourceLocation(premadeNPCId)
        buffer.writeText(displayName)
        buffer.writeBoolean(includeVariation)
        buffer.writeBoolean(includeSettings)
        buffer.writeBoolean(includeParty)
        buffer.writeBoolean(includeClassification)
    }
}