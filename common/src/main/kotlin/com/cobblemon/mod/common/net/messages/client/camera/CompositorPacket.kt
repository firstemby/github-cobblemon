/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.camera

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorLayout
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorLayoutSerializer
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * S2C packet to create, update, or destroy a camera compositor on the client.
 *
 * - [CREATE]: Sends all fields. Client creates a new compositor with the given layout.
 * - [UPDATE_LAYOUT]: Sends compositorId + new layout. Client updates an existing compositor.
 * - [DESTROY]: Sends compositorId only. Client disposes the compositor.
 */
class CompositorPacket private constructor(
    val action: Action,
    val compositorId: UUID,
    val layout: CompositorLayout? = null,
    val fullscreen: Boolean = false,
    val width: Int = 0,
    val height: Int = 0
) : NetworkPacket<CompositorPacket> {

    enum class Action { CREATE, UPDATE_LAYOUT, DESTROY }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(action)
        buffer.writeUUID(compositorId)
        when (action) {
            Action.CREATE -> {
                CompositorLayoutSerializer.encode(layout!!, buffer)
                buffer.writeBoolean(fullscreen)
                buffer.writeInt(width)
                buffer.writeInt(height)
            }
            Action.UPDATE_LAYOUT -> {
                CompositorLayoutSerializer.encode(layout!!, buffer)
            }
            Action.DESTROY -> {}
        }
    }

    companion object {
        val ID = cobblemonResource("compositor")

        fun create(
            compositorId: UUID,
            layout: CompositorLayout,
            fullscreen: Boolean,
            width: Int,
            height: Int
        ) = CompositorPacket(Action.CREATE, compositorId, layout, fullscreen, width, height)

        fun updateLayout(
            compositorId: UUID,
            layout: CompositorLayout
        ) = CompositorPacket(Action.UPDATE_LAYOUT, compositorId, layout)

        fun destroy(compositorId: UUID) = CompositorPacket(Action.DESTROY, compositorId)

        fun decode(buffer: RegistryFriendlyByteBuf): CompositorPacket {
            val action = buffer.readEnum(Action::class.java)
            val compositorId = buffer.readUUID()
            return when (action) {
                Action.CREATE -> CompositorPacket(
                    action = Action.CREATE,
                    compositorId = compositorId,
                    layout = CompositorLayoutSerializer.decode(buffer),
                    fullscreen = buffer.readBoolean(),
                    width = buffer.readInt(),
                    height = buffer.readInt()
                )
                Action.UPDATE_LAYOUT -> CompositorPacket(
                    action = Action.UPDATE_LAYOUT,
                    compositorId = compositorId,
                    layout = CompositorLayoutSerializer.decode(buffer)
                )
                Action.DESTROY -> CompositorPacket(
                    action = Action.DESTROY,
                    compositorId = compositorId
                )
            }
        }
    }
}
