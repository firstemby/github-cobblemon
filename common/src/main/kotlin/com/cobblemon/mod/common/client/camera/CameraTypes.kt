/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.CameraFocus
import com.cobblemon.mod.common.api.camera.model.PositionExpression as ModelPositionExpression
import com.cobblemon.mod.common.api.camera.model.CoordinateValue as ModelCoordinateValue
import net.minecraft.world.phys.Vec3

/**
 * Legacy type alias for backward compatibility.
 * Use CameraFocus in new code.
 */
typealias LookTarget = CameraFocus

/**
 * Legacy type alias for backward compatibility.
 * Use com.cobblemon.mod.common.api.camera.model.PositionExpression in new code.
 */
typealias PositionExpression = ModelPositionExpression

/**
 * Legacy type alias for backward compatibility.
 * Use com.cobblemon.mod.common.api.camera.model.CoordinateValue in new code.
 */
typealias CoordinateValue = ModelCoordinateValue

/**
 * Legacy camera transform for old path-based system.
 *
 * @deprecated Use engine-based camera system instead
 */
@Deprecated("Use TimelineEngine instead", ReplaceWith("CameraTransform from engine"))
data class CameraTransform(
    val position: Vec3,
    val focus: LookTarget? = null,
    val manualRotation: CameraRotation? = null
)

/**
 * Legacy camera focus type alias.
 *
 * @deprecated Use CameraFocus directly
 */
@Deprecated("Use CameraFocus directly", ReplaceWith("CameraFocus"))
typealias CameraFocus = LookTarget

/**
 * Path interpolation types for legacy path system.
 *
 * @deprecated Legacy path system removed
 */
@Deprecated("Legacy path system removed")
enum class PathInterpolationType {
    LINEAR,
    BEZIER,
    CUBIC_BEZIER,
    HERMITE
}
