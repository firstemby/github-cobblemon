/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.CobblemonBrainConfigs
import com.cobblemon.mod.common.api.ai.BrainPreset
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class BrainPresetSyncPacket(entries: Map<ResourceLocation, BrainPreset>) : DataRegistrySyncPacket<Map.Entry<ResourceLocation, BrainPreset>, BrainPresetSyncPacket>(entries.entries.toList()) {
    companion object {
        val ID = cobblemonResource("brain_preset_sync")
        fun decode(buffer: RegistryFriendlyByteBuf): BrainPresetSyncPacket = BrainPresetSyncPacket(emptyMap()).apply { decodeBuffer(buffer) }
    }

    override val id = ID
    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): Map.Entry<ResourceLocation, BrainPreset>? {
        val identifier = buffer.readIdentifier()
        val brainPreset = BrainPreset(
            name = buffer.readText(),
            description = buffer.readText(),
            configurations = emptyList()
        )
        return object : Map.Entry<ResourceLocation, BrainPreset> {
            override val key = identifier
            override val value = brainPreset
        }
    }

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: Map.Entry<ResourceLocation, BrainPreset>) {
        buffer.writeIdentifier(entry.key)
        buffer.writeText(entry.value.name)
        buffer.writeText(entry.value.description)
    }

    override fun synchronizeDecoded(entries: Collection<Map.Entry<ResourceLocation, BrainPreset>>) {
        CobblemonBrainConfigs.presets.clear()
        CobblemonBrainConfigs.presets.putAll(entries.associate { it.key to it.value })
    }
}