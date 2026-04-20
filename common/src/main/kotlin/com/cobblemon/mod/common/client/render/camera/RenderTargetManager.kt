/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import net.minecraft.client.Minecraft
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Manages render targets for camera feeds. */
object RenderTargetManager {

    private val renderTargets: MutableMap<UUID, TextureTarget> = ConcurrentHashMap()

    private const val DEFAULT_WIDTH = 1280
    private const val DEFAULT_HEIGHT = 720

    /** Returns an existing render target or creates a new one. */
    @JvmStatic
    fun getOrCreate(cameraId: UUID, resolution: Int = DEFAULT_WIDTH): RenderTarget {
        val width = resolution.coerceIn(64, 2048)
        val height = (width * 9 / 16).coerceAtLeast(64) // 16:9 aspect ratio
        val existing = renderTargets[cameraId]
        if (existing != null && existing.width == width && existing.height == height) {
            return existing
        }

        if (existing != null) {
            existing.destroyBuffers()
            renderTargets.remove(cameraId)
        }

        val target = TextureTarget(width, height, true, Minecraft.ON_OSX)
        renderTargets[cameraId] = target
        return target
    }

    /** Retrieves the texture id for the camera feed, if available. */
    @JvmStatic
    fun getTextureId(cameraId: UUID): Int? = renderTargets[cameraId]?.colorTextureId

    /** Removes a render target and frees GPU resources. */
    @JvmStatic
    fun remove(cameraId: UUID) {
        renderTargets.remove(cameraId)?.destroyBuffers()
    }

    /** Destroys all render targets. Called on world unload. */
    @JvmStatic
    fun clearAll() {
        renderTargets.values.forEach { it.destroyBuffers() }
        renderTargets.clear()
    }
}

