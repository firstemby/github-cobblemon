/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai.moves

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RealTimeMoveManager {

    private val realTimeMoveMap = ConcurrentHashMap<UUID, RealTimeMoveInstance>()


    @JvmStatic
    fun addInstance(realTimeMoveInstance: RealTimeMoveInstance): UUID {
        val id = UUID.randomUUID()
        realTimeMoveMap[id] = realTimeMoveInstance
        realTimeMoveInstance.start()
        return id
    }

    @JvmStatic
    fun removeInstance(id: UUID) {
        val instance = realTimeMoveMap.getOrDefault(id, null)
        if (instance != null) {
            instance.end()
            realTimeMoveMap.remove(id)
        }
    }

    fun tick() {
        // TODO: Handle whether stuff is loaded in chunks
        realTimeMoveMap.forEachEntry(Long.MAX_VALUE) { it ->
            if (!it.value.tick()) {
                it.value.end()
                realTimeMoveMap.remove(it.key)
            }
        }
    }
}