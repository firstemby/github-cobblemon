/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.event

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiActionPacket
import com.cobblemon.mod.common.util.asExpressionLike
import java.util.UUID

object ClientEventProcessor {

    fun processEvent(
        event: WidgetEvent,
        widget: WidgetDefinition,
        runtime: MoLangRuntime,
        sessionId: UUID
    ) {
        // Run client-side handler first (instant visual feedback)
        val clientHandler = widget.clientEvents[event.type.clientId]
        if (clientHandler != null) {
            try {
                val expression = clientHandler.asExpressionLike()
                expression.resolve(runtime)
            } catch (e: Exception) {
                Cobblemon.LOGGER.warn("[ClientEventProcessor] Failed to execute client event '${event.type.clientId}' on widget '${event.widgetId}': ${e.message}")
            }
        }

        // Send server-side handler as action packet
        val serverHandler = widget.events[event.type.id]
        if (serverHandler != null) {
            val packet = MolangUiActionPacket(
                sessionId = sessionId,
                action = "${event.widgetId}:${event.type.id}",
                data = event.data
            )
            packet.sendToServer()
        }
    }

    fun hitTest(root: WidgetInstance, x: Float, y: Float): WidgetInstance? {
        val isScrollPanel = root.definition.type == WidgetType.SCROLL_PANEL
        if (isScrollPanel && !root.containsPoint(x, y)) return null

        for (child in root.children.asReversed()) {
            if (!child.visible) continue
            val testY = if (isScrollPanel) y - root.scrollOffset else y
            val hit = hitTest(child, x, testY)
            if (hit != null) return hit
        }
        return if (root.containsPoint(x, y)) root else null
    }
}

enum class WidgetEventType(val id: String) {
    CLICK("on_click"),
    CHANGE("on_change"),
    SUBMIT("on_submit"),
    HOVER_ENTER("on_hover_enter"),
    HOVER_EXIT("on_hover_exit"),
    SCROLL("on_scroll"),
    KEY("on_key");

    val clientId: String get() = "${id}_client"
}

data class WidgetEvent(
    val type: WidgetEventType,
    val widgetId: String,
    val data: Map<String, String> = emptyMap()
)
