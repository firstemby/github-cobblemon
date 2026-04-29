/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.block.entity.DiscShelfBlockEntity
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.Containers
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.VoxelShape

class DiscShelfBlock(properties: Properties) : BaseEntityBlock(properties) {
    companion object {
        val CODEC: MapCodec<DiscShelfBlock> = simpleCodec(::DiscShelfBlock)
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
        val SLOT_OCCUPIED_PROPERTIES = (0 until 14).map {
            BooleanProperty.create("slot_${it}_occupied")
        }
    }

    init {
        val baseState = stateDefinition.any().setValue(FACING, Direction.NORTH)
        registerDefaultState(SLOT_OCCUPIED_PROPERTIES.fold(baseState) { state, prop ->
            state.setValue(prop, false)
        })
    }

    override fun codec() = CODEC

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return DiscShelfBlockEntity(pos, state)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
        SLOT_OCCUPIED_PROPERTIES.forEach { builder.add(it) }
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState {
        return defaultBlockState().setValue(FACING, ctx.horizontalDirection.opposite)
    }

    override fun useWithoutItem(
        state: BlockState, level: Level, pos: BlockPos,
        player: Player, hit: BlockHitResult
    ): InteractionResult {
        val entity = level.getBlockEntity(pos) as? DiscShelfBlockEntity ?: return InteractionResult.PASS
        return entity.handleUseWithoutItem(state, level, pos, player, hit)
    }

    override fun useItemOn(
        stack: ItemStack, state: BlockState, level: Level, pos: BlockPos,
        player: Player, hand: InteractionHand, hit: BlockHitResult
    ): ItemInteractionResult {
        val entity = level.getBlockEntity(pos) as? DiscShelfBlockEntity ?: return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
        return entity.handleUseItem(stack, state, level, pos, player, hit)
    }

    override fun hasAnalogOutputSignal(state: BlockState) = true

    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int {
        val entity = level.getBlockEntity(pos) as? DiscShelfBlockEntity
        return entity?.items?.count { !it.isEmpty }?.coerceIn(0, 14) ?: 0
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block)) {
            if (!level.isClientSide && !movedByPiston) {
                val entity = level.getBlockEntity(pos) as? DiscShelfBlockEntity
                if (entity != null) {
                    for (index in entity.items.indices) {
                        val stack = entity.items[index]
                        if (!stack.isEmpty) {
                            Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack)
                            entity.items[index] = ItemStack.EMPTY
                        }
                    }
                    level.updateNeighbourForOutputSignal(pos, this)
                }
            }
            super.onRemove(state, level, pos, newState, movedByPiston)
        } else {
            super.onRemove(state, level, pos, newState, movedByPiston)
        }
    }

    override fun getShape(
        state: BlockState, level: BlockGetter,
        pos: BlockPos, context: net.minecraft.world.phys.shapes.CollisionContext
    ): VoxelShape = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
}
