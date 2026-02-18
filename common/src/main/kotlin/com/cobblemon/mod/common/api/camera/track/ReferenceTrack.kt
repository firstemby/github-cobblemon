/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.track

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.model.CameraTrack
import com.cobblemon.mod.common.api.camera.model.TrackOutput
import com.cobblemon.mod.common.client.camera.tracks.CameraTrackDefinitionRegistry
import net.minecraft.world.phys.Vec3

/**
 * Track that references another track definition by ID.
 */
data class ReferenceTrack(
    val trackId: String
) : CameraTrack {
    override val trackType = "reference"

    override val duration: Float
        get() {
            val referencedTrack = CameraTrackDefinitionRegistry.getTrack(trackId)
            return referencedTrack?.duration ?: 0f
        }

    override fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): List<TrackOutput> {
        val referencedTrack = CameraTrackDefinitionRegistry.getTrack(trackId)

        if (referencedTrack == null) {
            // Log warning but don't crash
            Cobblemon.LOGGER.warn("[ReferenceTrack] Track '$trackId' not found in registry")
            return emptyList()
        }

        // Delegate to referenced track
        return referencedTrack.evaluate(time, context, runtime, origin)
    }
}
