/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import net.minecraft.world.phys.Vec3

internal class StarBoneTracker(private val descendants: Map<String, Int>) {
    var starBone: String? = null
        private set
    private val previousPositions = mutableMapOf<String, Vec3>()
    private val movedBones = mutableSetOf<String>()
    private var sampleCount = 0

    fun sample(state: PosableState): String? {
        starBone?.let { return it }
        val positions = mutableMapOf<String, Vec3>()
        if (state.currentModel != null && state.trackedBoneModel === state.currentModel) {
            state.trackedBoneStates.forEach { (name, matrix) -> positions[name] = matrix.getOrigin() }
        }
        state.locatorStates.forEach { (name, matrix) -> positions[name] = matrix.getOrigin() }
        positions.entries.removeIf { (name, pos) -> name.lowercase() in EXCLUDED_BONES || !pos.isFinite() }
        val middle = positions["middle"]
        val root = state.locatorStates["root"]
        for ((name, position) in positions) {
            // Root-relative movement excludes translation and rotation of the whole entity.
            val local = root?.transformWorldToParticle(position) ?: position.subtract(middle ?: Vec3.ZERO)
            if (!local.isFinite()) continue
            val previous = previousPositions.put(name, local)
            if (previous != null && local.distanceTo(previous) > MIN_MOVEMENT_THRESHOLD) movedBones.add(name)
        }
        sampleCount++
        if (sampleCount >= MIN_SAMPLES_FOR_RELIABILITY) {
            starBone = positions.keys.filter { it in movedBones }
                .maxWithOrNull(compareBy<String> { score(it, positions.getValue(it), middle) }.thenBy { it })
        }
        return starBone
    }

    private fun score(name: String, position: Vec3, middle: Vec3?): Double {
        // Proximity dominates; descendants and anatomical names only adjust otherwise eligible bones.
        val proximity = middle?.let { 3.0 / (1.0 + position.distanceTo(it) / 0.3) } ?: 1.0
        val structure = 1.0 + (descendants[name] ?: 0) * 0.1
        val lower = name.lowercase()
        val central = if (IMPORTANT_BONE_PATTERNS.any(lower::contains)) 1.5 else 1.0
        val extremity = if (EXTREMITY_PATTERNS.any(lower::contains)) 0.3 else 1.0
        return proximity * structure * central * extremity
    }

    private fun Vec3.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    companion object {
        private const val MIN_MOVEMENT_THRESHOLD = 0.005
        private const val MIN_SAMPLES_FOR_RELIABILITY = 5
        private val EXCLUDED_BONES = setOf("root", "origin")
        private val EXTREMITY_PATTERNS = listOf("tail", "wing_tip", "feather", "antenna", "whisker", "fin_tip")
        private val IMPORTANT_BONE_PATTERNS = listOf("torso", "body", "chest", "spine", "core")
    }
}
