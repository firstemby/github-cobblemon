/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.detail

import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition

/**
 * Does a whole lot of nothing. Spawns nothing.
 *
 * @author Hiroku
 * @since May 16th, 2026
 */
class EmptySpawnAction(
    spawnablePosition: SpawnablePosition,
    bucket: String,
    detail: EmptySpawnDetail
) : SpawnAction<Unit>(spawnablePosition, bucket, detail) {
    override fun run() {}
}