/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.block

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.net.serverhandling.block.TMMachineTeachMoveHandler
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Handled by [TMMachineTeachMoveHandler].
 */
class TMMachineTeachMovePacket(val uuid: UUID, val heldStack: ItemStack, val moveTemplate: MoveTemplate?, val isParty: Boolean = true) : NetworkPacket<TMMachineTeachMovePacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(uuid)
        buffer.writeNullable(moveTemplate) { _, v -> buffer.writeString(v.name) }
        buffer.writeBoolean(isParty)
        buffer.writeItemStack(heldStack)
    }

    companion object {
        val ID = cobblemonResource("teach_move")
        fun decode(buffer: RegistryFriendlyByteBuf): TMMachineTeachMovePacket {
            val uuid = buffer.readUUID()
            val moveName = buffer.readNullable { buffer.readString() }
            val moveTemplate = if (moveName == null) null else Moves.getByName(moveName)
            val isParty = buffer.readBoolean()
            val heldStack = buffer.readItemStack()
            return TMMachineTeachMovePacket(uuid, heldStack, moveTemplate, isParty)
        }
    }
}
