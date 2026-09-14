/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.PositionExpression

data class StartPose(
    val position: PositionExpression,
    val rotation: CameraRotation,
    val blendDurationSeconds: Float = 0.5f
) {
    init {
        require(rotation.yaw.isFinite() && rotation.pitch.isFinite()) { "Start pose rotation must be finite" }
        require(blendDurationSeconds.isFinite() && blendDurationSeconds >= 0f) { "Start pose blend duration must be finite and nonnegative" }
    }
}

/** Unset mode teleports newly visible actors and ignores the start pose for already visible actors. */
enum class StartPoseMode {
    TELEPORT,

    BLEND,

    EXPECT,

    IGNORE
}
