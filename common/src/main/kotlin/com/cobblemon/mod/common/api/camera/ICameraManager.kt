/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.MoValue
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.function.Function

interface ICameraManager {
    fun startCamera(
        timelineId: String,
        context: Map<String, MoValue> = emptyMap(),
        viewers: List<ServerPlayer> = emptyList(),
        origin: Vec3 = Vec3.ZERO,
        queryFunctions: Map<String, Function<MoParams, Any>> = emptyMap()
    ): UUID?

    fun addViewerToCamera(player: ServerPlayer, cameraId: UUID): Boolean
    fun stopCamera(cameraId: UUID)
    fun stopCameraForPlayer(player: ServerPlayer)
    fun getPlayerCamera(player: ServerPlayer): ICameraController?
    fun getCamera(cameraId: UUID): ICameraController?
    fun tick()
    fun clear()
}
