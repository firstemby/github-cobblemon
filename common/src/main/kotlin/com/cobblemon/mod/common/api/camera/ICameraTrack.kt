/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import net.minecraft.world.phys.Vec3

interface ICameraTrack {
    val trackType: String

    fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): Any?
}

data class CameraTrackEvaluation(
    val position: Vec3? = null,
    val focus: Vec3? = null,
    val rotation: CameraRotation? = null,
    val events: List<String> = emptyList()
)

data class CameraRotation(
    val yaw: Float,
    val pitch: Float
)
