/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

import com.cobblemon.mod.common.Cobblemon
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import net.minecraft.client.Minecraft
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RenderTargetManager {

    private val renderTargets: MutableMap<UUID, TextureTarget> = ConcurrentHashMap()

    private const val DEFAULT_WIDTH = 1280

    @JvmStatic
    fun getOrCreate(cameraId: UUID, resolution: Int = DEFAULT_WIDTH): RenderTarget {
        val width = resolution.coerceIn(64, 2048)
        val height = (width * 9 / 16).coerceAtLeast(64)
        val existing = renderTargets[cameraId]
        if (existing != null && existing.width == width && existing.height == height) {
            return existing
        }

        val target = TextureTarget(width, height, true, Minecraft.ON_OSX)
        renderTargets[cameraId] = target
        if (existing != null) runCatching { existing.destroyBuffers() }
            .onFailure { Cobblemon.LOGGER.warn("Could not dispose replaced camera feed target {}", cameraId, it) }
        return target
    }

    @JvmStatic
    fun remove(cameraId: UUID) {
        renderTargets.remove(cameraId)?.destroyBuffers()
    }
}
