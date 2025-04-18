package com.cobblemon.mod.common.api.spawning

import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity

class GrottoSpawnCause(
    spawner: Spawner,
    bucket: SpawnBucket,
    entity: Entity? = null,
    val structureCenter: BlockPos,
    val grottoPos: BlockPos
) : SpawnCause(spawner, bucket, entity)