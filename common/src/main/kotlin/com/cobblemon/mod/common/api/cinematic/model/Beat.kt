/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import com.cobblemon.mod.common.api.camera.model.HudElement

/** Null duration leaves the beat unbounded; feeds without cuts are inactive. */
data class Beat(
    val id: String,
    val duration: Float? = null,
    val isLooping: Boolean = false,
    val speed: Float = 1.0f,
    val actorDirections: Map<String, ActorDirection> = emptyMap(),
    val cuts: Map<String, CutTrack> = emptyMap(),
    val guiTrack: SceneGuiTrack? = null,
    val renderPlayerBody: Boolean = true,
    val hiddenHudElements: Set<HudElement> = emptySet(),
    val cues: List<SceneCue> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Beat id must be non-blank" }
        require(speed.isFinite() && speed > 0f) { "Beat speed must be positive, got $speed" }
        duration?.let {
            require(it.isFinite() && it >= 0f) { "Beat duration must be non-negative, got $it" }
        }
    }
}
