/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

enum class InterpolationMethod {
    LINEAR,

    /** Catmull-Rom spline (smooth curves through all points). */
    BEZIER,

    /** Hermite spline with tangents. */
    HERMITE,

    /** Cubic Bezier (can be extended with control points). */
    CUBIC_BEZIER
}
