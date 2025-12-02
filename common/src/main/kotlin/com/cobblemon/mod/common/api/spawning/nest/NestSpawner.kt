/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.fishing

import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.position.NestSpawnablePosition
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import com.cobblemon.mod.common.api.spawning.nest.NestSpawnCause
import com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos

// todo I don't think this is needed at all
/*
/**
 * A spawner that takes in a [FishingSpawnCause] and spawns things.
 *
 * @author Plastered_Crab
 * @since February 3rd, 2024
 */
open class NestSpawner(
    override val name: String = "nest",
    pool: SpawnPool = CobblemonSpawnPools.WORLD_SPAWN_POOL
) : BasicSpawner<NestSpawnCause>(name, pool) {
    override fun parseSpawnablePosition(cause: NestSpawnCause, world: ServerLevel, pos: BlockPos): NestSpawnablePosition? {
        val spawnablePosition = NestSpawnablePosition(
            cause = cause,
            world = world,
            pos = pos,
            influences = influences
        )
        return spawnablePosition
    }
}

 */