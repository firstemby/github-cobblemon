/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.dispenser.DispenseItemBehavior
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DispenserBlock
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult

class SaccharineHoneyLogBlock(properties: Properties) : RotatedPillarBlock(properties) {
    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult? {
        val itemStack = player.getItemInHand(hand)
        val waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER).item
        if (itemStack.`is`(waterBottle)) {
            if (!level.isClientSide) {
                // Replace the honey with the block variant
                val newState = CobblemonBlocks.SACCHARINE_LOG.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS))
                SaccharineLogBlock.changeLogType(level, pos, newState, player, itemStack)
            }
            return ItemInteractionResult.SUCCESS
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
    }

    companion object {
        val AXIS: EnumProperty<Direction.Axis> = BlockStateProperties.AXIS

        fun createBehavior(): DispenseItemBehavior {
            return DispenseItemBehavior { source, stack ->
                val level = source.level
                val pos = source.pos.relative(source.state.getValue(DispenserBlock.FACING))
                val blockState = level.getBlockState(pos)

                val waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER).item

                if (blockState.block is SaccharineHoneyLogBlock && stack.`is`(waterBottle)) {
                    val newState = CobblemonBlocks.SACCHARINE_LOG.defaultBlockState()
                        .setValue(AXIS, blockState.getValue(AXIS))
                    SaccharineLogBlock.changeLogTypeDispenser(level, pos, newState, stack, source)
                }
                stack
            }
        }
    }
}
