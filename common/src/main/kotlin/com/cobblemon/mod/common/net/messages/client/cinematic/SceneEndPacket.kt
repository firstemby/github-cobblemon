/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.cinematic

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class SceneEndPacket(
    val sceneId: UUID
) : NetworkPacket<SceneEndPacket> {

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sceneId)
    }

    companion object {
        val ID = cobblemonResource("scene_end")

        fun decode(buffer: RegistryFriendlyByteBuf): SceneEndPacket {
            return SceneEndPacket(buffer.readUUID())
        }
    }
}
