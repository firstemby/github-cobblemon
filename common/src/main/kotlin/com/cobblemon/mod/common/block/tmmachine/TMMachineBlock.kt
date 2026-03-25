/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.tmmachine

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.block.entity.TMMachineBlockEntity
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.playSoundServer
import com.cobblemon.mod.common.util.toVec3d
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.jetbrains.annotations.Nullable

class TMMachineBlock(properties: Properties) : BaseEntityBlock(properties), SimpleWaterloggedBlock {

    companion object {
        val EMPTY: BooleanProperty = BooleanProperty.create("empty")
        val FACING = BlockStateProperties.HORIZONTAL_FACING
        val WATERLOGGED = BlockStateProperties.WATERLOGGED
        val ACTIVE: BooleanProperty = BooleanProperty.create("active")
        val OPEN: BooleanProperty = BooleanProperty.create("open")

        private val SHAPE: VoxelShape = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.375, 1.0)

        private val SHAPE_OPEN: VoxelShape = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.3125, 1.0)

        private val SHAPE_OPEN_NORTH: VoxelShape = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.3125, 1.0),
            Shapes.box(0.0, 0.3125, 0.8125, 1.0, 0.4375, 0.9375),
            Shapes.box(0.0, 0.4375, 0.6875, 1.0, 0.5625, 0.8125),
            Shapes.box(0.0, 0.5625, 0.5625, 1.0, 0.6875, 0.6875),
            Shapes.box(0.0, 0.6875, 0.4375, 1.0, 0.8125, 0.5625),
            Shapes.box(0.0, 0.8125, 0.3125, 1.0, 0.9375, 0.4375),
            Shapes.box(0.0, 0.9375, 0.3125, 1.0, 1.0, 0.375)
        )

        private val SHAPE_OPEN_SOUTH: VoxelShape = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.3125, 1.0),
            Shapes.box(0.0, 0.3125, 0.0625, 1.0, 0.4375, 0.1875),
            Shapes.box(0.0, 0.4375, 0.1875, 1.0, 0.5625, 0.3125),
            Shapes.box(0.0, 0.5625, 0.3125, 1.0, 0.6875, 0.4375),
            Shapes.box(0.0, 0.6875, 0.4375, 1.0, 0.8125, 0.5625),
            Shapes.box(0.0, 0.8125, 0.5625, 1.0, 0.9375, 0.6875),
            Shapes.box(0.0, 0.9375, 0.625, 1.0, 1.0, 0.6875)
        )

        private val SHAPE_OPEN_EAST: VoxelShape = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.3125, 1.0),
            Shapes.box(0.0625, 0.3125, 0.0, 0.1875, 0.4375, 1.0),
            Shapes.box(0.1875, 0.4375, 0.0, 0.3125, 0.5625, 1.0),
            Shapes.box(0.3125, 0.5625, 0.0, 0.4375, 0.6875, 1.0),
            Shapes.box(0.4375, 0.6875, 0.0, 0.5625, 0.8125, 1.0),
            Shapes.box(0.5625, 0.8125, 0.0, 0.6875, 0.9375, 1.0),
            Shapes.box(0.625, 0.9375, 0.0, 0.6875, 1.0, 1.0)
        )

        private val SHAPE_OPEN_WEST: VoxelShape = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.3125, 1.0),
            Shapes.box(0.8125, 0.3125, 0.0, 0.9375, 0.4375, 1.0),
            Shapes.box(0.6875, 0.4375, 0.0, 0.8125, 0.5625, 1.0),
            Shapes.box(0.5625, 0.5625, 0.0, 0.6875, 0.6875, 1.0),
            Shapes.box(0.4375, 0.6875, 0.0, 0.5625, 0.8125, 1.0),
            Shapes.box(0.3125, 0.8125, 0.0, 0.4375, 0.9375, 1.0),
            Shapes.box(0.3125, 0.9375, 0.0, 0.375, 1.0, 1.0)
        )

        val CODEC: MapCodec<TMMachineBlock> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Properties.CODEC.fieldOf("properties").forGetter { it.properties }
            ).apply(instance, ::TMMachineBlock)
        }
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false)
            .setValue(ACTIVE, false)
            .setValue(EMPTY, true))
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, blockEntityType: BlockEntityType<T>): BlockEntityTicker<T>? {
        return createTicker(level, blockEntityType, CobblemonBlockEntities.TM_MACHINE)
    }

    @Nullable
    protected fun <T : BlockEntity> createTicker(level: Level, serverType: BlockEntityType<T>, clientType: BlockEntityType<out TMMachineBlockEntity>): BlockEntityTicker<T>? {
        return if (!level.isClientSide) createTickerHelper(serverType, clientType, TMMachineBlockEntity::serverTick)
        else null
    }

    override fun fallOn(level: Level, state: BlockState, pos: BlockPos, entity: Entity, fallDistance: Float) {
        if (!level.isClientSide && (entity is LivingEntity) &&
            (entity.bbWidth * entity.bbWidth * entity.bbHeight > 0.512F) &&
            (entity.y >= (pos.y + 0.55)) &&
            state.hasProperty(OPEN) && state.getValue(OPEN)
        ) {
            level.setBlockAndUpdate(pos, state.setValue(OPEN, false))
            level.playSoundServer(position = pos.toVec3d(), sound = CobblemonSounds.TM_MACHINE_CLOSE, volume = 1F, pitch = 1F)
        }
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = TMMachineBlockEntity(pos, state)

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        return defaultBlockState()
                .setValue(FACING, context.horizontalDirection.opposite)
                .setValue(WATERLOGGED, context.level.getFluidState(context.clickedPos).type == Fluids.WATER)
                .setValue(EMPTY, true)
                .setValue(ACTIVE, false)
                .setValue(OPEN, false)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (!level.isClientSide) {
            if (player.isCrouching) {
                if (state.hasProperty(OPEN)) {
                    val isOpen = state.getValue(OPEN)
                    level.setBlockAndUpdate(pos, state.setValue(OPEN, !isOpen))
                    level.playSoundServer(
                        position = pos.toVec3d(),
                        sound = if (!isOpen) CobblemonSounds.TM_MACHINE_CLOSE else CobblemonSounds.TM_MACHINE_OPEN,
                        volume = 1F,
                        pitch = 1F
                    )
                }
            } else {
                val serverPlayer = player as? ServerPlayer
                if (serverPlayer != null) {
                    val recipeId = cobblemonResource("tm_machine")
                    val recipeOpt = level.server?.recipeManager?.byKey(recipeId)
                    recipeOpt?.ifPresent { recipe ->
                        if (!serverPlayer.recipeBook.contains(recipe)) {
                            serverPlayer.awardRecipes(listOf(recipe))
                        }
                    }
                }
                val blockEntity = level.getBlockEntity(pos)
                if (blockEntity is TMMachineBlockEntity) {
                    player.openMenu(blockEntity)
                    level.playSoundServer(
                        position = pos.toVec3d(),
                        sound = CobblemonSounds.TM_MACHINE_ON,
                        volume = 0.5F,
                        pitch = 1F
                    )
                    level.gameEvent(player, GameEvent.BLOCK_OPEN, pos)
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, WATERLOGGED, EMPTY, ACTIVE, OPEN)
    }

    override fun codec(): MapCodec<TMMachineBlock> = CODEC

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getFluidState(state: BlockState): FluidState =
        if (state.getValue(WATERLOGGED)) Fluids.WATER.defaultFluidState() else super.getFluidState(state)

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return if (state.getValue(OPEN)) {
            when (state.getValue(FACING)) {
                Direction.NORTH -> SHAPE_OPEN_NORTH
                Direction.SOUTH -> SHAPE_OPEN_SOUTH
                Direction.WEST -> SHAPE_OPEN_WEST
                Direction.EAST -> SHAPE_OPEN_EAST
                else -> Shapes.empty()
            }
        } else SHAPE
    }

    override fun getShape(state: BlockState, getter: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = if (state.getValue(OPEN)) SHAPE_OPEN else SHAPE

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        Containers.dropContentsOnDestroy(state, newState, level, pos)
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun isSignalSource(state: BlockState): Boolean = false

    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int = 0

    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true

    /*override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int {
        val be = level.getBlockEntity(pos) as? TMMachineBlockEntity ?: return 0

        // Have comparator output act like a progress bar when burning because that might be super dope to see without going into the menu
        val scaled = (be.burnProgress.toFloat() / TMMachineBlockEntity.TOTAL_PROCESS_TIME.toFloat() * 15f).toInt()
        return scaled.coerceIn(0, 15)
    }*/

    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int {
        val be = level.getBlockEntity(pos) as? TMMachineBlockEntity ?: return 0

        // Have comparator output act like a progress bar when burning because that might be super dope to see without going into the menu
        if (be.burnActive) {
            val scaled = (be.burnProgress.toFloat() / TMMachineBlockEntity.TOTAL_PROCESS_TIME.toFloat() * 15F).toInt()
            return scaled.coerceIn(0, 15)
        }

        // if batch mode is off but it is ready to craft then show signal of 15
        if (!be.repeatProcess) {
            if (be.isReadyToCraft()) return 15
            else return 0
        }

        // When not burning but idling in batch mode maybe show some cool states? Could be nice
        return when {
            be.isOutputBlocked() -> 8
            else -> 4
        }
    }
}
