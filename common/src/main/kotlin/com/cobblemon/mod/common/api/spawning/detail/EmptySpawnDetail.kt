/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.detail

import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.selection.SpawnSelectionData

/**
 * A spawn detail for nothing. It is used to take a slot, like to weigh down a spawn attempt,
 * with something that ultimately doesn't spawn anything.
 *
 * The use case for this is very niche. With 1.8, the buckets that can be selected by a spawner
 * during a spawn attempt will be narrowed down to the buckets that are used by the spawn
 * pool. That means that if a bucket is never used by the pool, it will never be selected. That
 * way, a bucket won't be selected when it never had any chance of being selected. This is what
 * you would want in almost every case.
 *
 * However, in some cases you may want spawning to not spawn anything or you may want to preserve
 * the relative rarity of the other buckets, and in that case you'd put something like this in a
 * bucket so that it technically exists in the spawn pool without actually resulting in a spawned
 * entity.
 *
 * @author Hiroku
 * @since May 16th, 2026
 */
class EmptySpawnDetail : SpawnDetail() {
    companion object {
        val TYPE = "empty"
        val EMPTY = EmptySpawnDetail()
    }

    override val type: String = TYPE

    override fun createSpawnAction(
        spawnablePosition: SpawnablePosition,
        bucket: String,
        selectionData: SpawnSelectionData
    ) = EmptySpawnAction(
        spawnablePosition = spawnablePosition,
        bucket = bucket,
        detail = this
    )
}