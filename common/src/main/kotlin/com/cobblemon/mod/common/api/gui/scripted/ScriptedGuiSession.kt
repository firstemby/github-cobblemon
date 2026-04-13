/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetDefinition
import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetTree
import com.cobblemon.mod.common.net.messages.client.gui.ScriptedGuiClosePacket
import com.cobblemon.mod.common.net.messages.client.gui.ScriptedGuiUpdatePacket
import com.cobblemon.mod.common.util.asExpressionLike
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Server-side representation of an active scripted GUI session for a specific player.
 */
class ScriptedGuiSession(
    val sessionId: UUID,
    val guiId: ResourceLocation,
    val player: ServerPlayer,
    definition: ScriptedGuiDefinition,
    private val actionListeners: MutableList<(String, Map<String, String>) -> Unit> = mutableListOf(),
    private val closeListeners: MutableList<() -> Unit> = mutableListOf()
) {
    private var closed = false

    val runtime: MoLangRuntime = ScriptedGuiMolangContext.createServerRuntime(
        guiId = guiId,
        sessionId = sessionId,
        player = player,
        parameters = definition.parameters.mapValues { (_, param) -> param.default ?: "" }
    )

    val rootWidget: WidgetDefinition = WidgetTree.parse(definition.rootJson, isClient = false)

    /**
     * Sends updated arguments to the client.
     */
    fun updateArgs(args: Map<String, String>) {
        if (closed) return
        ScriptedGuiUpdatePacket(sessionId, args).sendToPlayer(player)
    }

    /**
     * Pushes variable and/or parameter updates to the client GUI.
     */
    fun pushUpdate(variables: Map<String, Double> = emptyMap(), parameters: Map<String, String> = emptyMap()) {
        if (closed) {
            Cobblemon.LOGGER.warn("[ScriptedGuiSession] pushUpdate called on closed session $sessionId")
            return
        }
        Cobblemon.LOGGER.info("[ScriptedGuiSession] pushUpdate: sessionId=$sessionId variables=$variables parameters=${parameters.keys}")
        ScriptedGuiUpdatePacket(sessionId, parameters, variables).sendToPlayer(player)
    }

    /**
     * Closes this GUI session and notifies the client.
     */
    fun close() {
        if (closed) return
        closed = true
        ScriptedGuiClosePacket(sessionId).sendToPlayer(player)
        closeListeners.forEach { it() }
        ScriptedGuiSessionManager.removeSession(sessionId)
    }

    /**
     * Adds a listener for player actions from the GUI.
     */
    fun addActionListener(listener: (action: String, data: Map<String, String>) -> Unit) {
        actionListeners.add(listener)
    }

    /**
     * Adds a listener for when the GUI is closed (by server or client).
     */
    fun addCloseListener(listener: () -> Unit) {
        closeListeners.add(listener)
    }

    /**
     * Handles a widget event by finding the widget definition, setting MoLang event context,
     * and evaluating the event handler expression.
     */
    fun handleWidgetEvent(widgetId: String, eventType: String, data: Map<String, String>) {
        if (closed) return
        val widgetDef = findWidget(rootWidget, widgetId)
        if (widgetDef == null) {
            Cobblemon.LOGGER.debug("[ScriptedGuiSession] Widget '$widgetId' not found in GUI '$guiId'")
            return
        }
        val handler = widgetDef.events[eventType]
        if (handler == null) {
            Cobblemon.LOGGER.debug("[ScriptedGuiSession] No handler for event '$eventType' on widget '$widgetId' in GUI '$guiId'")
            return
        }
        ScriptedGuiMolangContext.setEventContext(runtime, widgetId, data)
        try {
            handler.asExpressionLike().resolve(runtime)
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("[ScriptedGuiSession] Error evaluating event '$eventType' handler on widget '$widgetId': ${e.message}", e)
        }
    }

    private fun findWidget(node: WidgetDefinition, targetId: String): WidgetDefinition? {
        if (node.id == targetId) return node
        for (child in node.children) {
            val result = findWidget(child, targetId)
            if (result != null) return result
        }
        return null
    }

    /** Called when an action is received from the client. */
    internal fun onAction(action: String, data: Map<String, String>) {
        if (closed) return
        actionListeners.forEach { it(action, data) }
    }

    /** Called when the client notifies that it closed the GUI. */
    internal fun onClientClosed() {
        if (closed) return
        closed = true
        closeListeners.forEach { it() }
        ScriptedGuiSessionManager.removeSession(sessionId)
    }

    fun isClosed(): Boolean = closed
}
