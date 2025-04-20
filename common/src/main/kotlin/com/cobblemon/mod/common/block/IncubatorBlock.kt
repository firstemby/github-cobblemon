/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.block.entity.EggBlockEntity
import com.cobblemon.mod.common.block.entity.IncubatorBlockEntity
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.AirItem
import net.minecraft.world.item.EggItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.PickaxeItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class IncubatorBlock(settings: Properties) : BaseEntityBlock(settings) {

    companion object {
        val FACING: DirectionProperty = DirectionProperty.create("facing", { dir -> dir.axis.isHorizontal })
        val HAS_EGG: BooleanProperty = BooleanProperty.create("has_egg")
        val CODEC: MapCodec<IncubatorBlock> = simpleCodec(::IncubatorBlock) as MapCodec<IncubatorBlock>
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, net.minecraft.core.Direction.NORTH)
            .setValue(HAS_EGG, false)
        )
    }

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: net.minecraft.world.phys.shapes.CollisionContext): VoxelShape {
        return Shapes.or(
                // Base
                Shapes.box(2.0 / 16.0, 0.0, 2.0 / 16.0, 14.0 / 16.0, 3.0 / 16.0, 14.0 / 16.0),
                // Top
                Shapes.box(2.0 / 16.0, 14.0 / 16.0, 2.0 / 16.0, 14.0 / 16.0, 1.0, 14.0 / 16.0),
                // Unified Glass Section (Covers all four glass sides as one box)
                Shapes.box(2.5 / 16.0, 3.0 / 16.0, 2.5 / 16.0, 13.5 / 16.0, 14.0 / 16.0, 13.5 / 16.0)
        )
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(FACING, ctx.horizontalDirection.opposite)
    }

    override fun setPlacedBy(
            level: Level,
            pos: BlockPos,
            state: BlockState,
            placer: LivingEntity?,
            stack: ItemStack
    ) {
        val blockEntity = level.getBlockEntity(pos) as? IncubatorBlockEntity
        if (placer is Player) {
            blockEntity?.ownerUUID = placer.uuid
        }
        super.setPlacedBy(level, pos, state, placer, stack)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, HAS_EGG)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): IncubatorBlockEntity {
        return IncubatorBlockEntity(pos, state)
    }

    override fun useWithoutItem(
        blockState: BlockState,
        level: Level,
        blockPos: BlockPos,
        player: Player,
        blockHitResult: BlockHitResult
    ): InteractionResult {
        // Ensure this logic only runs on the server
        if (level.isClientSide) {
            return InteractionResult.SUCCESS // Client-side only needs to show the hand animation
        }

        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.FAIL // Cast safely to ServerPlayer

        val entity = level.getBlockEntity(blockPos) as? IncubatorBlockEntity ?: return InteractionResult.FAIL

        val egg = entity.getItem(0).get(CobblemonItemComponents.EGG_DATA)?.egg

        // if the player interacting with the nest is not the owner of the nest we do not want them taking the egg or hatching
        if (egg != null && entity.ownerUUID != player.uuid && entity.ownerUUID != null){
            return InteractionResult.FAIL
        }

        // if the parent owner ID is null then something is wrong (maybe something happened to one of the parent datas)
        if (egg != null && egg?.parentOne?.getOwnerUUID() == null || egg?.parentTwo?.getOwnerUUID() == null) {
            return entity.onUse(blockState, level, blockPos, serverPlayer, InteractionHand.MAIN_HAND, blockHitResult)
        }

        // if the person interacting is not the owner of one of the parents
        if (egg != null && player.uuid != egg?.parentOne?.getOwnerUUID() || player.uuid != egg?.parentTwo?.getOwnerUUID()) {
            return InteractionResult.FAIL
        }

        return entity.onUse(blockState, level, blockPos, serverPlayer, InteractionHand.MAIN_HAND, blockHitResult)
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean {
        return true
    }

    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int {
        val blockEntity = level.getBlockEntity(pos) as? IncubatorBlockEntity
        return blockEntity?.getComparatorOutput() ?: 0
    }


    override fun getDestroyProgress(state: BlockState, player: Player, level: BlockGetter, pos: BlockPos): Float {
        // Retrieve the block entity at the given position
        val blockEntity = level.getBlockEntity(pos) as? IncubatorBlockEntity ?: return super.getDestroyProgress(state, player, level, pos)

        // Check if the player is the owner of the nest
        val isOwner = blockEntity.ownerUUID == player.uuid

        // Check if there's an egg present in the nest
        val hasEgg = !blockEntity.getItem(0).isEmpty

        return if (!isOwner && hasEgg) {
            // Reduce destroy speed by 80%, making it 5 times harder to break
            super.getDestroyProgress(state, player, level, pos) * 0.2f
        } else {
            // Use the default destroy speed
            super.getDestroyProgress(state, player, level, pos)
        }
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val entity = level.getBlockEntity(pos) as? IncubatorBlockEntity
        val itemStack = player.mainHandItem

        entity?.let {
            if (!player.isCreative) {
                it.dropItem(level, pos, state)
                if (itemStack.item is PickaxeItem) {
                    val itemEntity = ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.INCUBATOR.defaultInstance)
                    level.addFreshEntity(itemEntity)
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }

    override fun <T : BlockEntity?> getTicker(level: Level, blockState: BlockState, blockEntityType: BlockEntityType<T>): BlockEntityTicker<T>? {
        return createTickerHelper(blockEntityType, CobblemonBlockEntities.INCUBATOR, IncubatorBlockEntity.TICKER)
    }
}
