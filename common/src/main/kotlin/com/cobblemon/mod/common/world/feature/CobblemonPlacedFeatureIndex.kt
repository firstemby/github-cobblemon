/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.feature

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import java.util.WeakHashMap
import net.minecraft.server.level.ServerLevel

/**
 * Per-dimension, session-lifetime record of where locatable Cobblemon placed features (fossils, habitats,
 * ruins) have generated, keyed by placed feature id to packed chunk positions. Populated at decoration time
 * by `PlacedFeatureMixin` via [PlacedFeatureLocator.onFeaturePlaced], with [markDecorated] flagging chunks
 * the index is authoritative for.
 *
 * Deliberately not persisted: `/locatefeature` answers chunks the index doesn't know (earlier sessions,
 * pre-update terrain) with full seed-model predictions instead, so persistence would only upgrade those
 * answers from "predicted" to "confirmed" at the cost of a save file growing with every generated chunk.
 */
class CobblemonPlacedFeatureIndex {
    private val placements = HashMap<String, LongOpenHashSet>()

    /** Chunks decorated while the index was active - for these, [contains] is authoritative. */
    private val decoratedChunks = LongOpenHashSet()

    /** Chunks discovered to have decorated before the index existed - unknowable this session. */
    private val preIndexChunks = LongOpenHashSet()

    @Synchronized
    fun add(featureId: String, chunkKey: Long) {
        placements.getOrPut(featureId) { LongOpenHashSet() }.add(chunkKey)
    }

    @Synchronized
    fun contains(featureId: String, chunkKey: Long): Boolean = placements[featureId]?.contains(chunkKey) == true

    @Synchronized
    fun markDecorated(chunkKey: Long) {
        decoratedChunks.add(chunkKey)
    }

    @Synchronized
    fun isDecorated(chunkKey: Long): Boolean = decoratedChunks.contains(chunkKey)

    @Synchronized
    fun markPreIndex(chunkKey: Long) {
        preIndexChunks.add(chunkKey)
    }

    @Synchronized
    fun isPreIndex(chunkKey: Long): Boolean = preIndexChunks.contains(chunkKey)

    companion object {
        private val indices = WeakHashMap<ServerLevel, CobblemonPlacedFeatureIndex>()

        @JvmStatic
        fun get(level: ServerLevel): CobblemonPlacedFeatureIndex = synchronized(indices) {
            indices.getOrPut(level) { CobblemonPlacedFeatureIndex() }
        }
    }
}