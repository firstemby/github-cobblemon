/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.util.UUID

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
                val x1 = args[0].asInt()
                val y1 = args[1].asInt()
                val x2 = args[2].asInt()
                val y2 = args[3].asInt()
                val color = args[4].asLong().toInt()
                gfx.fill(x1, y1, x2, y2, color)
                null
            },

            "drawString" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val text = args[0].asString()
                val x = args[1].asInt()
                val y = args[2].asInt()
                val color = if (args.size > 3 && !args[3].isNull) args[3].asLong().toInt() else 0xFFFFFF
                gfx.drawString(mc.font, text, x, y, color)
                null
            },

            "drawCenteredString" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val text = args[0].asString()
                val x = args[1].asInt()
                val y = args[2].asInt()
                val color = if (args.size > 3 && !args[3].isNull) args[3].asLong().toInt() else 0xFFFFFF
                gfx.drawCenteredString(mc.font, text, x, y, color)
                null
            },

            "drawTexture" to ProxyExecutable { args ->
                val gfx = guiGraphics ?: return@ProxyExecutable null
                val textureId = args[0].asString()
                val x = args[1].asInt()
                val y = args[2].asInt()
                val width = args[3].asInt()
                val height = args[4].asInt()
                val u = if (args.size > 5 && !args[5].isNull) args[5].asInt() else 0
                val v = if (args.size > 6 && !args[6].isNull) args[6].asInt() else 0
                val textureWidth = if (args.size > 7 && !args[7].isNull) args[7].asInt() else width
                val textureHeight = if (args.size > 8 && !args[8].isNull) args[8].asInt() else height
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
                val x1 = args[0].asInt()
                val y1 = args[1].asInt()
                val x2 = args[2].asInt()
                val y2 = args[3].asInt()
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
