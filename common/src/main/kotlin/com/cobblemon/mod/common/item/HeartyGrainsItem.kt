/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.block.HeartyGrainsBlock
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemNameBlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.PlaceOnWaterBlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

class HeartyGrainsItem(block: HeartyGrainsBlock) : ItemNameBlockItem(block, Properties()) {

    init {
        Cobblemon.implementation.registerCompostable(this, 0.65F)
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val world = context.level
        val clickedPos = context.clickedPos
        val player = context.player ?: return InteractionResult.FAIL

        if (world.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val clickedBlockState = world.getBlockState(clickedPos)
        val fluidState = world.getFluidState(clickedPos)
        val blockAbove = world.getBlockState(clickedPos.above())
        val fluidStateAbove = world.getFluidState(clickedPos.above())
        val block2Above = world.getBlockState(clickedPos.above(2))
        val fluidState2Above = world.getFluidState(clickedPos.above(2))
        val blockBelow = world.getBlockState(clickedPos.below())


        // 1. Prioritize source water block
        if (fluidState.`is`(FluidTags.WATER) && fluidState.isSource) {
            // Check if it's 1 block deep water
            if (blockAbove.isAir && !blockBelow.fluidState.isSource) {
                if (world.setBlock(clickedPos, this.block.defaultBlockState().setValue(HeartyGrainsBlock.WATERLOGGED, true), Block.UPDATE_CLIENTS)) {
                    context.itemInHand.shrink(1)
                    return InteractionResult.SUCCESS
                }
            }

            // Fails if not 1 block deep
            return InteractionResult.FAIL
        }

        // 2. Check for farmland or mud
        if (clickedBlockState.`is`(Blocks.FARMLAND) || clickedBlockState.`is`(Blocks.MUD) || (fluidStateAbove.`is`(FluidTags.WATER) && fluidStateAbove.isSource && block2Above.isAir)) {
            if (world.setBlock(clickedPos.above(), this.block.defaultBlockState(), Block.UPDATE_CLIENTS)) {
                context.itemInHand.shrink(1)
                return InteractionResult.SUCCESS
            }
        }

        // 3. Default: fail if neither condition matches
        return InteractionResult.FAIL
    }



    private fun placeOnLand(context: UseOnContext, targetPos: BlockPos): InteractionResult {
        val world = context.level
        val targetState = this.block.defaultBlockState()

        if (world.setBlock(targetPos, targetState, Block.UPDATE_CLIENTS)) {
            context.itemInHand.shrink(1)
            return InteractionResult.SUCCESS
        }
        return InteractionResult.FAIL
    }

    private fun placeInWater(context: UseOnContext, targetPos: BlockPos): InteractionResult {
        val world = context.level
        val targetState = this.block.defaultBlockState().setValue(HeartyGrainsBlock.WATERLOGGED, true)

        if (world.setBlock(targetPos, targetState, Block.UPDATE_CLIENTS)) {
            context.itemInHand.shrink(1)
            return InteractionResult.SUCCESS
        }
        return InteractionResult.FAIL
    }


    // todo is this needed like this? should it be simpler?
    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val blockHitResult = PlaceOnWaterBlockItem.getPlayerPOVHitResult(world, user, ClipContext.Fluid.ANY)
        val stack = user.getItemInHand(hand)

        if (world.isClientSide) {
            return InteractionResultHolder.success(stack)
        }

        val clickedPos = blockHitResult.blockPos
        val clickedBlockState = world.getBlockState(clickedPos)
        val fluidState = world.getFluidState(clickedPos)

        // 1. Prioritize source water block
        if (fluidState.`is`(FluidTags.WATER) && fluidState.isSource) {
            val blockAbove = world.getBlockState(clickedPos.above())
            val blockBelow = world.getBlockState(clickedPos.below())

            // Check if it's 1 block deep water
            if (blockAbove.isAir && !blockBelow.fluidState.isSource) {
                if (world.setBlock(clickedPos, this.block.defaultBlockState().setValue(HeartyGrainsBlock.WATERLOGGED, true), Block.UPDATE_CLIENTS)) {
                    stack.shrink(1)
                    return InteractionResultHolder.success(stack)
                }
            }

            // Fails if not 1 block deep
            return InteractionResultHolder.fail(stack)
        }

        // 2. Check for farmland or mud
        if (clickedBlockState.`is`(Blocks.FARMLAND) || clickedBlockState.`is`(Blocks.MUD)) {
            if (world.setBlock(clickedPos.above(), this.block.defaultBlockState(), Block.UPDATE_CLIENTS)) {
                stack.shrink(1)
                return InteractionResultHolder.success(stack)
            }
        }

        // 3. Default: fail if neither condition matches
        return InteractionResultHolder.fail(stack)
    }

}