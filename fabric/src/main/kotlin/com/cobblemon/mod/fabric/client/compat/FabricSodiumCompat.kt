/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.client.compat

import com.cobblemon.mod.common.client.util.compat.SodiumCompat
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder
import net.minecraft.client.multiplayer.ClientLevel

/**
 * Fabric-specific Sodium compatibility that directly calls Sodium's
 * [ChunkTrackerHolder] API to notify it of camera chunk load/unload events.
 */
object FabricSodiumCompat : SodiumCompat {

    override fun isActive(): Boolean = true

    override fun notifyChunkAdded(level: ClientLevel, chunkX: Int, chunkZ: Int) {
        ChunkTrackerHolder.get(level).onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA)
    }

    override fun notifyChunkRemoved(level: ClientLevel, chunkX: Int, chunkZ: Int) {
        ChunkTrackerHolder.get(level).onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA)
    }
}
