/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.editor

import com.cobblemon.mod.common.api.cinematic.model.CinematicValidationException
import com.cobblemon.mod.common.Cobblemon
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewControlC2SPacket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EditorPreviewSessionRegistry {

    data class Session(val previewId: UUID, val sceneId: UUID, val ownerPlayerUuid: UUID) {
        var revision = 0L
        var pending: EditorPreviewControlC2SPacket? = null
        var error: Component? = null
    }

    private val sessions: ConcurrentHashMap<UUID, Session> = ConcurrentHashMap()

    fun register(previewId: UUID, sceneId: UUID, ownerPlayerUuid: UUID): Session {
        val session = Session(previewId, sceneId, ownerPlayerUuid)
        check(sessions.putIfAbsent(previewId, session) == null) { "Preview id is already active" }
        return session
    }

    fun contains(previewId: UUID): Boolean = sessions.containsKey(previewId)

    fun enqueue(packet: EditorPreviewControlC2SPacket, requesterUuid: UUID): Boolean {
        val session = sessions[packet.previewId] ?: return false
        if (session.ownerPlayerUuid != requesterUuid || packet.revision <= session.revision ||
            packet.revision <= (session.pending?.revision ?: 0L) || !packet.time.isFinite()) return false
        session.pending = packet
        return true
    }

    fun applyPending(session: Session) {
        val packet = session.pending ?: return
        session.pending = null
        session.revision = packet.revision
        val instance = CinematicManager.getScene(session.sceneId) ?: return
        session.error = try {
            instance.configurePreview(packet.beatId, packet.time, packet.paused)
            null
        } catch (error: IllegalArgumentException) {
            Cobblemon.LOGGER.warn("Could not update editor preview", error)
            (error as? CinematicValidationException)?.reason ?: Component.translatable("cobblemon.cinematic_editor.preview.control_failed")
        }
    }

    fun consume(previewId: UUID, requesterUuid: UUID): UUID? {
        val session = sessions[previewId] ?: return null
        if (session.ownerPlayerUuid != requesterUuid) return null
        sessions.remove(previewId)
        return session.sceneId
    }

    fun onPlayerDisconnect(playerUuid: UUID) {
        val owned = sessions.values.filter { it.ownerPlayerUuid == playerUuid }
        owned.forEach { session ->
            CinematicManager.stopScene(session.sceneId)
            sessions.remove(session.previewId)
        }
    }

    fun clear() {
        sessions.clear()
    }

    fun activeCount(): Int = sessions.size
}
