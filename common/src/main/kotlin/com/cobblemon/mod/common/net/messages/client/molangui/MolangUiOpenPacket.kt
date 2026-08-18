/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.net.messages.client.molangui

import com.cobblemon.mod.common.api.molangui.GuiMode
import com.cobblemon.mod.common.api.molangui.MolangUiPacketLimits
import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class MolangUiOpenPacket(
    val sessionId: UUID, val guiId: ResourceLocation, val contractVersion: String, val mode: GuiMode,
    val args: Map<String, String>, val blocksInput: Boolean, val exclusive: Boolean,
    val hasContainer: Boolean = false, val slotLayouts: Map<String, SlotLayout> = emptyMap()
) : NetworkPacket<MolangUiOpenPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        require(args.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS && slotLayouts.size <= MolangUiPacketLimits.MAX_CONTRACTS)
        buffer.writeUUID(sessionId)
        buffer.writeUtf(guiId.toString(), MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
        buffer.writeUtf(contractVersion, MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
        buffer.writeEnum(mode)
        buffer.writeVarInt(args.size)
        args.forEach { (key, value) -> buffer.writeUtf(key, MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH).writeUtf(value, MolangUiPacketLimits.MAX_VALUE_LENGTH) }
        buffer.writeBoolean(blocksInput).writeBoolean(exclusive).writeBoolean(hasContainer)
        if (hasContainer) {
            buffer.writeVarInt(slotLayouts.size)
            slotLayouts.forEach { (name, layout) ->
                buffer.writeUtf(name, MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
                listOf(layout.offsetX, layout.offsetY, layout.columns, layout.rows, layout.slotWidth, layout.slotHeight, layout.spacingX, layout.spacingY)
                    .forEach(buffer::writeVarInt)
            }
        }
    }
    companion object {
        val ID = cobblemonResource("molang_ui_open")
        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiOpenPacket {
            require(buffer.readableBytes() <= MolangUiPacketLimits.MAX_PACKET_BYTES)
            val sessionId = buffer.readUUID()
            val guiId = ResourceLocation.tryParse(buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)) ?: error("invalid UI id")
            val version = buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
            val mode = buffer.readEnum(GuiMode::class.java)
            val argCount = buffer.readVarInt()
            require(argCount in 0..MolangUiPacketLimits.MAX_ACTION_FIELDS)
            val args = linkedMapOf<String, String>()
            repeat(argCount) { args[buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)] = buffer.readUtf(MolangUiPacketLimits.MAX_VALUE_LENGTH) }
            val blocksInput = buffer.readBoolean()
            val exclusive = buffer.readBoolean()
            val hasContainer = buffer.readBoolean()
            val layouts = linkedMapOf<String, SlotLayout>()
            if (hasContainer) {
                val count = buffer.readVarInt()
                require(count in 0..MolangUiPacketLimits.MAX_CONTRACTS)
                repeat(count) {
                    val name = buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
                    layouts[name] = SlotLayout(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt())
                }
            }
            return MolangUiOpenPacket(sessionId, guiId, version, mode, args, blocksInput, exclusive, hasContainer, layouts)
        }
    }
}
