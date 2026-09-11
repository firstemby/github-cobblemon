/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState

interface BlockExtensions { // Exist only to have better Sound Calls
    fun getSoundType(state: BlockState, level: BlockGetter, pos: BlockPos): SoundType
    fun getExplosionResistance(state: BlockState, level: BlockGetter, pos: BlockPos): Float
}