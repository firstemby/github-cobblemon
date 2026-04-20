/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

/**
 * A function that maps a linear progress value [0-1] to an eased value.
 */
fun interface EasingFunction {
    fun ease(t: Float): Float
}
