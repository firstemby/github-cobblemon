/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3

object CameraChunkVisibilityManager {
    @Volatile
    private var activeCameraPosition: Vec3? = null
    private const val CAMERA_RENDER_DISTANCE = 16

    @JvmStatic
    fun updateCameraPosition(position: Vec3) {
        val previousPos = activeCameraPosition
        activeCameraPosition = position
        val cameraChunkX = SectionPos.posToSectionCoord(position.x)
        val cameraChunkZ = SectionPos.posToSectionCoord(position.z)

        if (previousPos == null ||
            SectionPos.posToSectionCoord(previousPos.x) != cameraChunkX ||
            SectionPos.posToSectionCoord(previousPos.z) != cameraChunkZ) {
            Cobblemon.LOGGER.debug(
                "[CobblemonCamera] Camera moved to chunk ($cameraChunkX, $cameraChunkZ) at position $position"
            )
        }
    }

    @JvmStatic
    fun clearCameraPosition() {
        activeCameraPosition = null
    }

    @JvmStatic
    fun shouldKeepChunk(chunkPos: ChunkPos, renderDistance: Int): Boolean {
        val cameraPos = activeCameraPosition ?: return false
        val cameraChunk = ChunkPos(
            SectionPos.posToSectionCoord(cameraPos.x),
            SectionPos.posToSectionCoord(cameraPos.z)
        )
        val radius = maxOf(CAMERA_RENDER_DISTANCE, renderDistance) + 1
        return chunkPos.getChessboardDistance(cameraChunk) <= radius
    }

}
