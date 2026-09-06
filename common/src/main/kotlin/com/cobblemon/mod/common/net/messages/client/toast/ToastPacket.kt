/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.toast

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class ToastPacket(
    val title: Component,
    val description: Component,
    val icons: List<ItemStack>,
    val frameTexture: ResourceLocation,
    val progress: Float,
    val progressColor: Int,
    val uuid: UUID,
    val behaviour: Behaviour,
    val durationMs: Long? = null
) : NetworkPacket<ToastPacket> {

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeText(this.title)
        buffer.writeText(this.description)
        buffer.writeCollection(this.icons) { _, icon ->
            buffer.writeItemStack(icon)
        }
        buffer.writeIdentifier(this.frameTexture)
        buffer.writeFloat(this.progress)
        buffer.writeInt(this.progressColor)
        buffer.writeUUID(this.uuid)
        buffer.writeEnumConstant(this.behaviour)
        if (durationMs != null) buffer.writeLong(durationMs)
    }

    companion object {

        val ID = cobblemonResource("toast")

        fun decode(buffer: RegistryFriendlyByteBuf): ToastPacket = ToastPacket(
            buffer.readText(),
            buffer.readText(),
            buffer.readList { _ -> buffer.readItemStack() },
            buffer.readIdentifier(),
            buffer.readFloat(),
            buffer.readInt(),
            buffer.readUUID(),
            buffer.readEnumConstant(Behaviour::class.java),
            durationMs = if (buffer.readableBytes() >= 8) buffer.readLong() else null
        )

    }

    enum class Behaviour {
        SHOW_OR_UPDATE,
        HIDE
    }

}