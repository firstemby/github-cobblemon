/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.fishing.SpawnBait
import com.cobblemon.mod.common.api.fishing.SpawnBaitEffects
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class LureCakeBlockEntity(
    pos: BlockPos,
    state: BlockState
) : CakeBlockEntity(CobblemonBlockEntities.LURE_CAKE, pos, state) {
    override val maxBites: Int
        get() = 20

    /**
     * Combine all the [SpawnBait.Effect] values from the [baitEffectsComponent] data.
     */
    fun getBaitEffectsFromLureCake(): List<SpawnBait.Effect> {
        return baitEffectsComponent?.effects?.mapNotNull(SpawnBaitEffects::getFromIdentifier)?.flatMap { it.effects }.orEmpty()
    }
}