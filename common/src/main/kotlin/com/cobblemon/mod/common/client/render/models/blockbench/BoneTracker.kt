/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench

import java.util.concurrent.ConcurrentHashMap

object BoneTracker {
    private val trackedBones = ConcurrentHashMap<Int, ConcurrentHashMap<String, Int>>()

    fun hold(entityId: Int, boneName: String) {
        trackedBones.compute(entityId) { _, existing ->
            (existing ?: ConcurrentHashMap()).also { it.merge(boneName, 1, Int::plus) }
        }
    }

    fun release(entityId: Int, boneName: String) {
        trackedBones.computeIfPresent(entityId) { _, bones ->
            bones.computeIfPresent(boneName) { _, count -> if (count > 1) count - 1 else null }
            bones.takeUnless { it.isEmpty() }
        }
    }

    fun getTrackedBones(entityId: Int): Set<String> = trackedBones[entityId]?.keys?.toSet() ?: emptySet()

    fun isTracked(entityId: Int, boneName: String): Boolean = trackedBones[entityId]?.containsKey(boneName) == true

    fun clear() = trackedBones.clear()
}
