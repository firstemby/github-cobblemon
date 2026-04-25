/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.habitat

import com.cobblemon.mod.common.CobblemonBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class HabitatBlock(properties: Properties) : Block(properties), EntityBlock {
    var level: Level? = null
    var pos: BlockPos? = null

    companion object {
        val CANCELS_REGULAR_SPAWNS = BooleanProperty.create("cancels_regular_spawns")
        val ACTIVATED_STYLE = BooleanProperty.create("activated_style")
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(CANCELS_REGULAR_SPAWNS, false)
            .setValue(ACTIVATED_STYLE, true)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(FACING, CANCELS_REGULAR_SPAWNS, ACTIVATED_STYLE)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return HabitatBlockEntity(pos, state)
    }

    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState {
        return defaultBlockState()
            .setValue(FACING, blockPlaceContext.horizontalDirection.opposite)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (!level.isClientSide && type == CobblemonBlockEntities.HABITAT_BLOCK) {
            @Suppress("UNCHECKED_CAST")
            HabitatBlockEntity.TICKER as BlockEntityTicker<T>
        } else {
            null
        }
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos) as? HabitatBlockEntity ?: return InteractionResult.PASS
            return be.onUse(player, hitResult)
        }
        return InteractionResult.SUCCESS
    }

    override fun getDrops(state: BlockState, params: LootParams.Builder): MutableList<ItemStack> {
        val blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) as? HabitatBlockEntity
        val player = params.getOptionalParameter(LootContextParams.THIS_ENTITY) as? Player

        if (player?.isCreative == true) return mutableListOf()

        val mimic = blockEntity?.mimickedState?.block ?: return mutableListOf()
        return mutableListOf(ItemStack(mimic))
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.INVISIBLE
    }

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    }

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    }

    override fun getVisualShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    }

    override fun getOcclusionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape? {
        return Shapes.empty()
    }

    override fun useShapeForLightOcclusion(state: BlockState): Boolean {
        return false
    }

    override fun skipRendering(state: BlockState, adjacentBlockState: BlockState, side: Direction): Boolean {
        return false
    }

    override fun spawnDestroyParticles(level: Level, player: Player, pos: BlockPos, state: BlockState) {
        val blockEntity = level.getBlockEntity(pos) as? HabitatBlockEntity
        val mimicState = blockEntity?.mimickedState ?: return
        mimicState.block.spawnDestroyParticles(level, player, pos, mimicState)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        val blockEntity = level.getBlockEntity(pos) as? HabitatBlockEntity
        blockEntity?.updateLevelAndPos(level, pos)
    }

    override fun getSoundType(state: BlockState): SoundType {
        val level = this.level ?: return super.getSoundType(state)
        val pos = this.pos ?: return super.getSoundType(state)
        val blockEntity = level.getBlockEntity(pos) as? HabitatBlockEntity
        val mimicState = blockEntity?.mimickedState ?: return super.getSoundType(state)
        return mimicState.soundType
    }
}
