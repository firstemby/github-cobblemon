/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted

import com.cobblemon.mod.common.net.messages.client.gui.ScriptedGuiClosePacket
import com.cobblemon.mod.common.net.messages.client.gui.ScriptedGuiUpdatePacket
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
    private val actionListeners: MutableList<(String, Map<String, String>) -> Unit> = mutableListOf(),
    private val closeListeners: MutableList<() -> Unit> = mutableListOf()
) {
    private var closed = false

    /**
     * Sends updated arguments to the client.
     */
    fun updateArgs(args: Map<String, String>) {
        if (closed) return
        ScriptedGuiUpdatePacket(sessionId, args).sendToPlayer(player)
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
