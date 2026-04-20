/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.client.Minecraft
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import java.util.UUID

object CameraChunkVisibilityManager {
    @Volatile
    private var activeCameraPosition: Vec3? = null
    @Volatile
    private var activeCameraId: UUID? = null
    @Volatile
    private var cameraRenderDistance = 16
    @Volatile
    private var lastViewAreaResetPosition: Vec3? = null

    @JvmStatic
    fun updateCameraPosition(cameraId: UUID, position: Vec3) {
        val previousPos = activeCameraPosition
        activeCameraId = cameraId
        activeCameraPosition = position
        val cameraChunkX = SectionPos.posToSectionCoord(position.x)
        val cameraChunkZ = SectionPos.posToSectionCoord(position.z)
        val newChunkPos = ChunkPos(cameraChunkX, cameraChunkZ)
        CameraClientChunkCacheExtension.setCameraCenter(newChunkPos)

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
        activeCameraId = null
        activeCameraPosition = null
        CameraClientChunkCacheExtension.setCameraCenter(null)
    }

    @JvmStatic
    fun shouldRenderChunk(chunkPos: ChunkPos): Boolean {
        return shouldKeepChunk(chunkPos, cameraRenderDistance)
    }

    @JvmStatic
    fun shouldKeepChunk(chunkPos: ChunkPos, renderDistance: Int): Boolean {
        val cameraPos = activeCameraPosition ?: return false
        val cameraChunk = ChunkPos(
            SectionPos.posToSectionCoord(cameraPos.x),
            SectionPos.posToSectionCoord(cameraPos.z)
        )
        val radius = maxOf(cameraRenderDistance, renderDistance) + 1
        return chunkPos.getChessboardDistance(cameraChunk) <= radius
    }

    @JvmStatic
    fun isSectionInCameraView(sectionPos: SectionPos): Boolean {
        val cameraPos = activeCameraPosition ?: return false

        val chunkPos = ChunkPos(sectionPos.x(), sectionPos.z())
        if (!shouldRenderChunk(chunkPos)) return false

        val cameraSectionY = SectionPos.posToSectionCoord(cameraPos.y)
        val dy = Math.abs(sectionPos.y() - cameraSectionY)

        return dy <= 8
    }

    @JvmStatic
    fun getEffectiveRenderCenter(): Vec3 {
        val mc = Minecraft.getInstance()
        val player = mc.player

        if (player == null) {
            return activeCameraPosition ?: Vec3.ZERO
        }

        return player.position()
    }

    @JvmStatic
    fun isActive(): Boolean = activeCameraPosition != null

    @JvmStatic
    fun getCameraRenderDistance(): Int = cameraRenderDistance

    @JvmStatic
    fun setCameraRenderDistance(distance: Int) {
        cameraRenderDistance = distance.coerceIn(2, 32)
    }

    @JvmStatic
    fun getActiveCameraPosition(): Vec3? = activeCameraPosition
}
