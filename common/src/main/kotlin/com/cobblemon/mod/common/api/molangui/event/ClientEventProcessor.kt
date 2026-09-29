/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.event

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molangui.MolangUiPacketLimits
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
        val context = QueryStruct(hashMapOf())
        event.data.forEach { (key, value) -> context.addFunction(key) { DoubleValue(value) } }
        context.addFunction("type") { StringValue(event.type.id) }
        context.addFunction("widget_id") { StringValue(event.widgetId) }
        val functions = runtime.environment.query.functions
        val previous = functions.remove("event")
        runtime.environment.query.addFunction("event") { context }
        try {
            widget.clientEvents[event.type.clientId]?.asExpressionLike()?.resolve(runtime)
            widget.events[event.type.id]?.let { serverHandler ->
                // Action arguments may depend on variables changed by the client handler.
                val parameters = serverHandler.parameters.mapValues { (_, property) -> property.resolveString(runtime) }
                require(parameters.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS) { "too many action parameters" }
                parameters.forEach { (name, value) ->
                    MolangUiPacketLimits.requireIdentifier(name, "action parameter")
                    MolangUiPacketLimits.requireValue(value, name)
                }
                MolangUiActionPacket(sessionId, serverHandler.action.value, parameters).sendToServer()
            }
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Failed UI event {} on widget {}: {}", event.type.id, event.widgetId, error.message)
        } finally {
            functions.remove("event")
            if (previous != null) functions["event"] = previous
        }
    }

    fun hitTest(root: WidgetInstance, x: Float, y: Float): WidgetInstance? {
        if (!root.visible || !root.enabled) return null
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
    HOVER_ENTER("on_hover_enter"),
    HOVER_EXIT("on_hover_exit"),
    SCROLL("on_scroll"),
    KEY("on_key");

    val clientId: String get() = "${id}_client"
}

data class WidgetEvent(
    val type: WidgetEventType,
    val widgetId: String,
    val data: Map<String, Double> = emptyMap()
)
