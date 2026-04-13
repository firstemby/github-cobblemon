/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.event

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetDefinition
import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetInstance
import com.cobblemon.mod.common.net.messages.server.gui.ScriptedGuiActionPacket
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
            val packet = ScriptedGuiActionPacket(
                sessionId = sessionId,
                action = "${event.widgetId}:${event.type.id}",
                data = event.data
            )
            packet.sendToServer()
        }
    }

    fun hitTest(root: WidgetInstance, x: Float, y: Float): WidgetInstance? {
        for (child in root.children.asReversed()) {
            if (!child.visible) continue
            val hit = hitTest(child, x, y)
            if (hit != null) return hit
        }
        return if (root.containsPoint(x, y)) root else null
    }
}
