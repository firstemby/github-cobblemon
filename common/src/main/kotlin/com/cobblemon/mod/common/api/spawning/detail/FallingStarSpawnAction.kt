/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.detail

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.entity.projectile.FallingStarEntity
import kotlin.math.min
import kotlin.random.Random
import net.minecraft.world.phys.Vec3

class FallingStarSpawnAction(
    spawnablePosition: SpawnablePosition,
    bucket: String,
    override val detail: FallingStarSpawnDetail
) : SpawnAction<EntitySpawnResult>(spawnablePosition, bucket, detail) {

    override fun run(): EntitySpawnResult {
        Cobblemon.LOGGER.info("Falling towards ${spawnablePosition.position}")
        val world = spawnablePosition.world
        val fallingStar = FallingStarEntity(CobblemonEntities.FALLING_STAR, world)
        fallingStar.resultType = detail.resultType

        val spawnPos = spawnablePosition.position
        fallingStar.horizontalDirection = Random.nextDouble(2 * Math.PI).toFloat()
        
        val skyHeight = min(spawnPos.y + 64, world.maxBuildHeight - 10).toDouble()
        val fallDistance = skyHeight - spawnPos.y
        val ticksToFall = kotlin.math.sqrt(fallDistance / 0.015)
        val horizontalDistance = ticksToFall * 0.8
        
        val offsetX = -kotlin.math.sin(fallingStar.horizontalDirection) * horizontalDistance
        val offsetZ = -kotlin.math.cos(fallingStar.horizontalDirection) * horizontalDistance
        val skyPos = Vec3(spawnPos.x + 0.5 + offsetX, skyHeight, spawnPos.z + 0.5 + offsetZ)
        fallingStar.setPos(skyPos)
        
        world.addFreshEntity(fallingStar)
        return EntitySpawnResult(listOf(fallingStar))
    }
}
