/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.molangui

import com.cobblemon.mod.common.api.molangui.GuiMode
import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * S2C packet to open a Molang UI on the client.
 */
class MolangUiOpenPacket(
    val sessionId: UUID,
    val guiId: ResourceLocation,
    val mode: GuiMode,
    val args: Map<String, String>,
    val blocksInput: Boolean,
    val exclusive: Boolean,
    val hasContainer: Boolean = false,
    val slotLayouts: Map<String, SlotLayout> = emptyMap()
) : NetworkPacket<MolangUiOpenPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sessionId)
        buffer.writeIdentifier(guiId)
        buffer.writeEnumConstant(mode)
        buffer.writeMap(args, { buf, key -> buf.writeString(key) }, { buf, value -> buf.writeString(value) })
        buffer.writeBoolean(blocksInput)
        buffer.writeBoolean(exclusive)
        buffer.writeBoolean(hasContainer)
        if (hasContainer) {
            buffer.writeVarInt(slotLayouts.size)
            for ((name, layout) in slotLayouts) {
                buffer.writeString(name)
                buffer.writeVarInt(layout.offsetX)
                buffer.writeVarInt(layout.offsetY)
                buffer.writeVarInt(layout.columns)
                buffer.writeVarInt(layout.rows)
                buffer.writeVarInt(layout.slotWidth)
                buffer.writeVarInt(layout.slotHeight)
                buffer.writeVarInt(layout.spacingX)
                buffer.writeVarInt(layout.spacingY)
            }
        }
    }

    companion object {
        val ID = cobblemonResource("molang_ui_open")

        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiOpenPacket {
            val sessionId = buffer.readUUID()
            val guiId = buffer.readIdentifier()
            val mode = buffer.readEnumConstant(GuiMode::class.java)
            val args = buffer.readMap({ buf -> buf.readString() }, { buf -> buf.readString() })
            val blocksInput = buffer.readBoolean()
            val exclusive = buffer.readBoolean()
            val hasContainer = buffer.readBoolean()
            val slotLayouts = if (hasContainer) {
                val count = buffer.readVarInt()
                (0 until count).associate {
                    buffer.readString() to SlotLayout(
                        offsetX = buffer.readVarInt(), offsetY = buffer.readVarInt(),
                        columns = buffer.readVarInt(), rows = buffer.readVarInt(),
                        slotWidth = buffer.readVarInt(), slotHeight = buffer.readVarInt(),
                        spacingX = buffer.readVarInt(), spacingY = buffer.readVarInt()
                    )
                }
            } else emptyMap()
            return MolangUiOpenPacket(sessionId, guiId, mode, args, blocksInput, exclusive, hasContainer, slotLayouts)
        }
    }
}
