/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.decor.MistleafFlowerBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class MistleafFlowerBlockEntity(pos: BlockPos, state: BlockState): BlockEntity(CobblemonBlockEntities.MISTLEAF_FLOWER, pos, state) {
    companion object {
        fun serverTick(level: Level, pos: BlockPos, state: BlockState, mistleafFlowerBlockEntity: MistleafFlowerBlockEntity) {
            if (level.isClientSide) return
            if (state.block !is MistleafFlowerBlock) return

            val glowing = state.getValue(MistleafFlowerBlock.GLOWING)

            val wantedState = when (state.`is`(CobblemonBlocks.MISTLEAF)) {
                //The light level and comparator output increases on odds from New to Full, and decreases on the evens from Full to New
                ((level.dayTime().toInt().mod(24000) in 12542..23459) && level.moonPhase == 4) -> 1 //New Moon
                ((level.dayTime().toInt().mod(24000) in 12542..23459) && level.moonPhase == 5) -> 3 //Waxing Crescent
                ((level.dayTime().toInt().mod(24000) in 12542..23459) && level.moonPhase == 6) -> 5 //First Quarter
                ((level.dayTime().toInt().mod(24000) in 12542..23459) && level.moonPhase == 7) -> 7 //Waxing Gibbous
                ((level.dayTime().toInt().mod(24000) in 12542..23459) && level.moonPhase == 0) -> 8 //Full Moon
                ((level.dayTime().toInt().mod(24000) in 12542..23459) && level.moonPhase == 1) -> 6 //Waning Gibbous
                ((level.dayTime().toInt().mod(24000) in 12542..23459) && level.moonPhase == 2) -> 4 // Third Quarter
                ((level.dayTime().toInt().mod(24000) in 12542..23459) && level.moonPhase == 3) -> 2 //Waning Crescent
                else -> 0 //Daytime, and likely dimensions with no concept of day/night cycle or moon phase
            }

            if (glowing != wantedState) {
                level.setBlock(pos, state.setValue(MistleafFlowerBlock.GLOWING, wantedState), Block.UPDATE_ALL)
            }
        }
    }
}