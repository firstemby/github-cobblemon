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
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Deletes the world-saved [PremadeNPC] with the given identifier.
 *
 * @author Hiroku
 * @since April 18th, 2026
 */
class DeletePremadePacket(val premadeId: ResourceLocation) : NetworkPacket<DeletePremadePacket> {
    companion object {
        val ID = cobblemonResource("delete_premade")
        fun decode(buffer: RegistryFriendlyByteBuf) = DeletePremadePacket(buffer.readResourceLocation())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(premadeId)
    }
}