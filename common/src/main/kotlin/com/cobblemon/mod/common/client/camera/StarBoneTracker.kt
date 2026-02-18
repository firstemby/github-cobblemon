/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks bone movement to find the "star bone" - the most animated bone.
 */
object StarBoneTracker {

    private const val MIN_MOVEMENT_THRESHOLD = 0.005
    private const val DECAY_FACTOR = 1
    private const val MIN_SAMPLES_FOR_RELIABILITY = 5
    private const val HALF_WEIGHT_DISTANCE = 0.3

    private val EXCLUDED_BONES = setOf("root", "origin")
    private val EXTREMITY_PATTERNS = listOf("tail", "wing_tip", "feather", "antenna", "whisker", "fin_tip")
    private val IMPORTANT_BONE_PATTERNS = listOf("torso", "body", "chest", "spine", "core")

    private class TrackingData {
        val previousPositions = mutableMapOf<String, Vec3>()
        val scores = mutableMapOf<String, Double>()
        var sampleCount = 0
        var cachedStarBone: String? = null
        var starBoneLocked = false
        var isFirstSample = true
        var middlePosition: Vec3? = null
        val childrenCount = mutableMapOf<String, Int>()
        var modelId: String? = null
    }

    private val tracking = ConcurrentHashMap<Int, TrackingData>()
    private val modelStarBoneCache = ConcurrentHashMap<String, String>()

    fun startTracking(entityId: Int) {
        tracking[entityId] = TrackingData()
    }

    fun stopTracking(entityId: Int) {
        tracking.remove(entityId)
    }

    fun isTracking(entityId: Int): Boolean {
        return tracking.containsKey(entityId)
    }

    fun resetTracking(entityId: Int) {
        if (tracking.containsKey(entityId)) {
            tracking[entityId] = TrackingData()
        }
    }

    fun isStale(entityId: Int): Boolean {
        val data = tracking[entityId] ?: return false
        if (data.starBoneLocked) return false
        return data.sampleCount > 60 && data.cachedStarBone == null
    }

    fun setBoneMetadata(entityId: Int, boneChildrenCounts: Map<String, Int>) {
        val data = tracking[entityId] ?: return
        data.childrenCount.clear()
        data.childrenCount.putAll(boneChildrenCounts)
    }

    fun setModelId(entityId: Int, modelId: String) {
        val data = tracking[entityId] ?: return
        data.modelId = modelId

        val cachedBone = modelStarBoneCache[modelId]
        if (cachedBone != null) {
            data.cachedStarBone = cachedBone
            data.starBoneLocked = true
        }
    }

    fun sample(entityId: Int, state: PosableState) {
        val data = tracking[entityId] ?: return

        // Get middle position for proximity weighting
        val middlePos = try {
            state.locatorStates["middle"]?.getOrigin()
        } catch (e: Exception) {
            null
        }
        data.middlePosition = middlePos

        // Collect all bone positions from locators and tracked bones
        val currentPositions = mutableMapOf<String, Vec3>()

        for ((name, matrix) in state.locatorStates) {
            if (name.lowercase() in EXCLUDED_BONES) continue
            try {
                currentPositions[name] = matrix.getOrigin()
            } catch (e: Exception) {
                // Skip bones that fail position calculation
            }
        }

        for ((name, matrix) in state.trackedBoneStates) {
            if (name.lowercase() in EXCLUDED_BONES) continue
            if (currentPositions.containsKey(name)) continue // Prefer locator
            try {
                currentPositions[name] = matrix.getOrigin()
            } catch (e: Exception) {
                // Skip
            }
        }

        if (data.isFirstSample) {
            data.previousPositions.putAll(currentPositions)
            data.isFirstSample = false
            data.sampleCount++
            return
        }

        data.scores.replaceAll { _, score -> score * DECAY_FACTOR }

        for ((name, currentPos) in currentPositions) {
            val previousPos = data.previousPositions[name]
            if (previousPos != null) {
                val movement = currentPos.distanceTo(previousPos)
                if (movement > MIN_MOVEMENT_THRESHOLD) {
                    val weight = calculateBoneWeight(name, currentPos, middlePos, data.childrenCount)
                    val currentScore = data.scores.getOrDefault(name, 0.0)
                    data.scores[name] = currentScore + (movement * weight)
                }
            }
            data.previousPositions[name] = currentPos
        }

        if (!data.starBoneLocked) {
            val bestBone = data.scores.maxByOrNull { it.value }?.key
            if (bestBone != null && data.sampleCount >= MIN_SAMPLES_FOR_RELIABILITY) {
                data.cachedStarBone = bestBone
                data.starBoneLocked = true

                val modelId = data.modelId
                if (modelId != null) {
                    modelStarBoneCache[modelId] = bestBone
                }
            }
        }

        data.sampleCount++
    }

    private fun calculateBoneWeight(
        boneName: String,
        bonePos: Vec3,
        middlePos: Vec3?,
        childrenCounts: Map<String, Int>
    ): Double {
        var weight = 1.0
        val lowerName = boneName.lowercase()

        if (middlePos != null) {
            val distance = bonePos.distanceTo(middlePos)
            val proximityWeight = 1.0 / (1.0 + distance / HALF_WEIGHT_DISTANCE)
            weight *= proximityWeight
        }

        val descendants = childrenCounts[boneName] ?: 0
        if (descendants > 0) {
            val descendantBoost = 1.0 + (descendants.toDouble() * 0.3)
            weight *= descendantBoost
        }

        if (IMPORTANT_BONE_PATTERNS.any { lowerName.contains(it) }) {
            weight *= 3.0
        }

        if (EXTREMITY_PATTERNS.any { lowerName.contains(it) }) {
            weight *= 0.2
        }

        return weight
    }

    fun getStarBone(entityId: Int): String? {
        val data = tracking[entityId] ?: return null

        if (data.sampleCount < MIN_SAMPLES_FOR_RELIABILITY) {
            return null
        }

        return data.cachedStarBone
    }

    fun getStarBonePosition(entityId: Int, state: PosableState): Vec3? {
        val starBone = getStarBone(entityId) ?: return null
        state.locatorStates[starBone]?.let { return it.getOrigin() }
        state.trackedBoneStates[starBone]?.let { return it.getOrigin() }
        return null
    }

    fun getBoneScore(entityId: Int, boneName: String): Double {
        return tracking[entityId]?.scores?.get(boneName) ?: 0.0
    }

    fun getRankedBones(entityId: Int, limit: Int = 10): List<Pair<String, Double>> {
        val data = tracking[entityId] ?: return emptyList()

        return data.scores.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    fun isReliable(entityId: Int): Boolean {
        val data = tracking[entityId] ?: return false
        return data.sampleCount >= MIN_SAMPLES_FOR_RELIABILITY
    }

    fun getSampleCount(entityId: Int): Int {
        return tracking[entityId]?.sampleCount ?: 0
    }

    fun clearAll() {
        tracking.clear()
    }

    fun debugPrint(entityId: Int) {
        val data = tracking[entityId]
        if (data == null) {
            Cobblemon.LOGGER.debug("[StarBone] Entity $entityId: not tracking")
            return
        }

        val starBone = getStarBone(entityId) ?: "(not enough data)"
        val topBones = getRankedBones(entityId, 5).joinToString { "${it.first}=${String.format("%.4f", it.second)}" }
        Cobblemon.LOGGER.debug("[StarBone] Entity $entityId: samples=${data.sampleCount}, star=$starBone, top=[$topBones]")
    }
}
