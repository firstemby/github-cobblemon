/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.npc.premades.PremadeNPC
import com.cobblemon.mod.common.api.npc.premades.PremadeNPCs
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Sync packet for premade NPCs so that the client knows which exist and which are editable
 *
 * @author Hiroku
 * @since April 12th, 2026
 */
class PremadeNPCRegistrySyncPacket(premadeNPCs: Map<ResourceLocation, PremadeNPC>) : DataRegistrySyncPacket<Map.Entry<ResourceLocation, PremadeNPC>, PremadeNPCRegistrySyncPacket>(premadeNPCs.entries) {
    companion object {
        val ID = cobblemonResource("premade_npcs_sync")
        fun decode(buffer: RegistryFriendlyByteBuf) = PremadeNPCRegistrySyncPacket(emptyMap()).apply { decodeBuffer(buffer) }
    }

    override val id = ID
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: Map.Entry<ResourceLocation, PremadeNPC>) {
        buffer.writeResourceLocation(entry.key)
        buffer.writeText(entry.value.displayName)
        buffer.writeBoolean(entry.value.editable)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): Map.Entry<ResourceLocation, PremadeNPC> {
        val premadeNPC = PremadeNPC()
        premadeNPC.id = buffer.readResourceLocation()
        premadeNPC.displayName = buffer.readText().copy()
        premadeNPC.editable = buffer.readBoolean()
        return object : Map.Entry<ResourceLocation, PremadeNPC> {
            override val key: ResourceLocation = premadeNPC.id
            override val value: PremadeNPC = premadeNPC
        }
    }

    override fun synchronizeDecoded(entries: Collection<Map.Entry<ResourceLocation, PremadeNPC>>) {
        PremadeNPCs.worldSavedPremades.clear()
        entries.forEach { (id, premadeNPC) -> PremadeNPCs.datapackedPremades[id] = premadeNPC }
    }
}