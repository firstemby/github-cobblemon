/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.api.spawning.WorldSlice
import com.cobblemon.mod.common.api.spawning.context.SpawningContext

/**
 * A wrapping around a [SpawningInfluence] that was produced for a [WorldSlice]. This is wrapped
 * to provide a filtration mechanism on which of these can be applied to a [SpawningContext].
 *
 * @author Hiroku
 * @since March 9th, 2025
 */
open class WorldSlicedSpawningInfluence(val influence: SpawningInfluence) {
    /** Whether this influence will apply to */
    open fun appliesTo(context: SpawningContext): Boolean = true
}
