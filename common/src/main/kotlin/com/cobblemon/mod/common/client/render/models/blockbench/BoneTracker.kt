/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench

/**
 * Manages bone tracking requests per entity.
 *
 * When a system (like bone focus camera) needs to track the animated position
 * of an arbitrary bone, it calls [hold] with the entity ID and bone name.
 * During the next render cycle, [PosableModel.updateTrackedBones] will calculate
 * the bone's position after animations are applied and store it in the entity's
 * [PosableState.trackedBoneStates].
 *
 * This is necessary because bone positions can only be calculated correctly
 * during the render cycle, after animations have been applied to the model.
 *
 * @author Cobblemon Contributors
 * @since 1.6.0
 */
object BoneTracker {
    // entityId -> Set<boneName>
    private val trackedBones = mutableMapOf<Int, MutableSet<String>>()

    /**
     * Request tracking of a bone for an entity.
     * The bone position will be calculated during the next render cycle.
     *
     * @param entityId The entity's network ID
     * @param boneName The name of the bone to track
     */
    fun hold(entityId: Int, boneName: String) {
        trackedBones.getOrPut(entityId) { mutableSetOf() }.add(boneName)
    }

    /**
     * Stop tracking a specific bone for an entity.
     *
     * @param entityId The entity's network ID
     * @param boneName The name of the bone to stop tracking
     */
    fun release(entityId: Int, boneName: String) {
        trackedBones[entityId]?.remove(boneName)
        // Clean up empty sets
        if (trackedBones[entityId]?.isEmpty() == true) {
            trackedBones.remove(entityId)
        }
    }

    /**
     * Stop tracking all bones for an entity.
     * Should be called when an entity is removed or when camera stops.
     *
     * @param entityId The entity's network ID
     */
    fun releaseAll(entityId: Int) {
        trackedBones.remove(entityId)
    }

    /**
     * Get the set of bones currently being tracked for an entity.
     *
     * @param entityId The entity's network ID
     * @return Set of bone names being tracked, or empty set if none
     */
    fun getTrackedBones(entityId: Int): Set<String> {
        return trackedBones[entityId] ?: emptySet()
    }

    /**
     * Check if a specific bone is being tracked for an entity.
     *
     * @param entityId The entity's network ID
     * @param boneName The name of the bone
     * @return True if the bone is being tracked
     */
    fun isTracked(entityId: Int, boneName: String): Boolean {
        return trackedBones[entityId]?.contains(boneName) == true
    }

    /**
     * Clear all tracked bones. Should be called on world unload.
     */
    fun clear() {
        trackedBones.clear()
    }
}
