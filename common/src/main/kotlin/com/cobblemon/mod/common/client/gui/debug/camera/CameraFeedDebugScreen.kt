/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.debug.camera

import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import java.lang.Math.floorMod
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Debug screen for visualizing active camera feeds.
 */
class CameraFeedDebugScreen : Screen(Component.literal("Camera Feeds")) {

    private var cameraIds: List<UUID> = emptyList()
    private var selectedCameraId: UUID? = null

    private var prevButton: Button? = null
    private var nextButton: Button? = null

    override fun init() {
        refreshCameraIds()

        val buttonWidth = 100
        val bottomY = height - 48

        val refreshButton = Button.builder(Component.literal("Refresh")) {
            refreshCameraIds()
        }.pos(width / 2 - buttonWidth / 2, bottomY - 24).size(buttonWidth, 20).build()

        val prev = Button.builder(Component.literal("Prev")) {
            cycleSelection(-1)
        }.pos(width / 2 - buttonWidth - 8, bottomY).size(buttonWidth, 20).build()

        val next = Button.builder(Component.literal("Next")) {
            cycleSelection(1)
        }.pos(width / 2 + 8, bottomY).size(buttonWidth, 20).build()

        addRenderableWidget(refreshButton)
        addRenderableWidget(prev)
        addRenderableWidget(next)

        prevButton = prev
        nextButton = next

        updateButtonStates()
    }

    override fun tick() {
        super.tick()
        val ids = CameraFeedHandler.getActiveCameraIds().toList().sortedBy { it.toString() }
        if (ids != cameraIds) {
            cameraIds = ids
            if (cameraIds.isEmpty()) {
                selectedCameraId = null
            } else if (selectedCameraId !in cameraIds) {
                selectedCameraId = cameraIds.first()
            }
            updateButtonStates()
        }
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(context, mouseX, mouseY, partialTick)
        drawFeed(context)
        drawHints(context)
    }

    override fun isPauseScreen(): Boolean = false

    private fun drawFeed(context: GuiGraphics) {
        val font = minecraft?.font!!
        context.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF.toInt())

        if (cameraIds.isEmpty() || selectedCameraId == null) {
            context.drawCenteredString(
                font,
                Component.literal("No active camera feeds"),
                width / 2,
                height / 2 - 10,
                0xFFAAAAAA.toInt()
            )
            context.drawCenteredString(
                font,
                Component.literal("Trigger a camera or wait for feeds to start."),
                width / 2,
                height / 2 + 6,
                0xFF777777.toInt()
            )
            return
        }

        val selectedId = selectedCameraId!!
        val renderTarget = CameraFeedHandler.getRenderTarget(selectedId)
        val feedContext = CameraFeedHandler.getFeedContext(selectedId)

        context.drawCenteredString(
            font,
            Component.literal("Camera: $selectedId"),
            width / 2,
            28,
            0xFFFFFFFF.toInt()
        )

        feedContext?.let { ctx ->
            val positionText = "Pos: %.2f %.2f %.2f  Yaw: %.1f  Pitch: %.1f  FOV: %.1f".format(
                ctx.position.x,
                ctx.position.y,
                ctx.position.z,
                ctx.yaw,
                ctx.pitch,
                ctx.fov
            )
            context.drawCenteredString(font, Component.literal(positionText), width / 2, 42, 0xFFA0A0A0.toInt())

            val renderInfo = "Resolution: ${ctx.resolution}  Render Distance: ${ctx.renderDistance}"
            context.drawCenteredString(font, Component.literal(renderInfo), width / 2, 54, 0xFFA0A0A0.toInt())
        }

        val isLive = CameraFeedHandler.wasRecentlyCaptured(selectedId, 0.75)
        val statusColour = if (isLive) 0xFF55FF55.toInt() else 0xFFFFAA00.toInt()
        val statusText = if (isLive) "Status: capturing" else "Status: waiting for next frame"
        context.drawCenteredString(font, Component.literal(statusText), width / 2, 66, statusColour)

        val availableWidth = width - 60
        val availableHeight = height - 160
        val feedSize = max(64, min(availableWidth, availableHeight))
        val x = (width - feedSize) / 2
        val y = 78

        context.fill(x - 2, y - 2, x + feedSize + 2, y + feedSize + 2, 0xFF101010.toInt())

        if (renderTarget == null) {
            context.drawCenteredString(
                font,
                Component.literal("Feed render target unavailable"),
                width / 2,
                y + feedSize / 2,
                0xFFFF5555.toInt()
            )
            return
        }

        // Use glBlitFramebuffer to bypass Iris shader fog/depth injection
        val mc = Minecraft.getInstance()
        val scale = mc.window.guiScale
        val pixelX = (x * scale).toInt()
        val pixelY = ((height - y - feedSize) * scale).toInt() // flip Y for OpenGL
        val pixelW = (feedSize * scale).toInt()
        val pixelH = (feedSize * scale).toInt()

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, renderTarget.frameBufferId)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mc.mainRenderTarget.frameBufferId)
        GL30.glBlitFramebuffer(
            0, 0, renderTarget.width, renderTarget.height,
            pixelX, pixelY, pixelX + pixelW, pixelY + pixelH,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        )
        mc.mainRenderTarget.bindWrite(true)
    }

    private fun drawHints(context: GuiGraphics) {
        val font = minecraft?.font!!
        context.drawCenteredString(
            font,
            Component.literal("F8 to close • Shift + F8 toggles overlay"),
            width / 2,
            height - 20,
            0xFFAAAAAA.toInt()
        )
        if (cameraIds.size > 1) {
            context.drawCenteredString(
                font,
                Component.literal("Use Prev/Next to switch between active cameras"),
                width / 2,
                height - 32,
                0xFFAAAAAA.toInt()
            )
        }
    }

    private fun refreshCameraIds() {
        val ids = CameraFeedHandler.getActiveCameraIds().toList().sortedBy { it.toString() }
        cameraIds = ids
        selectedCameraId = if (ids.isEmpty()) null else ids.first()
        updateButtonStates()
    }

    private fun cycleSelection(delta: Int) {
        if (cameraIds.isEmpty()) {
            return
        }

        val currentIndex = cameraIds.indexOf(selectedCameraId).takeIf { it >= 0 } ?: 0
        val newIndex = floorMod(currentIndex + delta, cameraIds.size)
        selectedCameraId = cameraIds[newIndex]
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val hasMultiple = cameraIds.size > 1
        prevButton?.active = hasMultiple
        nextButton?.active = hasMultiple
    }

    private val minecraft: Minecraft
        get() = Minecraft.getInstance()
}

