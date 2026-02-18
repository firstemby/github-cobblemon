/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.net.messages.client.gui.ScriptedGuiOpenPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-side manager for active scripted GUI sessions.
 */
object ScriptedGuiSessionManager {

    private val sessions = ConcurrentHashMap<UUID, ScriptedGuiSession>()

    /**
     * Opens a scripted GUI for a player.
     *
     * @param player The target player
     * @param guiId The GUI definition resource location
     * @param args Initial arguments to pass to the GUI
     * @param actionListener Optional listener for player actions
     * @return The session, or null if the GUI definition doesn't exist
     */
    fun openGui(
        player: ServerPlayer,
        guiId: ResourceLocation,
        args: Map<String, String> = emptyMap(),
        actionListener: ((String, Map<String, String>) -> Unit)? = null
    ): ScriptedGuiSession? {
        val definition = ScriptedGuiRegistry.getDefinition(guiId)
        if (definition == null) {
            Cobblemon.LOGGER.error("[ScriptedGuiSessionManager] GUI definition '$guiId' not found")
            return null
        }

        val sessionId = UUID.randomUUID()
        val session = ScriptedGuiSession(sessionId, guiId, player)

        if (actionListener != null) {
            session.addActionListener(actionListener)
        }

        sessions[sessionId] = session

        // Send open packet to client
        ScriptedGuiOpenPacket(
            sessionId = sessionId,
            guiId = guiId,
            mode = definition.mode,
            args = args,
            blocksInput = definition.blocksInput,
            exclusive = definition.exclusive
        ).sendToPlayer(player)

        Cobblemon.LOGGER.debug("[ScriptedGuiSessionManager] Opened GUI '$guiId' for ${player.name.string} (session=$sessionId)")
        return session
    }

    /**
     * Closes a GUI session.
     */
    fun closeGui(sessionId: UUID) {
        sessions[sessionId]?.close()
    }

    /**
     * Handles an action from a client.
     */
    fun handleAction(sessionId: UUID, player: ServerPlayer, action: String, data: Map<String, String>) {
        val session = sessions[sessionId]
        if (session == null) {
            Cobblemon.LOGGER.debug("[ScriptedGuiSessionManager] Action for unknown session $sessionId")
            return
        }
        if (session.player.uuid != player.uuid) {
            Cobblemon.LOGGER.warn("[ScriptedGuiSessionManager] Action from wrong player for session $sessionId")
            return
        }
        session.onAction(action, data)
    }

    /**
     * Handles a client-closed notification.
     */
    fun handleClosed(sessionId: UUID, player: ServerPlayer) {
        val session = sessions[sessionId]
        if (session == null) return
        if (session.player.uuid != player.uuid) {
            Cobblemon.LOGGER.warn("[ScriptedGuiSessionManager] Close from wrong player for session $sessionId")
            return
        }
        session.onClientClosed()
    }

    /**
     * Closes all GUI sessions for a player. Call on disconnect.
     */
    fun closeAllForPlayer(playerId: UUID) {
        sessions.values.filter { it.player.uuid == playerId }.forEach {
            it.close()
        }
    }

    /** Internal: removes a session from tracking. */
    internal fun removeSession(sessionId: UUID) {
        sessions.remove(sessionId)
    }

    fun getSession(sessionId: UUID): ScriptedGuiSession? = sessions[sessionId]
}
