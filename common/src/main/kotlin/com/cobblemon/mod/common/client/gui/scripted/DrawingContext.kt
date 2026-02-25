/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted

import com.cobblemon.mod.common.client.render.camera.CameraTextureProvider
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.util.UUID

/** Safely converts a GraalVM [Value] to Int, handling JS doubles that don't fit exactly. */
private fun Value.safeInt(): Int = if (fitsInInt()) asInt() else asDouble().toInt()

/** Safely converts a GraalVM [Value] to Long, handling JS doubles that don't fit exactly. */
private fun Value.safeLong(): Long = if (fitsInLong()) asLong() else asDouble().toLong()

/**
 * Bridges JavaScript draw calls to Minecraft's GuiGraphics.
 *
 * Exposes drawing primitives (fill, drawString, drawTexture, etc.),
 * screen dimensions, mouse position, and action sending to JS scripts
 * via a [ProxyObject] that can be passed into the sandboxed GraalJS context.
 *
 * When [guiGraphics] is null (e.g., in event handlers), drawing functions are no-ops
 * but state, sendAction, and dimensions still work.
 */
class DrawingContext(
    private val guiGraphics: GuiGraphics?,
    private val sessionId: UUID,
    private val mouseX: Int,
    private val mouseY: Int,
    val pendingActions: MutableList<PendingAction> = mutableListOf()
) {
    data class PendingAction(val action: String, val data: Map<String, String>)

    private var closeRequested = false

    fun isCloseRequested(): Boolean = closeRequested

    /** Draws a textured quad using a raw OpenGL texture ID (for camera feeds/compositors). */
    private fun drawRawTextureQuad(gfx: GuiGraphics, textureId: Int, x: Int, y: Int, width: Int, height: Int) {
        val matrix = gfx.pose().last().pose()
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderTexture(0, textureId)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        val x1 = x.toFloat()
        val y1 = y.toFloat()
        val x2 = (x + width).toFloat()
        val y2 = (y + height).toFloat()

        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        // UV-flip vertically for OpenGL texture orientation
        buf.addVertex(matrix, x1, y1, 0f).setUv(0f, 1f)
        buf.addVertex(matrix, x1, y2, 0f).setUv(0f, 0f)
        buf.addVertex(matrix, x2, y2, 0f).setUv(1f, 0f)
        buf.addVertex(matrix, x2, y1, 0f).setUv(1f, 1f)
        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    fun buildJsObject(state: Value): ProxyObject {
        val mc = Minecraft.getInstance()
        val window = mc.window

        val members = mutableMapOf<String, Any?>(
            "screenWidth" to window.guiScaledWidth,
            "screenHeight" to window.guiScaledHeight,
            "mouseX" to mouseX,
            "mouseY" to mouseY,
            "state" to state,
            "fontHeight" to mc.font.lineHeight,

            "fill" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val x1 = args[0].safeInt()
                val y1 = args[1].safeInt()
                val x2 = args[2].safeInt()
                val y2 = args[3].safeInt()
                val color = args[4].safeLong().toInt()
                gfx.fill(x1, y1, x2, y2, color)
                null
            },

            "drawString" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val text = args[0].asString()
                val x = args[1].safeInt()
                val y = args[2].safeInt()
                val color = if (args.size > 3 && !args[3].isNull) args[3].safeLong().toInt() else 0xFFFFFF
                gfx.drawString(mc.font, text, x, y, color)
                null
            },

            "drawCenteredString" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val text = args[0].asString()
                val x = args[1].safeInt()
                val y = args[2].safeInt()
                val color = if (args.size > 3 && !args[3].isNull) args[3].safeLong().toInt() else 0xFFFFFF
                gfx.drawCenteredString(mc.font, text, x, y, color)
                null
            },

            "drawTexture" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val textureId = args[0].asString()
                val x = args[1].safeInt()
                val y = args[2].safeInt()
                val width = args[3].safeInt()
                val height = args[4].safeInt()
                val u = if (args.size > 5 && !args[5].isNull) args[5].safeInt() else 0
                val v = if (args.size > 6 && !args[6].isNull) args[6].safeInt() else 0
                val textureWidth = if (args.size > 7 && !args[7].isNull) args[7].safeInt() else width
                val textureHeight = if (args.size > 8 && !args[8].isNull) args[8].safeInt() else height
                val resourceLocation = ResourceLocation.tryParse(textureId) ?: return@ProxyExecutable null
                gfx.blit(resourceLocation, x, y, u.toFloat(), v.toFloat(), width, height, textureWidth, textureHeight)
                null
            },

            "pushPose" to ProxyExecutable {
                guiGraphics?.pose()?.pushPose()
                null
            },

            "popPose" to ProxyExecutable {
                guiGraphics?.pose()?.popPose()
                null
            },

            "translate" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val x = args[0].asDouble().toFloat()
                val y = args[1].asDouble().toFloat()
                val z = if (args.size > 2 && !args[2].isNull) args[2].asDouble().toFloat() else 0f
                gfx.pose().translate(x, y, z)
                null
            },

            "scale" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val sx = args[0].asDouble().toFloat()
                val sy = args[1].asDouble().toFloat()
                val sz = if (args.size > 2 && !args[2].isNull) args[2].asDouble().toFloat() else 1f
                gfx.pose().scale(sx, sy, sz)
                null
            },

            "enableScissor" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val x1 = args[0].safeInt()
                val y1 = args[1].safeInt()
                val x2 = args[2].safeInt()
                val y2 = args[3].safeInt()
                gfx.enableScissor(x1, y1, x2, y2)
                null
            },

            "disableScissor" to ProxyExecutable {
                guiGraphics?.disableScissor()
                null
            },

            "sendAction" to ProxyExecutable { args ->
                val action = args[0].asString()
                val data = mutableMapOf<String, String>()
                if (args.size > 1 && !args[1].isNull && args[1].hasMembers()) {
                    val dataObj = args[1]
                    for (key in dataObj.memberKeys) {
                        data[key] = dataObj.getMember(key).asString()
                    }
                }
                pendingActions.add(PendingAction(action, data))
                null
            },

            "close" to ProxyExecutable {
                closeRequested = true
                null
            },

            "stringWidth" to ProxyExecutable { args ->
                mc.font.width(args[0].asString())
            },

            "drawCameraFeed" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val cameraIdStr = args[0].asString()
                val x = args[1].safeInt()
                val y = args[2].safeInt()
                val width = args[3].safeInt()
                val height = args[4].safeInt()
                val cameraUUID = try { UUID.fromString(cameraIdStr) } catch (_: Exception) { return@ProxyExecutable null }
                val textureId = CameraTextureProvider.getFeedTextureId(cameraUUID)
                if (textureId != -1) {
                    drawRawTextureQuad(gfx, textureId, x, y, width, height)
                }
                null
            },

            "drawCompositorOutput" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val compositorIdStr = args[0].asString()
                val x = args[1].safeInt()
                val y = args[2].safeInt()
                val width = args[3].safeInt()
                val height = args[4].safeInt()
                val compositorUUID = try { UUID.fromString(compositorIdStr) } catch (_: Exception) { return@ProxyExecutable null }
                val textureId = CameraTextureProvider.getCompositorTextureId(compositorUUID)
                if (textureId != -1) {
                    drawRawTextureQuad(gfx, textureId, x, y, width, height)
                }
                null
            }
        )

        return object : ProxyObject {
            override fun getMember(key: String): Any? = members[key]
            override fun getMemberKeys(): Any = members.keys
            override fun hasMember(key: String): Boolean = key in members
            override fun putMember(key: String, value: Value?) { members[key] = value }
        }
    }
}
