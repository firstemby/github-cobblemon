/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.fishing

import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.context.FishingSpawningContext
import com.cobblemon.mod.common.api.spawning.context.NestSpawningContext
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import com.cobblemon.mod.common.api.spawning.nest.NestSpawnCause
import com.cobblemon.mod.common.api.spawning.spawner.TriggerSpawner
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos

/**
 * A spawner that takes in a [FishingSpawnCause] and spawns things.
 *
 * @author Plastered_Crab
 * @since February 3rd, 2024
 */
open class NestSpawner(
        override val name: String = "nest",
        pool: SpawnPool = CobblemonSpawnPools.WORLD_SPAWN_POOL
) : TriggerSpawner<NestSpawnCause>(name, pool) {
    override fun parseContext(cause: NestSpawnCause, world: ServerLevel, pos: BlockPos): NestSpawningContext? {
        val ctx = NestSpawningContext(
                cause = cause,
                world = world,
                pos = pos,
                influences = influences
        )
        return ctx
    }
}