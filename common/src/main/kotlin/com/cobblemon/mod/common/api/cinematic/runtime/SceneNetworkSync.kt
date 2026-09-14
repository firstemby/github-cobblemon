/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.net.messages.client.cinematic.BeatChangePacket
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.SceneEndPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.SceneStartPacket
import com.cobblemon.mod.common.util.server
import com.cobblemon.mod.common.Cobblemon
import java.util.UUID

class SceneNetworkSync(
    val sceneId: UUID,
    private val viewers: MutableSet<UUID>
) {

    private val viewerChangeListeners: MutableList<(Set<UUID>) -> Unit> = mutableListOf()

    fun onViewersChanged(listener: (Set<UUID>) -> Unit) {
        viewerChangeListeners.add(listener)
    }

    fun viewerIdsSnapshot(): Set<UUID> = viewers.toSet()

    fun addViewer(playerId: UUID) {
        viewers.add(playerId)
        viewerChangeListeners.forEach { it(viewers.toSet()) }
    }

    fun removeViewer(playerId: UUID): Boolean {
        val removed = viewers.remove(playerId)
        if (removed) viewerChangeListeners.forEach { it(viewers.toSet()) }
        return removed
    }

    fun sendSceneStart(packet: SceneStartPacket) {
        val srv = serverOrNull() ?: return
        for (viewerId in viewers) {
            val player = srv.playerList.getPlayer(viewerId) ?: continue
            packet.sendToPlayer(player)
        }
    }

    fun sendSceneEnd(packet: SceneEndPacket) {
        val srv = serverOrNull() ?: return
        for (viewerId in viewers.toList()) {
            try {
                val player = srv.playerList.getPlayer(viewerId) ?: continue
                packet.sendToPlayer(player)
            } catch (error: Exception) {
                Cobblemon.LOGGER.warn("Failed to end cinematic scene '{}' for viewer '{}'", sceneId, viewerId, error)
            }
        }
    }

    fun sendFeedTransform(packet: FeedTransformPacket) {
        val srv = serverOrNull() ?: return
        for (viewerId in viewers) {
            val player = srv.playerList.getPlayer(viewerId) ?: continue
            packet.sendToPlayer(player)
        }
    }

    fun sendFeedAssignment(packet: FeedAssignmentPacket) {
        val srv = serverOrNull() ?: return
        for (viewerId in viewers) {
            val player = srv.playerList.getPlayer(viewerId) ?: continue
            packet.sendToPlayer(player)
        }
    }

    fun sendBeatChange(packet: BeatChangePacket) {
        val srv = serverOrNull() ?: return
        for (viewerId in viewers) {
            val player = srv.playerList.getPlayer(viewerId) ?: continue
            packet.sendToPlayer(player)
        }
    }

    private fun serverOrNull() = try { server() } catch (_: UninitializedPropertyAccessException) { null }
}
