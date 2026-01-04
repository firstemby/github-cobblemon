/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID

interface ICameraController {
    val id: UUID
    val origin: Vec3
    fun startTimeline(timelineId: String): Boolean
    fun tick()
    fun stop()
    fun isActive(): Boolean
    fun addViewer(player: ServerPlayer)
    fun removeViewer(player: ServerPlayer)
    fun setVariable(name: String, value: Double)
    fun setFocusOverride(position: Vec3?)
}
