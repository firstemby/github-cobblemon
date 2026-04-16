/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.block.ApricornBlock
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState

class ApricornItem(block: ApricornBlock, properties: Properties) : BlockItem(block, properties.useItemDescriptionPrefix()) {
    override fun canPlace(context: BlockPlaceContext, state: BlockState) = context.player?.isCreative != false && super.canPlace(context, state)
}
