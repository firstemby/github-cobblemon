/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.debug

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.ClientChunkEvent
import com.cobblemon.mod.common.platform.events.ClientPlayerEvent
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.world.level.ChunkPos
import org.joml.Matrix4f
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Renders wireframe boxes around the chunks currently loaded on the client.
 *
 * Intended for debugging chunk visibility and loading issues. Disabled by default and
 * can be toggled via a debug keybinding when debug keys are enabled.
 */
object ChunkBoundaryDebugRenderer {
    private val loadedChunks = LongOpenHashSet()
    private val chunkLock = Any()

    @Volatile
    private var enabled = false
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true

        PlatformEvents.CLIENT_CHUNK_LOAD.subscribe(Priority.HIGH, ::onChunkLoad)
        PlatformEvents.CLIENT_CHUNK_UNLOAD.subscribe(Priority.HIGH, ::onChunkUnload)
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.HIGH, ::onClientLogout)

        PlatformEvents.RENDER.subscribe { event ->
            if (!enabled || event.stage != RenderEvent.Stage.TRANSLUCENT) {
                return@subscribe
            }
            render(event)
        }
    }

    fun toggle(): Boolean {
        val newValue = !enabled
        setEnabled(newValue)
        return newValue
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun chunkCount(): Int = synchronized(chunkLock) { loadedChunks.size }

    private fun onChunkLoad(event: ClientChunkEvent.Load) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level != event.level) {
            return
        }

        synchronized(chunkLock) {
            loadedChunks.add(event.chunk.pos.toLong())
        }
    }

    private fun onChunkUnload(event: ClientChunkEvent.Unload) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level != event.level) {
            return
        }

        synchronized(chunkLock) {
            loadedChunks.remove(event.chunk.pos.toLong())
        }
    }

    private fun onClientLogout(@Suppress("UNUSED_PARAMETER") event: ClientPlayerEvent.Logout) {
        synchronized(chunkLock) {
            loadedChunks.clear()
        }
    }

    private fun render(event: RenderEvent) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level!!

        val cameraPosition = event.camera.position
        val positions = synchronized(chunkLock) { loadedChunks.toLongArray() }
        if (positions.isEmpty()) {
            return
        }

        val worldMinY = level.minBuildHeight.toDouble()
        val worldMaxY = (level.minBuildHeight + level.height).toDouble()
        val beltBase = floor(cameraPosition.y)
        val beltMinY = max(worldMinY, beltBase - 1.0)
        val beltMaxY = min(worldMaxY, beltMinY + 3.0)

        val poseStack = event.poseStack
        poseStack.pushPose()
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z)
        val matrix = poseStack.last().pose()

        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        RenderSystem.lineWidth(2.0f)

        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR)

        for (chunkPosLong in positions) {
            val chunkX = ChunkPos.getX(chunkPosLong)
            val chunkZ = ChunkPos.getZ(chunkPosLong)
            val minX = (chunkX shl 4).toDouble()
            val minZ = (chunkZ shl 4).toDouble()
            val maxX = minX + 16.0
            val maxZ = minZ + 16.0

            val centerX = minX + 8.0
            val centerZ = minZ + 8.0
            val dx = centerX - cameraPosition.x
            val dz = centerZ - cameraPosition.z
            val distance = sqrt(dx * dx + dz * dz)
            val normalized = min(distance / 160.0, 1.0)

            val red = (190 + 65 * normalized).toInt().coerceIn(0, 255)
            val green = (255 - 155 * normalized).toInt().coerceIn(0, 255)
            val blue = (200 - 120 * normalized).toInt().coerceIn(0, 255)
            val alpha = 220

            renderChunkBox(buffer, matrix, minX, beltMinY, minZ, maxX, beltMaxY, maxZ, red, green, blue, alpha)
        }

        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow())

        RenderSystem.disableBlend()
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.lineWidth(1.0f)

        poseStack.popPose()
    }

    private fun renderChunkBox(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val x0 = minX.toFloat()
        val y0 = minY.toFloat()
        val z0 = minZ.toFloat()
        val x1 = maxX.toFloat()
        val y1 = maxY.toFloat()
        val z1 = maxZ.toFloat()

        addEdge(buffer, matrix, x0, y0, z0, x1, y0, z0, red, green, blue, alpha)
        addEdge(buffer, matrix, x1, y0, z0, x1, y0, z1, red, green, blue, alpha)
        addEdge(buffer, matrix, x1, y0, z1, x0, y0, z1, red, green, blue, alpha)
        addEdge(buffer, matrix, x0, y0, z1, x0, y0, z0, red, green, blue, alpha)

        addEdge(buffer, matrix, x0, y1, z0, x1, y1, z0, red, green, blue, alpha)
        addEdge(buffer, matrix, x1, y1, z0, x1, y1, z1, red, green, blue, alpha)
        addEdge(buffer, matrix, x1, y1, z1, x0, y1, z1, red, green, blue, alpha)
        addEdge(buffer, matrix, x0, y1, z1, x0, y1, z0, red, green, blue, alpha)

        addEdge(buffer, matrix, x0, y0, z0, x0, y1, z0, red, green, blue, alpha)
        addEdge(buffer, matrix, x1, y0, z0, x1, y1, z0, red, green, blue, alpha)
        addEdge(buffer, matrix, x1, y0, z1, x1, y1, z1, red, green, blue, alpha)
        addEdge(buffer, matrix, x0, y0, z1, x0, y1, z1, red, green, blue, alpha)
    }

    private fun addEdge(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        buffer.addVertex(matrix, x0, y0, z0).setColor(red, green, blue, alpha)
        buffer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha)
    }

}

