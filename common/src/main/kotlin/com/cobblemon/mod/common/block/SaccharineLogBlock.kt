/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class SaccharineLogBlock(properties: Properties) : RotatedPillarBlock(properties) {

    init {
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(AXIS, Direction.Axis.Y)
        )
    }

    override fun getShape(
            state: BlockState,
            level: BlockGetter,
            pos: BlockPos,
            context: CollisionContext
    ): VoxelShape {
        return SHAPE
    }

    override fun getCollisionShape(
            state: BlockState,
            level: BlockGetter,
            pos: BlockPos,
            context: CollisionContext
    ): VoxelShape {
        if (context is EntityCollisionContext && (context.entity as? ItemEntity)?.item?.`is`(CobblemonItemTags.APRICORNS) == true) {
            return Shapes.empty()
        }
        return super.getCollisionShape(state, level, pos, context)
    }

    override fun updateShape(
            state: BlockState,
            direction: Direction,
            neighborState: BlockState,
            level: LevelAccessor,
            pos: BlockPos,
            neighborPos: BlockPos
    ): BlockState {
        return if (!state.canSurvive(level, pos)) {
            Blocks.AIR.defaultBlockState()
        } else {
            super.updateShape(state, direction, neighborState, level, pos, neighborPos)
        }
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(AXIS)
    }

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
        if (itemStack.`is`(Items.HONEY_BOTTLE)) {
            if (!level.isClientSide) {
                // Replace the block with the honey variant
                val newState = CobblemonBlocks.SACCHARINE_HONEY_LOG.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS))
                level.setBlock(pos, newState, 3)

                // Consume honey bottle
                if (!player.isCreative) {
                    itemStack.shrink(1)

                    // Give glass bottle
                    val glassBottle = ItemStack(Items.GLASS_BOTTLE)
                    if (!player.addItem(glassBottle)) {
                        player.drop(glassBottle, false)
                    }
                }
            }
            return ItemInteractionResult.SUCCESS
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
    }


    override fun isPathfindable(
            state: BlockState,
            type: PathComputationType
    ): Boolean = false

    companion object {
        val AXIS: EnumProperty<Direction.Axis> = BlockStateProperties.AXIS

        private val SHAPE: VoxelShape = Shapes.block()
    }
}
