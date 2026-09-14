/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.cinematic.ClientSceneSessionManager
import com.cobblemon.mod.common.client.editor.cinematic.viewport.EditorViewportState
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneSerializer
import com.google.gson.GsonBuilder
import imgui.ImGui
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

object EditorDiagnostics {
    var showBounds = false
    private var captureFrames = -1
    private var captureAt = 0.0
    private val items = mutableListOf<Item>()
    private val panels = mutableListOf<Panel>()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float)
    data class Panel(val name: String, val bounds: Rect, val content: Rect)
    data class Item(val id: String, val panel: String, val bounds: Rect, val visible: Boolean, val horizontalOverflow: Boolean)

    fun requestCapture(delay: Double = 0.25) { captureFrames = 3; captureAt = ImGui.getTime() + delay }

    fun beginFrame() {
        items.clear()
        panels.clear()
    }

    fun panel(name: String) {
        val x = ImGui.getWindowPosX()
        val y = ImGui.getWindowPosY()
        panels += Panel(name, Rect(x, y, ImGui.getWindowWidth(), ImGui.getWindowHeight()), Rect(
            x + ImGui.getWindowContentRegionMinX(), y + ImGui.getWindowContentRegionMinY(),
            ImGui.getWindowContentRegionMaxX() - ImGui.getWindowContentRegionMinX(),
            ImGui.getWindowContentRegionMaxY() - ImGui.getWindowContentRegionMinY()
        ))
    }

    fun item(id: String, expandedSelection: Boolean = false) {
        val panel = panels.lastOrNull() ?: return
        val bounds = Rect(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), ImGui.getItemRectSizeX(), ImGui.getItemRectSizeY())
        val margin = if (expandedSelection) ImGui.getStyle().itemSpacingX / 2f + 1f else 1f
        val overflow = bounds.x < panel.content.x - margin || bounds.x + bounds.width > panel.content.x + panel.content.width + margin
        items += Item(id, panel.name, bounds, ImGui.isItemVisible(), overflow)
        if (showBounds && ImGui.isItemVisible()) {
            ImGui.getWindowDrawList().addRect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height,
                if (overflow) 0xFF4444FF.toInt() else 0xFF88CC44.toInt())
        }
    }

    fun afterRender() {
        if (captureFrames < 0) return
        if (captureFrames > 0) { captureFrames--; return }
        if (ImGui.getTime() < captureAt) return
        captureFrames = -1
        try {
            val directory = Minecraft.getInstance().gameDirectory.toPath().resolve("cobblemon/editor-captures")
            Files.createDirectories(directory)
            val name = "editor-${System.currentTimeMillis()}"
            val image = captureBackbuffer()
            ImageIO.write(image, "png", directory.resolve("$name.png").toFile())
            Files.writeString(directory.resolve("$name.json"), gson.toJson(mapOf(
                "coordinateSpace" to "imgui-display", "displayWidth" to ImGui.getIO().displaySizeX,
                "displayHeight" to ImGui.getIO().displaySizeY, "fontScale" to ImGui.getIO().fontGlobalScale,
                "framebufferWidth" to image.width, "framebufferHeight" to image.height,
                "scene" to SceneEditorOverlay.activeScene?.id, "beat" to SceneEditorOverlay.selection.getSelectedBeatId(),
                "sceneData" to SceneEditorOverlay.activeScene?.let(SceneSerializer::toJson),
                "time" to SceneEditorOverlay.currentPlayheadSeconds,
                "cues" to SceneEditorOverlay.activeScene?.beats?.get(SceneEditorOverlay.selection.getSelectedBeatId())?.cues,
                "cuts" to SceneEditorOverlay.activeScene?.beats?.get(SceneEditorOverlay.selection.getSelectedBeatId())?.cuts,
                "previewPaused" to SceneEditorOverlay.preview.paused,
                "previewError" to SceneEditorOverlay.preview.error,
                "computedRoute" to EditorRoutePreview.diagnostics(),
                "previewSession" to SceneEditorOverlay.preview.runningSceneInstanceIdOrNull()?.toString(),
                "previewFeeds" to SceneEditorOverlay.preview.runningSceneInstanceIdOrNull()?.let {
                    ClientSceneSessionManager.getSession(it)?.feeds?.mapValues { entry -> entry.value.latestTransform }
                },
                "selection" to SceneEditorOverlay.selection.describeSelection(),
                "viewport" to Rect(EditorViewportState.frameX.toFloat(), EditorViewportState.frameY.toFloat(),
                    EditorViewportState.frameWidth.toFloat(), EditorViewportState.frameHeight.toFloat()),
                "panels" to panels, "items" to items,
                "issues" to items.filter { it.horizontalOverflow && it.visible },
                "panelIssues" to panels.filter { panel ->
                    val bounds = panel.bounds
                    bounds.width <= 0 || bounds.height <= 0 || bounds.x < -1 || bounds.y < -1 ||
                        bounds.x + bounds.width > ImGui.getIO().displaySizeX + 1 ||
                        bounds.y + bounds.height > ImGui.getIO().displaySizeY + 1
                }
            )))
            Cobblemon.LOGGER.info("Editor capture saved to {}", directory.resolve(name))
        } catch (error: Exception) {
            Cobblemon.LOGGER.error("Could not capture cinematic editor", error)
        }
    }

    private fun captureBackbuffer(): BufferedImage = MemoryStack.stackPush().use { stack ->
        val widthBuffer = stack.mallocInt(1)
        val heightBuffer = stack.mallocInt(1)
        GLFW.glfwGetFramebufferSize(Minecraft.getInstance().window.window, widthBuffer, heightBuffer)
        val width = widthBuffer[0]
        val height = heightBuffer[0]
        require(width > 0 && height > 0)
        val pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4))
        val previousFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER)
        val previousAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT)
        val previousPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)
        val packOptions = listOf(GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS)
        val previousPackOptions = packOptions.map { GL11.glGetInteger(it) }
        try {
            // ImGui is composited into the backbuffer after the world target has been blitted.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0)
            GL11.glReadBuffer(GL11.GL_BACK)
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
            packOptions.forEach { GL11.glPixelStorei(it, 0) }
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
            BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { image ->
                val row = IntArray(width)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val offset = (y * width + x) * 4
                        row[x] = ((pixels[offset].toInt() and 255) shl 16) or
                            ((pixels[offset + 1].toInt() and 255) shl 8) or (pixels[offset + 2].toInt() and 255)
                    }
                    image.setRGB(0, height - 1 - y, width, 1, row, 0, width)
                }
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousAlignment)
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPackBuffer)
            packOptions.forEachIndexed { index, option -> GL11.glPixelStorei(option, previousPackOptions[index]) }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousFramebuffer)
            GL11.glReadBuffer(previousReadBuffer)
            MemoryUtil.memFree(pixels)
        }
    }
}
