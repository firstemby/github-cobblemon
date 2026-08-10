/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.SilkThreadBlockEntity
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A thin strand of silk anchored to leaves, placed when a Pokemon spawns hanging from a tree.
 * Each strand survives only while the block above it is another strand or a valid anchor,
 * so breaking any part of the column pops everything below it.
 */
@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
class SilkThreadBlock(settings: Properties) : Block(settings), EntityBlock {
    override fun newBlockEntity(pos: BlockPos, state: BlockState) = SilkThreadBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    override fun canSurvive(state: BlockState, world: LevelReader, pos: BlockPos) = isSupport(world.getBlockState(pos.above()))

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        if (direction == Direction.UP && !isSupport(neighborState)) {
            world.scheduleTick(pos, this, 1)
        }
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    override fun tick(state: BlockState, world: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (!state.canSurvive(world, pos)) {
            world.destroyBlock(pos, true)
        }
    }

    override fun entityInside(state: BlockState, world: Level, pos: BlockPos, entity: Entity) {
        if (entity is Projectile && !world.isClientSide) {
            world.destroyBlock(pos, true, entity)
        }
    }

    override fun codec(): MapCodec<out Block> = CODEC

    companion object {
        val CODEC = simpleCodec(::SilkThreadBlock)

        const val MAX_LENGTH = 2

        private val COMMON_LEAVES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "leaves"))
        private val SHAPE = box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0)

        fun isValidAnchor(state: BlockState) = state.`is`(BlockTags.LEAVES) || state.`is`(COMMON_LEAVES)

        fun isSupport(state: BlockState) = state.`is`(CobblemonBlocks.SILK_THREAD) || isValidAnchor(state)
    }
}
