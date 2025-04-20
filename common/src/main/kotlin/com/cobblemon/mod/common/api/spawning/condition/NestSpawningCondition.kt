/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.condition

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.spawning.position.NestSpawnablePosition
import com.cobblemon.mod.common.util.Merger
import net.minecraft.world.level.block.Block

/**
 * A spawning condition that applies to [NestSpawnablePosition]s.
 *
 * @author Hiroku
 * @since February 3rd, 2024
 */
class NestSpawningCondition: SpawningCondition<NestSpawnablePosition>() {
    override fun spawnablePositionClass() = NestSpawnablePosition::class.java

    var neededNearbyBlocks: MutableList<RegistryLikeCondition<Block>>? = null

    override fun fits(spawnablePosition: NestSpawnablePosition): Boolean {
        if (!super.fits(spawnablePosition)) {
            return false
        } else if (neededNearbyBlocks != null && neededNearbyBlocks!!.none { cond -> spawnablePosition.nearbyBlockTypes.any { cond.fits(it, spawnablePosition.blockRegistry) } }) {
            return false
        }
        return true
    }

    override fun copyFrom(other: SpawningCondition<*>, merger: Merger) {
        super.copyFrom(other, merger)
        if (other is NestSpawningCondition) {
            neededNearbyBlocks = merger.merge(neededNearbyBlocks, other.neededNearbyBlocks)?.toMutableList()
        }
    }

    override fun isValid(): Boolean {
        val containsNullValues = neededNearbyBlocks != null && neededNearbyBlocks!!.any {it == null}
        return super.isValid() && !containsNullValues
    }

    companion object {
        const val NAME = "nest"
    }
}