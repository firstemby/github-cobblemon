/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.util.compat

import net.minecraft.client.multiplayer.ClientLevel

/**
 * Abstraction for Sodium's chunk tracking system.
 *
 * Platform modules (e.g. Fabric) provide a real implementation that directly
 * calls Sodium's [ChunkTrackerHolder] API. When Sodium is absent, the
 * [NoOpSodiumCompat] default is used.
 */
interface SodiumCompat {

    fun isActive(): Boolean
    fun notifyChunkAdded(level: ClientLevel, chunkX: Int, chunkZ: Int)
    fun notifyChunkRemoved(level: ClientLevel, chunkX: Int, chunkZ: Int)

    companion object {
        @JvmStatic
        var instance: SodiumCompat = NoOpSodiumCompat
    }
}

object NoOpSodiumCompat : SodiumCompat {
    override fun isActive(): Boolean = false
    override fun notifyChunkAdded(level: ClientLevel, chunkX: Int, chunkZ: Int) {}
    override fun notifyChunkRemoved(level: ClientLevel, chunkX: Int, chunkZ: Int) {}
}
