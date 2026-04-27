/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.BlockItemStateProperties
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class TypeGemClusterBlock(
    settings: Properties,
    val nextStage: Block,
    val dropItemId: ResourceLocation
) : DirectionalBlock(settings.pushReaction(PushReaction.DESTROY)) {

    companion object {
        val CODEC: MapCodec<TypeGemClusterBlock> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                propertiesCodec(),
                Block.CODEC.fieldOf("nextStage").forGetter { it.nextStage },
                ResourceLocation.CODEC.fieldOf("dropItem").forGetter { it.dropItemId }
            ).apply(instance) { settings, nextStage, dropItemId ->
                TypeGemClusterBlock(settings, nextStage, dropItemId).also {
                    gemToClusterMap[nextStage] = it
                }
            }
        }
        val gemToClusterMap: MutableMap<Block, TypeGemClusterBlock> = mutableMapOf()

        val SHOULD_GROW: BooleanProperty = BooleanProperty.create("should_grow")
        val FACING: DirectionProperty = DirectionalBlock.FACING
        val STAGE: IntegerProperty = IntegerProperty.create("stage", 0, 3)
        val STUNTED: BooleanProperty = BooleanProperty.create("stunted")

        fun clusterFromGemBlock(gemBlock: Block): TypeGemClusterBlock? {
            return gemToClusterMap[gemBlock]
        }
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.UP)
            .setValue(SHOULD_GROW, true)
            .setValue(STAGE, 0)
            .setValue(STUNTED, false)
        )
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val facing = context.clickedFace
        val world = context.level
        val pos = context.clickedPos.relative(facing.opposite)
        val blockBelow = world.getBlockState(pos).block
        val shouldGrow = blockBelow is TypeGemCoreBlock

        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(SHOULD_GROW, shouldGrow)
            .setValue(STAGE, 0)
            .setValue(STUNTED, false)
    }

    override fun isRandomlyTicking(state: BlockState): Boolean = state.getValue(SHOULD_GROW)

    override fun randomTick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        advanceGrowth(state, level, pos, random)
    }

    fun advanceGrowth(state: BlockState, level: LevelAccessor, pos: BlockPos, random: RandomSource) {
        if (!state.getValue(SHOULD_GROW)) return

        val currentStage = state.getValue(STAGE)

        if (currentStage < 3) {
            // Progress through stages regardless of STUNTED
            level.setBlock(pos, state.setValue(STAGE, currentStage + 1), UPDATE_ALL)
            return
        }

        // At STAGE_3
        val isStunted = state.getValue(STUNTED)
        val facing = state.getValue(FACING)

        if (isStunted) {
            level.setBlock(pos, state.setValue(SHOULD_GROW, false), UPDATE_ALL)
            return
        }

        // Check neighboring gem blocks
        val hasConflict = Direction.entries
                .filter { it != facing.opposite }
                .any { dir ->
                    val neighborPos = pos.relative(dir)
                    val neighborState = level.getBlockState(neighborPos)
                    isGem(neighborState)
                }

        if (hasConflict) {
            level.setBlock(
                pos,
                state.setValue(STUNTED, true).setValue(SHOULD_GROW, false),
                UPDATE_ALL
            )
        } else {
            var nextState = nextStage.defaultBlockState()
            if (nextState.hasProperty(FACING)) {
                nextState = nextState.setValue(FACING, facing)
            }
            level.setBlock(pos, nextState, UPDATE_ALL)
            attachDecorativeClusters(level, pos, random)
        }
    }

    private fun isGem(state: BlockState): Boolean {
        return state.`is`(CobblemonBlockTags.TYPE_GEM_BLOCKS)
    }

    private fun attachDecorativeClusters(level: LevelAccessor, gemPos: BlockPos, random: RandomSource) {
        val openDirections = Direction.entries.filter { direction ->
            level.getBlockState(gemPos.relative(direction)).isAir
        }.toMutableList()

        if (openDirections.isEmpty()) {
            return
        }

        val minClusters = maxOf(1, (openDirections.size * 0.75f).toInt())
        val clusterCount = random.nextInt(minClusters, openDirections.size + 1)

        repeat(clusterCount) {
            if (openDirections.isEmpty()) return@repeat

            val index = random.nextInt(openDirections.size)
            val direction = openDirections.removeAt(index)
            val clusterPos = gemPos.relative(direction)
            val stage = random.nextInt(0, 4)

            val clusterState = defaultBlockState()
                .setValue(FACING, direction)
                .setValue(STAGE, stage)
                .setValue(SHOULD_GROW, false) // so we can reduce lag... hopefully
                .setValue(STUNTED, false)

            level.setBlock(clusterPos, clusterState, UPDATE_ALL)
        }
    }

    override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack> {
        val drops = super.getDrops(state, params)

        for (stack in drops) {
            val blockItem = stack.item as? BlockItem ?: continue
            if (blockItem.block !is TypeGemClusterBlock) continue
            val itemPath = BuiltInRegistries.ITEM.getKey(stack.item).path
            if (!itemPath.startsWith("type_gem_cluster_")) continue

            stack.set(
                DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY
                    .with(STAGE, state.getValue(STAGE))
                    .with(SHOULD_GROW, false)
                    .with(STUNTED, state.getValue(STUNTED))
            )
            stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                CustomModelData(state.getValue(STAGE))
            )
        }

        return drops
    }

    override fun getShape(
            state: BlockState,
            level: net.minecraft.world.level.BlockGetter,
            pos: BlockPos,
            context: net.minecraft.world.phys.shapes.CollisionContext
    ): VoxelShape {
        val stage = state.getValue(STAGE)
        val facing = state.getValue(FACING)

        val (min, max) = when (stage) {
            0 -> 6.0 to 5.0  // type_gem_bud_small_* (4px wide, 5px protrusion)
            1 -> 5.0 to 6.0  // type_gem_bud_medium_* (6px wide, 6px protrusion)
            2 -> 4.0 to 7.0  // type_gem_bud_large_* (8px wide, 7px protrusion)
            3 -> 3.0 to 8.0  // Fully grown cluster (10px wide, 8px protrusion)
            else -> return Shapes.empty()
        }

        return when (facing) {
            Direction.UP    -> Shapes.box(min / 16.0, 0.0, min / 16.0, (16 - min) / 16.0, max / 16.0, (16 - min) / 16.0)
            Direction.DOWN  -> Shapes.box(min / 16.0, (16 - max) / 16.0, min / 16.0, (16 - min) / 16.0, 1.0, (16 - min) / 16.0)
            Direction.NORTH -> Shapes.box(min / 16.0, min / 16.0, (16 - max) / 16.0, (16 - min) / 16.0, (16 - min) / 16.0, 1.0)
            Direction.SOUTH -> Shapes.box(min / 16.0, min / 16.0, 0.0, (16 - min) / 16.0, (16 - min) / 16.0, max / 16.0)
            Direction.WEST  -> Shapes.box((16 - max) / 16.0, min / 16.0, min / 16.0, 1.0, (16 - min) / 16.0, (16 - min) / 16.0)
            Direction.EAST  -> Shapes.box(0.0, min / 16.0, min / 16.0, max / 16.0, (16 - min) / 16.0, (16 - min) / 16.0)
        }
    }

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        val facing = state.getValue(FACING)
        if (direction == facing.opposite && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState()
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    }

    override fun mirror(state: BlockState, mirror: Mirror): BlockState {
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val supportPos = pos.relative(state.getValue(FACING).opposite)
        val supportBlock = level.getBlockState(supportPos).block
        return CobblemonBlocks.typeGemBlocks().containsValue(supportBlock) || (supportBlock == CobblemonBlocks.TYPE_GEM_CORE)
    }

    override fun propagatesSkylightDown(state: BlockState, level: net.minecraft.world.level.BlockGetter, pos: BlockPos): Boolean = true

    override fun getShadeBrightness(state: BlockState, level: net.minecraft.world.level.BlockGetter, pos: BlockPos): Float = 1.0F

    override fun useShapeForLightOcclusion(state: BlockState): Boolean = false


    override fun codec(): MapCodec<out DirectionalBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(FACING, SHOULD_GROW, STAGE, STUNTED)
    }
}
