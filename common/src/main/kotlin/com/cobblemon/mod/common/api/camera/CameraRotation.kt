/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

/**
 * Represents a camera rotation in 3D space.
 *
 * @property yaw Horizontal rotation (left/right) in degrees
 * @property pitch Vertical rotation (up/down) in degrees
 */
data class CameraRotation(
    val yaw: Float,
    val pitch: Float
)
