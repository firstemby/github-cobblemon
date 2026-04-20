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
 * Tracks bone movement to find the "star bone" — the best bone to focus the camera on.
 *
 * **Scoring philosophy:**
 * - **Proximity to middle is king.** The closer a bone is to the entity's center, the higher it scores.
 * - **Movement is a qualifier, not a multiplier.** A bone must prove it *can* move (has moved at least once)
 *   to be eligible. The amount of movement is irrelevant beyond that threshold.
 * - **Star bone is stable during attacks.** Once chosen, the star bone stays locked until
 *   explicitly re-evaluated via [requestReEval] (e.g. between attacks).
 */
object StarBoneTracker {

    private const val MIN_MOVEMENT_THRESHOLD = 0.005
    private const val MIN_SAMPLES_FOR_RELIABILITY = 5
    private const val HALF_WEIGHT_DISTANCE = 0.3
    private const val PROXIMITY_CENTER_BOOST = 3.0

    private val EXCLUDED_BONES = setOf("root", "origin")
    private val EXTREMITY_PATTERNS = listOf("tail", "wing_tip", "feather", "antenna", "whisker", "fin_tip")
    private val IMPORTANT_BONE_PATTERNS = listOf("torso", "body", "chest", "spine", "core")

    data class WeightBreakdown(
        val proximity: Double = 1.0,
        val descendants: Double = 1.0,
        val nameBonus: Double = 1.0,
        val totalWeight: Double = 1.0,
        val distanceToMiddle: Double = -1.0,
        val hasMoved: Boolean = false
    )

    private class TrackingData {
        val previousPositions = mutableMapOf<String, Vec3>()
        val movedBones = mutableSetOf<String>()
        /** Latest proximity-based score per bone (recalculated each sample). */
        val scores = mutableMapOf<String, Double>()
        val breakdowns = mutableMapOf<String, WeightBreakdown>()
        var sampleCount = 0
        var cachedStarBone: String? = null
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
        if (data.cachedStarBone != null) return false
        return data.sampleCount > 60 && data.movedBones.isEmpty()
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
        }
    }

    /**
     * Request a re-evaluation of the star bone for this entity.
     * Call this between attacks/animations to allow the star bone to change
     * if a better candidate is now available. Safe to call during tracking —
     * the current star bone is cleared and the next [sample] call will pick the best one.
     */
    fun requestReEval(entityId: Int) {
        val data = tracking[entityId] ?: return
        val oldStar = data.cachedStarBone ?: return // nothing to re-evaluate
        data.cachedStarBone = null

        val modelId = data.modelId
        if (modelId != null) {
            modelStarBoneCache.remove(modelId)
        }

        Cobblemon.LOGGER.info("[StarBone] Entity $entityId: re-eval requested (was: $oldStar)")
    }

    fun sample(entityId: Int, state: PosableState) {
        val data = tracking[entityId] ?: return

        // Get middle position — this is the anchor for proximity scoring
        val middlePos = try {
            state.locatorStates["middle"]?.getOrigin()
        } catch (e: Exception) {
            null
        }
        data.middlePosition = middlePos

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
            if (currentPositions.containsKey(name)) continue
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

        var anyNewMovement = false
        for ((name, currentPos) in currentPositions) {
            val previousPos = data.previousPositions[name]
            if (previousPos != null) {
                val movement = currentPos.distanceTo(previousPos)
                if (movement > MIN_MOVEMENT_THRESHOLD) {
                    if (data.movedBones.add(name)) {
                        anyNewMovement = true
                    }
                }
            }
            data.previousPositions[name] = currentPos
        }

        data.scores.clear()
        data.breakdowns.clear()

        for ((name, currentPos) in currentPositions) {
            val hasMoved = name in data.movedBones
            val breakdown = calculateBoneScore(name, currentPos, middlePos, data.childrenCount, hasMoved)
            data.breakdowns[name] = breakdown

            if (hasMoved) {
                data.scores[name] = breakdown.totalWeight
            }
        }

        // Pick star bone once, then lock it. Only changes via requestReEval().
        if (data.cachedStarBone == null && data.scores.isNotEmpty() && data.sampleCount >= MIN_SAMPLES_FOR_RELIABILITY) {
            val bestBone = data.scores.maxByOrNull { it.value }?.key
            if (bestBone != null) {
                data.cachedStarBone = bestBone

                val modelId = data.modelId
                if (modelId != null) {
                    modelStarBoneCache[modelId] = bestBone
                }

                debugPrint(entityId)
            }
        }

        data.sampleCount++
    }

    /**
     * Calculate the score for a bone. Proximity to middle is the dominant factor.
     * Movement acts as a boolean gate — only bones that have moved are eligible.
     */
    private fun calculateBoneScore(
        boneName: String,
        bonePos: Vec3,
        middlePos: Vec3?,
        childrenCounts: Map<String, Int>,
        hasMoved: Boolean
    ): WeightBreakdown {
        val lowerName = boneName.lowercase()

        // Proximity: dominant factor. Higher = closer to middle
        var proximityFactor = 1.0
        var distanceToMiddle = -1.0
        if (middlePos != null) {
            distanceToMiddle = bonePos.distanceTo(middlePos)
            proximityFactor = PROXIMITY_CENTER_BOOST / (1.0 + distanceToMiddle / HALF_WEIGHT_DISTANCE)
        }

        // Descendants: minor structural bonus
        var descendantFactor = 1.0
        val descendants = childrenCounts[boneName] ?: 0
        if (descendants > 0) {
            descendantFactor = 1.0 + (descendants.toDouble() * 0.1)
        }

        // Name patterns: minor hints
        var nameFactor = 1.0
        if (IMPORTANT_BONE_PATTERNS.any { lowerName.contains(it) }) {
            nameFactor *= 1.5
        }
        if (EXTREMITY_PATTERNS.any { lowerName.contains(it) }) {
            nameFactor *= 0.3
        }

        val totalWeight = proximityFactor * descendantFactor * nameFactor

        return WeightBreakdown(
            proximity = proximityFactor,
            descendants = descendantFactor,
            nameBonus = nameFactor,
            totalWeight = totalWeight,
            distanceToMiddle = distanceToMiddle,
            hasMoved = hasMoved
        )
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

    fun getBreakdown(entityId: Int, boneName: String): WeightBreakdown? {
        return tracking[entityId]?.breakdowns?.get(boneName)
    }

    fun debugPrint(entityId: Int) {
        val data = tracking[entityId]
        if (data == null) {
            Cobblemon.LOGGER.info("[StarBone] Entity $entityId: not tracking")
            return
        }

        val starBone = data.cachedStarBone ?: "(none)"
        val modelId = data.modelId ?: "unknown"
        val movedCount = data.movedBones.size
        val totalBones = data.breakdowns.size

        Cobblemon.LOGGER.info("[StarBone] Entity $entityId | model=$modelId | samples=${data.sampleCount} | star=$starBone | moved=$movedCount/$totalBones bones")
        Cobblemon.LOGGER.info("[StarBone] --- Eligible bones (moved + scored by proximity) ---")

        val eligible = data.scores.entries
            .sortedByDescending { it.value }
            .take(10)

        for ((index, entry) in eligible.withIndex()) {
            val name = entry.key
            val score = entry.value
            val bd = data.breakdowns[name]
            val marker = if (name == starBone) " << STAR" else ""

            if (bd != null) {
                Cobblemon.LOGGER.info(
                    "[StarBone]  #${index + 1} %-25s score=%.4f | prox=%.2fx (dist=%.3f) | desc=%.2fx | name=%.2fx%s".format(
                        name, score, bd.proximity, bd.distanceToMiddle, bd.descendants, bd.nameBonus, marker
                    )
                )
            } else {
                Cobblemon.LOGGER.info("[StarBone]  #${index + 1} %-25s score=%.4f%s".format(name, score, marker))
            }
        }

        // Show top non-eligible bones (close but haven't moved yet)
        val nonEligible = data.breakdowns.entries
            .filter { it.key !in data.movedBones }
            .sortedByDescending { it.value.proximity }
            .take(5)

        if (nonEligible.isNotEmpty()) {
            Cobblemon.LOGGER.info("[StarBone] --- Close but haven't moved yet ---")
            for ((name, bd) in nonEligible) {
                Cobblemon.LOGGER.info(
                    "[StarBone]    %-25s prox=%.2fx (dist=%.3f) -- waiting for movement".format(
                        name, bd.proximity, bd.distanceToMiddle
                    )
                )
            }
        }

        if (data.middlePosition != null) {
            val mid = data.middlePosition!!
            Cobblemon.LOGGER.info("[StarBone] Middle position: (%.2f, %.2f, %.2f)".format(mid.x, mid.y, mid.z))
        } else {
            Cobblemon.LOGGER.info("[StarBone] Middle position: not available")
        }
    }
}
