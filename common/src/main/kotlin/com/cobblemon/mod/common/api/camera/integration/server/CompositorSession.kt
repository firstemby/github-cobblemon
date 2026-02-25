/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.integration.server

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorLayout
import com.cobblemon.mod.common.net.messages.client.camera.CompositorPacket
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class CompositorSession(
    val compositorId: UUID = UUID.randomUUID(),
    private val viewers: () -> Collection<ServerPlayer>,
    val fullscreen: Boolean = true,
    val width: Int = 1920,
    val height: Int = 1080
) {
    fun sendCreate(layout: CompositorLayout) {
        val packet = CompositorPacket.create(compositorId, layout, fullscreen, width, height)
        viewers().forEach { it.sendPacket(packet) }
    }

    fun sendLayoutUpdate(layout: CompositorLayout) {
        val packet = CompositorPacket.updateLayout(compositorId, layout)
        viewers().forEach { it.sendPacket(packet) }
    }

    fun sendDestroy() {
        val packet = CompositorPacket.destroy(compositorId)
        viewers().forEach { it.sendPacket(packet) }
    }
}
