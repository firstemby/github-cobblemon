/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedBoneHint
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import java.util.UUID
import kotlin.math.abs

class FeedRouter(
    val scene: Scene
) {
    val declaredFeeds: List<FeedDeclaration> get() = scene.feeds
    private val orbitProgress = mutableMapOf<String, Double>()
    private var activeOrbits = emptySet<String>()
    private var previousBeatId: String? = null
    private var previousBeatTime = 0f

    fun resetOrbitProgress() {
        orbitProgress.clear()
        activeOrbits = emptySet()
        previousBeatId = null
        previousBeatTime = 0f
    }

    fun buildTransformPacket(
        sceneId: UUID,
        shotEvaluator: ShotEvaluator,
        cutResolver: CutResolver,
        actorDirector: ActorDirector,
        runtime: MoLangRuntime?,
        currentBeat: Beat?,
        beatTime: Float,
        elapsedBeatSeconds: Double? = null
    ): FeedTransformPacket {
        if (currentBeat == null) {
            activeOrbits = emptySet()
            previousBeatId = null
            return FeedTransformPacket(sceneId, emptyList())
        }

        val resolutions = scene.feeds.associate { it.id to cutResolver.resolveLiveShot(currentBeat, it.id, beatTime) }
        val orbits = resolutions.values.mapNotNull { it?.shotId }.filter {
            (scene.getShot(it)?.position as? OrbitPosition)?.rememberProgress == true
        }.toSet()
        val delta = if (previousBeatId == currentBeat.id) {
            elapsedBeatSeconds?.takeIf { it.isFinite() && it >= 0.0 }
                ?: (beatTime.toDouble() - previousBeatTime.toDouble()).coerceAtLeast(0.0)
        } else 0.0
        for (shotId in orbits) {
            orbitProgress[shotId] = orbitProgress.getOrDefault(shotId, 0.0) + if (shotId in activeOrbits) delta else 0.0
        }
        activeOrbits = orbits
        previousBeatId = currentBeat.id
        previousBeatTime = beatTime

        val evaluated = mutableMapOf<String, ShotTransform?>()
        val feedTransforms = scene.feeds.mapNotNull { feed ->
            val resolution = resolutions[feed.id] ?: return@mapNotNull null
            val shotTransform = evaluated.getOrPut(resolution.shotId) {
                shotEvaluator.evaluate(resolution.shotId, beatTime, actorDirector, runtime, orbitTime(resolution.shotId))
            } ?: return@mapNotNull null
            FeedTransform(
                feedId = feed.id,
                position = shotTransform.position,
                rotation = shotTransform.rotation,
                fov = shotTransform.fov,
                roll = shotTransform.roll,
                lens = scene.getShot(resolution.shotId)?.lens,
                boneHint = shotTransform.boneHint?.let {
                    FeedBoneHint(
                        entityId = it.entityId,
                        boneName = it.boneName,
                        offset = it.offset,
                        smooth = it.smooth,
                        yawOffset = it.yawOffset,
                        pitchOffset = it.pitchOffset
                    )
                }
            )
        }

        return FeedTransformPacket(sceneId, feedTransforms)
    }

    private fun orbitTime(shotId: String): Float? {
        val progress = orbitProgress[shotId] ?: return null
        val orbit = scene.getShot(shotId)?.position as? OrbitPosition ?: return null
        val speed = abs(orbit.angularVelocityDegPerSec.toDouble())
        if (speed == 0.0) return 0f
        return (progress % (360.0 / speed)).toFloat()
    }
}
