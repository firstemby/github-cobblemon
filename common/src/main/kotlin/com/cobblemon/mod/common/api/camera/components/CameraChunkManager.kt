/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.components

import net.minecraft.server.level.ChunkTrackingView
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CameraChunkManager {
    private val playerChunkViews = ConcurrentHashMap<UUID, ChunkTrackingView>()
    private val chunkViewUpdates = ConcurrentHashMap<UUID, ChunkViewUpdate>()

    data class ChunkViewUpdate(val previous: ChunkTrackingView?, val current: ChunkTrackingView?)

    fun registerViewerChunkView(playerId: UUID, newView: ChunkTrackingView) {
        val previous = playerChunkViews.put(playerId, newView)

        if (previous == null || previous != newView) {
            chunkViewUpdates[playerId] = ChunkViewUpdate(previous, newView)
        }
    }

    fun clearViewerChunkView(playerId: UUID) {
        val previous = playerChunkViews.remove(playerId)

        if (previous != null) {
            chunkViewUpdates[playerId] = ChunkViewUpdate(previous, null)
        }
    }

    fun getPlayerChunkView(playerId: UUID): ChunkTrackingView? = playerChunkViews[playerId]

    fun consumeChunkViewUpdate(playerId: UUID): ChunkViewUpdate? = chunkViewUpdates.remove(playerId)

    fun clear() {
        playerChunkViews.clear()
        chunkViewUpdates.clear()
    }
}
