/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.ShotModifier
import com.google.gson.JsonObject

/** Factor 0 disables smoothing; factor 1 freezes the previous value. */
data class SmoothingModifier(
    val positionFactor: Float = 0f,
    val rotationFactor: Float = 0f,
    val fovFactor: Float = 0f
) : ShotModifier() {
    override val type: String = "smoothing"

    init {
        require(positionFactor in 0f..1f) { "positionFactor must be in [0, 1]" }
        require(rotationFactor in 0f..1f) { "rotationFactor must be in [0, 1]" }
        require(fovFactor in 0f..1f) { "fovFactor must be in [0, 1]" }
    }

    companion object {
        const val TYPE = "smoothing"
        fun fromJson(obj: JsonObject): SmoothingModifier = SmoothingModifier(
            positionFactor = obj.get("position")?.asFloat ?: 0f,
            rotationFactor = obj.get("rotation")?.asFloat ?: 0f,
            fovFactor = obj.get("fov")?.asFloat ?: 0f
        )
    }
}
