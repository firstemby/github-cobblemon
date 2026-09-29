/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.RotationSource
import com.google.gson.JsonObject

data class FixedRotation(val rotation: CameraRotation) : RotationSource() {
    override val type: String = "fixed"

    init {
        require(rotation.yaw.isFinite() && rotation.pitch.isFinite()) { "Fixed rotation must be finite" }
    }

    companion object {
        const val TYPE = "fixed"
        fun fromJson(obj: JsonObject): FixedRotation {
            return FixedRotation(CameraRotation(
                yaw = obj.get("yaw").asFloat,
                pitch = obj.get("pitch").asFloat
            ))
        }
    }
}
