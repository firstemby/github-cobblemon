/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.gui.scripted.GuiMode
import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiDefinition
import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiParameter
import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiRegistry
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * S2C packet to sync all scripted GUI definitions to the client.
 * Uses the DataRegistrySyncPacket pattern.
 */
class ScriptedGuiSyncPacket(
    entries: Collection<Map.Entry<ResourceLocation, ScriptedGuiDefinition>>
) : DataRegistrySyncPacket<Map.Entry<ResourceLocation, ScriptedGuiDefinition>, ScriptedGuiSyncPacket>(entries) {

    companion object {
        val ID = cobblemonResource("scripted_gui_sync")

        fun decode(buffer: RegistryFriendlyByteBuf): ScriptedGuiSyncPacket {
            return ScriptedGuiSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
        }
    }

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: Map.Entry<ResourceLocation, ScriptedGuiDefinition>) {
        val def = entry.value
        buffer.writeIdentifier(def.guiId)
        buffer.writeString(def.mode.name)
        buffer.writeBoolean(def.blocksInput)
        buffer.writeBoolean(def.exclusive)
        buffer.writeString(def.scriptSource)

        // Write parameters
        buffer.writeMap(
            def.parameters,
            { buf, key -> buf.writeString(key) },
            { buf, param ->
                buf.writeString(param.type)
                buf.writeNullable(param.default) { b, v -> b.writeString(v) }
            }
        )
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): Map.Entry<ResourceLocation, ScriptedGuiDefinition> {
        val guiId = buffer.readIdentifier()
        val mode = GuiMode.fromString(buffer.readString())
        val blocksInput = buffer.readBoolean()
        val exclusive = buffer.readBoolean()
        val scriptSource = buffer.readString()

        val parameters = buffer.readMap(
            { buf -> buf.readString() },
            { buf ->
                val type = buf.readString()
                val default = buf.readNullable { b -> b.readString() }
                ScriptedGuiParameter(type, default)
            }
        )

        val definition = ScriptedGuiDefinition(guiId, mode, parameters, blocksInput, exclusive, scriptSource)

        return object : Map.Entry<ResourceLocation, ScriptedGuiDefinition> {
            override val key = guiId
            override val value = definition
        }
    }

    override fun synchronizeDecoded(entries: Collection<Map.Entry<ResourceLocation, ScriptedGuiDefinition>>) {
        ScriptedGuiRegistry.clientDefinitions.clear()
        ScriptedGuiRegistry.clientDefinitions.putAll(entries.map { it.key to it.value })
    }
}
