/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.block.VivichokeBlock
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level

class VivichokeItem(block: VivichokeBlock, properties: Properties) : BlockItem(block, properties) {
    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResult {
        val blockHitResult = getPlayerPOVHitResult(world, user, ClipContext.Fluid.SOURCE_ONLY)
        val blockHitResult2 = blockHitResult.withPosition(blockHitResult.blockPos.above())
        return super.useOn(UseOnContext(user, hand, blockHitResult2))
    }
}
