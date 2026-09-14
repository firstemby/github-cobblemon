/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ClientSceneSessionManager {

    private val sessions: ConcurrentHashMap<UUID, ClientSceneSession> = ConcurrentHashMap()

    fun onSceneStart(
        sceneId: UUID,
        sceneDefinitionId: String,
        origin: Vec3,
        feeds: List<FeedDeclaration>
    ) {
        val existing = sessions[sceneId]
        if (existing != null) {
            // Late-join resync must retain camera IDs so in-flight transforms still resolve.
            return
        }
        sessions[sceneId] = ClientSceneSession(sceneId, sceneDefinitionId, origin, feeds)
    }

    fun onSceneEnd(sceneId: UUID) {
        sessions.remove(sceneId)?.stop()
    }

    fun onFeedTransform(sceneId: UUID, feeds: List<FeedTransform>) {
        val session = sessions[sceneId] ?: return
        feeds.forEach { session.applyFeedTransform(it) }
    }

    fun onFeedAssignment(
        sceneId: UUID,
        feedId: String,
        shotId: String,
        transitionType: FeedAssignmentPacket.TransitionType,
        transitionDurationSeconds: Float
    ) {
        val session = sessions[sceneId] ?: return
        session.applyFeedAssignment(feedId, shotId, transitionType, transitionDurationSeconds)
    }

    fun onBeatChange(
        sceneId: UUID,
        beatId: String,
        instantTransition: Boolean,
        renderPlayerBody: Boolean,
        hiddenHudElements: Set<HudElement>,
        activeFeedIds: Set<String>
    ) {
        val session = sessions[sceneId] ?: return
        session.applyBeatChange(beatId, instantTransition, renderPlayerBody, hiddenHudElements, activeFeedIds)
    }

    fun getSession(sceneId: UUID): ClientSceneSession? = sessions[sceneId]

    fun getAllSessions(): Collection<ClientSceneSession> = sessions.values

    fun clear() {
        sessions.values.forEach { it.stop() }
        sessions.clear()
    }
}
