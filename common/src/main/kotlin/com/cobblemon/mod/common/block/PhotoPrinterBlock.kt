/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.block.entity.PhotoPrinterBlockEntity
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.Containers
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class PhotoPrinterBlock(properties: Properties) : BaseEntityBlock(properties) {
    companion object {
        val FACING = BlockStateProperties.HORIZONTAL_FACING
        val PART: EnumProperty<PrinterPart> = EnumProperty.create("part", PrinterPart::class.java)
        val STATE: EnumProperty<PrinterState> = EnumProperty.create("state", PrinterState::class.java)
        private val BOTTOM_SHAPE: VoxelShape = Shapes.box(1.0 / 16.0, 0.0, 1.0 / 16.0, 15.0 / 16.0, 1.0, 15.0 / 16.0)
        private val TOP_SHAPE: VoxelShape = Shapes.box(1.0 / 16.0, 0.0, 1.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0)

        val CODEC: MapCodec<PhotoPrinterBlock> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Properties.CODEC.fieldOf("properties").forGetter { it.properties }
            ).apply(instance, ::PhotoPrinterBlock)
        }
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, PrinterPart.BOTTOM)
                .setValue(STATE, PrinterState.OFF)
        )
    }

    override fun codec(): MapCodec<PhotoPrinterBlock> = CODEC

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return if (state.getValue(PART) == PrinterPart.BOTTOM) PhotoPrinterBlockEntity(pos, state) else null
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val abovePos = context.clickedPos.above()
        val level = context.level
        if (!level.getBlockState(abovePos).canBeReplaced(context) || level.isOutsideBuildHeight(abovePos)) {
            return null
        }

        return defaultBlockState()
            .setValue(FACING, context.horizontalDirection.opposite)
            .setValue(PART, PrinterPart.BOTTOM)
            .setValue(STATE, PrinterState.OFF)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        level.setBlock(pos.above(), state.setValue(PART, PrinterPart.TOP), UPDATE_ALL)
        level.blockUpdated(pos, Blocks.AIR)
        state.updateNeighbourShapes(level, pos, UPDATE_ALL)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, PART, STATE)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    }

    override fun mirror(state: BlockState, mirror: Mirror): BlockState {
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (state.getValue(PART) != PrinterPart.BOTTOM) return null
        return createTickerHelper(type, CobblemonBlockEntities.PHOTO_PRINTER, PhotoPrinterBlockEntity::tick)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        if (!level.isClientSide) {
            val blockEntity = level.getBlockEntity(getBasePosition(state, pos)) as? PhotoPrinterBlockEntity ?: return InteractionResult.CONSUME
            (player as? ServerPlayer)?.openMenu(blockEntity)
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (state.`is`(newState.block)) {
            return
        }

        if (state.getValue(PART) == PrinterPart.TOP) {
            super.onRemove(state, level, pos, newState, movedByPiston)
            return
        }

        val blockEntity = level.getBlockEntity(pos) as? PhotoPrinterBlockEntity
        blockEntity?.printingStack?.takeIf { !it.isEmpty }?.let { stack ->
            Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack.copy())
        }
        Containers.dropContentsOnDestroy(state, newState, level, pos)
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val belowPos = pos.below()
        val belowState = level.getBlockState(belowPos)
        return if (state.getValue(PART) == PrinterPart.BOTTOM) {
            belowState.isFaceSturdy(level, belowPos, Direction.UP)
        } else {
            belowState.`is`(this) && belowState.getValue(PART) == PrinterPart.BOTTOM
        }
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        val part = state.getValue(PART)
        val expectedDirection = if (part == PrinterPart.BOTTOM) Direction.UP else Direction.DOWN
        if (direction == expectedDirection && (!neighborState.`is`(this) || neighborState.getValue(PART) == part)) {
            return Blocks.AIR.defaultBlockState()
        }
        return state
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return if (state.getValue(PART) == PrinterPart.BOTTOM) BOTTOM_SHAPE else TOP_SHAPE
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    fun getBasePosition(state: BlockState, pos: BlockPos): BlockPos {
        return if (state.getValue(PART) == PrinterPart.BOTTOM) pos else pos.below()
    }

    enum class PrinterPart(private val serializedName: String) : StringRepresentable {
        TOP("top"),
        BOTTOM("bottom");

        override fun getSerializedName(): String = serializedName
    }

    enum class PrinterState(private val serializedName: String) : StringRepresentable {
        OFF("off"),
        ON("on"),
        PRINTING("printing");

        override fun getSerializedName(): String = serializedName
    }
}

