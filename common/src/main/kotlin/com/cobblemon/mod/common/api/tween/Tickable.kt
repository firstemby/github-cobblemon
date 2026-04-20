/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

interface Tickable {
    val isActive: Boolean

    /** @param dt delta time in seconds (not game ticks). */
    fun tick(dt: Float)

    fun cancel()
}
