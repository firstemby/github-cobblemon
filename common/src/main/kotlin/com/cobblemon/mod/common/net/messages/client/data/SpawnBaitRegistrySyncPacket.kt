/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.fishing.SpawnBait
import com.cobblemon.mod.common.api.fishing.SpawnBaitEffects
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.Identifier

class SpawnBaitRegistrySyncPacket(spawnBaits: Map<Identifier, SpawnBait>) : DataRegistrySyncPacket<Map.Entry<Identifier, SpawnBait>, SpawnBaitRegistrySyncPacket>(spawnBaits.entries) {
    companion object {
        val ID = cobblemonResource("spawn_baits")
        fun decode(buffer: RegistryFriendlyByteBuf) = SpawnBaitRegistrySyncPacket(emptyMap()).apply { decodeBuffer(buffer) }
    }

    override val id = ID
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: Map.Entry<Identifier, SpawnBait>) {
        buffer.writeIdentifier(entry.key)
        SpawnBait.STREAM_CODEC.encode(buffer, entry.value)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): Map.Entry<Identifier, SpawnBait> {
        val Identifier = buffer.readIdentifier()
        val bait = SpawnBait.STREAM_CODEC.decode(buffer)
        return object : Map.Entry<Identifier, SpawnBait> {
            override val key: Identifier = Identifier
            override val value: SpawnBait = bait
        }
    }

    override fun synchronizeDecoded(entries: Collection<Map.Entry<Identifier, SpawnBait>>) {
        SpawnBaitEffects.reload(entries.associateByTo(mutableMapOf(), { it.key }, { it.value }))
    }
}
