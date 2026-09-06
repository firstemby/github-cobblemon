/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.player.client

import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.net.messages.client.SetClientPlayerDataPacket
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class ClientTMMoveManager(
    val learnedTMs: MutableSet<ResourceLocation>
) : ClientInstancedPlayerData {

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeCollection(learnedTMs) { _, id -> buf.writeString(id.toString()) }
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): SetClientPlayerDataPacket {
            val tms = buf.readCollection(
                { mutableSetOf<ResourceLocation>() },
                { buf.readString().let(ResourceLocation::tryParse) ?: ResourceLocation("minecraft", "empty") }
            )
            return SetClientPlayerDataPacket(
                PlayerInstancedDataStoreTypes.TM_MOVES,
                ClientTMMoveManager(tms)
            )
        }

        fun runAction(data: ClientInstancedPlayerData) {
            if (data !is ClientTMMoveManager) return
            CobblemonClient.clientTMMoveData = data
        }

        fun runIncremental(data: ClientInstancedPlayerData) {
            if (data !is ClientTMMoveManager) return
            CobblemonClient.clientTMMoveData.learnedTMs.addAll(data.learnedTMs)
        }
    }
}