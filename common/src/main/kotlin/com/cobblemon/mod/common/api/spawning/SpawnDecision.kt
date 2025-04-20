/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning

import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail

/**
 * A decided consequence of a spawn pass that should result in a spawn detail being actioned on a particular
 * spawnable position.
 *
 * @author Hiroku
 * @since April 19th, 2025
 */
class SpawnDecision(
    val spawnablePosition: SpawnablePosition,
    val bucket: SpawnBucket,
    val spawnDetail: SpawnDetail,
)