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
import com.cobblemon.mod.common.block.entity.IncubatorBlockEntity
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.client.gui.PartyOverlay.Companion.state
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.PickaxeItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class NestBlock(val variant: NestVariant, properties: Properties) : BaseEntityBlock(properties), SimpleWaterloggedBlock {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(BlockStateProperties.WATERLOGGED, false)
        )
    }

    enum class NestVariant(val id: String) : StringRepresentable {
        STICK("nest_stick"),
        LEAF("nest_leaf"),
        STONE("nest_stone"),
        DEEPSLATE("nest_deepslate"),
        BASALT("nest_basalt"),
        SNOW("nest_snow"),
        NETHER("nest_nether"),
        SOUL_SAND("nest_soul_sand"),
        WARM_OCEAN("nest_warm_ocean"),
        SEAGRASS("nest_seagrass"),
        LUSH("nest_lush"),
        HAY("nest_hay"),
        SAND("nest_sand"),
        RED_SAND("nest_red_sand"),
        CHORUS("nest_chorus"),

        CAVE("cave_nest"),
        //NETHER("nether_nest"),
        WATER("water_nest"),
        BASIC("basic_nest");

        override fun getSerializedName() = id
    }

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: net.minecraft.world.phys.shapes.CollisionContext): VoxelShape {
        return when (variant) {
            NestVariant.STICK -> Shapes.or(
                    Shapes.box(3.0 / 16.0, 0.2 / 16.0, 3.0 / 16.0, 13.0 / 16.0, 4.2 / 16.0, 13.0 / 16.0), // Base
                    Shapes.box(2.8 / 16.0, 0.0 / 16.0, 2.8 / 16.0, 13.2 / 16.0, 4.4 / 16.0, 13.2 / 16.0), // Hat layer
                    Shapes.box(12.0 / 16.0, 3.0 / 16.0, 11.5 / 16.0, 12.0 / 16.0, 6.0 / 16.0, 15.5 / 16.0), // Stick 1
                    Shapes.box(13.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0, 15.0 / 16.0, 3.0 / 16.0, 12.0 / 16.0), // Stick 2
                    Shapes.box(1.5 / 16.0, 2.0 / 16.0, 12.0 / 16.0, 4.5 / 16.0, 6.0 / 16.0, 12.0 / 16.0), // Stick 3
                    Shapes.box(1.0 / 16.0, 1.0 / 16.0, 4.0 / 16.0, 4.0 / 16.0, 1.0 / 16.0, 9.0 / 16.0), // Stick 4
                    Shapes.box(4.0 / 16.0, 2.0 / 16.0, 13.0 / 16.0, 11.0 / 16.0, 2.0 / 16.0, 15.0 / 16.0), // Stick 5
                    Shapes.box(9.0 / 16.0, 3.0 / 16.0, 1.0 / 16.0, 15.0 / 16.0, 3.0 / 16.0, 6.0 / 16.0), // Stick 6
                    Shapes.box(6.0 / 16.0, 3.0 / 16.0, 0.5 / 16.0, 6.0 / 16.0, 6.0 / 16.0, 4.5 / 16.0) // Stick 7
            )
            NestVariant.LEAF -> Shapes.or(
                    Shapes.box(3.0 / 16.0, 0.2 / 16.0, 3.0 / 16.0, 13.0 / 16.0, 4.2 / 16.0, 13.0 / 16.0), // Base of the nest
                    Shapes.box(2.8 / 16.0, 0.0, 2.8 / 16.0, 13.2 / 16.0, 4.4 / 16.0, 13.2 / 16.0), // Hat layer
                    Shapes.box(11.975 / 16.0, 6.0 / 16.0, 12.0 / 16.0, 11.975 / 16.0, 10.0 / 16.0, 15.0 / 16.0), // Leaf 1
                    Shapes.box(12.025 / 16.0, 5.525 / 16.0, 6.0 / 16.0, 16.025 / 16.0, 5.525 / 16.0, 9.0 / 16.0), // Leaf 2
                    Shapes.box(12.025 / 16.0, 8.0 / 16.0, 4.0 / 16.0, 12.025 / 16.0, 12.0 / 16.0, 7.0 / 16.0), // Leaf 3
                    Shapes.box(0.0 / 16.0, 5.0 / 16.0, 11.975 / 16.0, 3.0 / 16.0, 9.0 / 16.0, 11.975 / 16.0), // Leaf 4
                    Shapes.box(5.25 / 16.0, 7.75 / 16.0, 12.025 / 16.0, 9.25 / 16.0, 10.75 / 16.0, 12.025 / 16.0), // Leaf 5
                    Shapes.box(6.025 / 16.0, 10.0 / 16.0, 2.25 / 16.0, 6.025 / 16.0, 14.0 / 16.0, 5.25 / 16.0), // Leaf 6
                    Shapes.box(0.525 / 16.0, 8.0 / 16.0, 6.5 / 16.0, 3.525 / 16.0, 12.0 / 16.0, 6.5 / 16.0), // Leaf 7
                    Shapes.box(13.975 / 16.0, 3.0 / 16.0, 4.0 / 16.0, 15.975 / 16.0, 3.0 / 16.0, 5.0 / 16.0) // Leaf 8
            )
            NestVariant.STONE -> Shapes.or(
                    Shapes.box(3.0 / 16.0, 0.05 / 16.0, 3.0 / 16.0, 13.0 / 16.0, 3.05 / 16.0, 13.0 / 16.0),
                    Shapes.box(10.0 / 16.0, 0.0 / 16.0, 1.0 / 16.0, 14.0 / 16.0, 4.0 / 16.0, 5.0 / 16.0),
                    Shapes.box(11.0 / 16.0, 0.0 / 16.0, 5.0 / 16.0, 14.0 / 16.0, 4.0 / 16.0, 9.0 / 16.0),
                    Shapes.box(1.0 / 16.0, -0.1 / 16.0, 7.0 / 16.0, 5.0 / 16.0, 4.9 / 16.0, 15.0 / 16.0),
                    Shapes.box(5.0 / 16.0, -0.1 / 16.0, 11.0 / 16.0, 8.0 / 16.0, 4.9 / 16.0, 15.0 / 16.0)
            )
            NestVariant.DEEPSLATE -> Shapes.or(
                    Shapes.box(3.0 / 16.0, 0.05 / 16.0, 3.0 / 16.0, 13.0 / 16.0, 3.05 / 16.0, 13.0 / 16.0),
                    Shapes.box(2.0 / 16.0, 0.0 / 16.0, 2.0 / 16.0, 6.0 / 16.0, 4.0 / 16.0, 5.0 / 16.0),
                    Shapes.box(1.0 / 16.0, -0.05 / 16.0, 5.0 / 16.0, 5.0 / 16.0, 5.95 / 16.0, 9.0 / 16.0),
                    Shapes.box(11.0 / 16.0, -0.1 / 16.0, 7.0 / 16.0, 15.0 / 16.0, 3.9 / 16.0, 15.0 / 16.0),
                    Shapes.box(10.0 / 16.0, -0.1 / 16.0, 14.0 / 16.0, 11.0 / 16.0, 3.9 / 16.0, 15.0 / 16.0),
                    Shapes.box(8.0 / 16.0, -0.125 / 16.0, 11.0 / 16.0, 11.0 / 16.0, 6.875 / 16.0, 14.0 / 16.0),
                    Shapes.box(8.0 / 16.0, 0.0 / 16.0, 1.0 / 16.0, 14.0 / 16.0, 2.0 / 16.0, 5.0 / 16.0)
            )
            NestVariant.BASALT -> Shapes.or(
                    Shapes.box(4.0 / 16.0, -0.1 / 16.0, 4.0 / 16.0, 12.0 / 16.0, 1.9 / 16.0, 12.0 / 16.0),
                    Shapes.box(10.55 / 16.0, 0.0 / 16.0, 5.95 / 16.0, 12.55 / 16.0, 5.0 / 16.0, 10.95 / 16.0),
                    Shapes.box(2.55 / 16.0, 0.0 / 16.0, 10.95 / 16.0, 12.55 / 16.0, 5.0 / 16.0, 12.95 / 16.0),
                    Shapes.box(2.55 / 16.0, 0.0 / 16.0, 9.95 / 16.0, 4.55 / 16.0, 5.0 / 16.0, 10.95 / 16.0),
                    Shapes.box(9.55 / 16.0, -0.2 / 16.0, 9.95 / 16.0, 13.55 / 16.0, 8.8 / 16.0, 13.95 / 16.0),
                    Shapes.box(9.55 / 16.0, -0.2 / 16.0, 0.95 / 16.0, 13.55 / 16.0, 3.8 / 16.0, 4.95 / 16.0),
                    Shapes.box(0.55 / 16.0, -0.2 / 16.0, 5.95 / 16.0, 4.55 / 16.0, 5.8 / 16.0, 9.95 / 16.0),
                    Shapes.box(1.55 / 16.0, -0.2 / 16.0, 2.95 / 16.0, 4.55 / 16.0, 8.8 / 16.0, 5.95 / 16.0),
                    Shapes.box(3.45 / 16.0, -0.2 / 16.0, 11.05 / 16.0, 6.45 / 16.0, 11.8 / 16.0, 14.05 / 16.0),
                    Shapes.box(11.55 / 16.0, -0.1 / 16.0, 4.95 / 16.0, 14.55 / 16.0, 5.9 / 16.0, 7.95 / 16.0)
            )
            NestVariant.SNOW -> Shapes.or(
                    Shapes.box(2.0 / 16.0, 2.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0, 4.0 / 16.0, 11.0 / 16.0),
                    Shapes.box(3.0 / 16.0, 1.1 / 16.0, 3.0 / 16.0, 13.0 / 16.0, 6.1 / 16.0, 13.0 / 16.0),
                    Shapes.box(5.0 / 16.0, 1.0 / 16.0, 5.0 / 16.0, 15.0 / 16.0, 5.0 / 16.0, 15.0 / 16.0),
                    Shapes.box(1.0 / 16.0, 1.0 / 16.0, 8.0 / 16.0, 5.0 / 16.0, 8.0 / 16.0, 14.0 / 16.0),
                    Shapes.box(5.0 / 16.0, 5.0 / 16.0, 11.0 / 16.0, 7.0 / 16.0, 8.0 / 16.0, 14.0 / 16.0),
                    Shapes.box(0.25 / 16.0, 7.6 / 16.0, 10.0 / 16.0, 5.25 / 16.0, 14.6 / 16.0, 10.0 / 16.0),
                    Shapes.box(13.5 / 16.0, 4.6 / 16.0, 6.0 / 16.0, 16.5 / 16.0, 8.6 / 16.0, 6.0 / 16.0),
                    Shapes.box(11.0 / 16.0, 4.6 / 16.0, 12.75 / 16.0, 11.0 / 16.0, 10.6 / 16.0, 16.75 / 16.0),
                    Shapes.box(0.0 / 16.0, 0.0 / 16.0, 0.0 / 16.0, 16.0 / 16.0, 2.0 / 16.0, 16.0 / 16.0)
            )
            NestVariant.CAVE -> Shapes.or(
                    Shapes.box(0.25, 0.0, 0.25, 0.75, 0.375, 0.75),
                    Shapes.box(0.625, 0.0, 0.625, 0.875, 0.5, 0.875)
            )
            NestVariant.NETHER -> Shapes.or(
                    Shapes.box(2.0 / 16.0, 0.0, 2.0 / 16.0, 14.0 / 16.0, 2.0 / 16.0, 14.0 / 16.0), // Main nest base
                    Shapes.box(0.0 / 16.0, 0.0, 6.0 / 16.0, 4.0 / 16.0, 3.0 / 16.0, 13.0 / 16.0), // Left extension
                    Shapes.box(7.0 / 16.0, 0.0, 0.0 / 16.0, 13.0 / 16.0, 3.0 / 16.0, 3.0 / 16.0), // Front extension
                    Shapes.box(11.0 / 16.0, 0.0, 11.0 / 16.0, 16.0 / 16.0, 4.0 / 16.0, 16.0 / 16.0) // Back right extension
            )
            NestVariant.WATER -> Shapes.box(0.2, 0.0, 0.2, 0.8, 0.3, 0.8)
            NestVariant.BASIC -> Shapes.box(0.1, 0.0, 0.1, 0.9, 0.45, 0.9)
            NestVariant.SOUL_SAND -> Shapes.or(
                    Shapes.box(1.0 / 16.0, 0.0, 1.0 / 16.0, 15.0 / 16.0, 2.0 / 16.0, 15.0 / 16.0), // Base layer
                    Shapes.box(2.0 / 16.0, 2.0 / 16.0, 2.0 / 16.0, 13.0 / 16.0, 4.0 / 16.0, 13.0 / 16.0), // Inner rim
                    Shapes.box(12.0 / 16.0, 0.0 / 16.0, 10.0 / 16.0, 16.0 / 16.0, 8.0 / 16.0, 14.0 / 16.0), // Side bump 1
                    Shapes.box(3.0 / 16.0, 0.0 / 16.0, 13.0 / 16.0, 6.0 / 16.0, 7.0 / 16.0, 16.0 / 16.0), // Side bump 2
                    Shapes.box(1.0 / 16.0, 2.0 / 16.0, 3.0 / 16.0, 4.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0) // Side bump 3
            )
            NestVariant.WARM_OCEAN -> Shapes.box(3.0 / 16.0, 0.0, 3.0 / 16.0, 13.0 / 16.0, 3.0 / 16.0, 13.0 / 16.0)
            NestVariant.SEAGRASS -> Shapes.or(
                    Shapes.box(3.0 / 16.0, 0.0, 3.0 / 16.0, 13.0 / 16.0, 3.0 / 16.0, 14.0 / 16.0), // Main base
                    Shapes.box(1.0 / 16.0, 0.0, 7.0 / 16.0, 5.0 / 16.0, 4.0 / 16.0, 13.0 / 16.0), // Side protrusion 1
                    Shapes.box(5.0 / 16.0, 0.0, 1.0 / 16.0, 15.0 / 16.0, 2.0 / 16.0, 9.0 / 16.0), // Side protrusion 2
                    Shapes.box(6.0 / 16.0, 0.0, 13.0 / 16.0, 16.0 / 16.0, 6.0 / 16.0, 14.0 / 16.0) // Backside bump
            )
            NestVariant.LUSH -> Shapes.or(
                    Shapes.box(4.0 / 16.0, 0.0, 0.0 / 16.0, 12.0 / 16.0, 2.0 / 16.0, 12.0 / 16.0), // Main base
                    Shapes.box(0.0 / 16.0, 0.0, 0.0 / 16.0, 4.0 / 16.0, 4.0 / 16.0, 12.0 / 16.0), // Side extension 1
                    Shapes.box(0.0 / 16.0, 0.0, 12.0 / 16.0, 12.0 / 16.0, 6.0 / 16.0, 16.0 / 16.0), // Side extension 2
                    Shapes.box(12.0 / 16.0, 0.0, 8.0 / 16.0, 16.0 / 16.0, 4.0 / 16.0, 16.0 / 16.0), // Side extension 3
                    Shapes.box(12.0 / 16.0, 0.0, 0.0 / 16.0, 16.0 / 16.0, 2.0 / 16.0, 8.0 / 16.0) // Side extension 4
            )
            NestVariant.HAY -> Shapes.box(3.0 / 16.0, 0.0, 3.0 / 16.0, 13.0 / 16.0, 4.0 / 16.0, 13.0 / 16.0)
            NestVariant.SAND -> Shapes.or(
                    Shapes.box(3.0 / 16.0, 0.0, 3.0 / 16.0, 14.0 / 16.0, 4.0 / 16.0, 14.0 / 16.0), // Main nest base
                    Shapes.box(13.0 / 16.0, 0.0, 12.0 / 16.0, 16.0 / 16.0, 5.0 / 16.0, 16.0 / 16.0), // Large rock back right
                    Shapes.box(10.0 / 16.0, 0.0, 12.0 / 16.0, 13.0 / 16.0, 3.0 / 16.0, 16.0 / 16.0), // Medium rock back center
                    Shapes.box(14.0 / 16.0, 0.0, 9.0 / 16.0, 16.0 / 16.0, 2.0 / 16.0, 12.0 / 16.0), // Small rock right side
                    Shapes.box(4.0 / 16.0, 0.0, 0.0 / 16.0, 7.0 / 16.0, 3.0 / 16.0, 3.0 / 16.0)  // Small rock front left
            )
            NestVariant.RED_SAND -> Shapes.or(
                    Shapes.box(3.0 / 16.0, 0.0, 3.0 / 16.0, 14.0 / 16.0, 4.0 / 16.0, 14.0 / 16.0), // Main nest base
                    Shapes.box(13.0 / 16.0, 0.0, 12.0 / 16.0, 16.0 / 16.0, 5.0 / 16.0, 16.0 / 16.0), // Large rock back right
                    Shapes.box(10.0 / 16.0, 0.0, 12.0 / 16.0, 13.0 / 16.0, 3.0 / 16.0, 16.0 / 16.0), // Medium rock back center
                    Shapes.box(14.0 / 16.0, 0.0, 9.0 / 16.0, 16.0 / 16.0, 2.0 / 16.0, 12.0 / 16.0), // Small rock right side
                    Shapes.box(4.0 / 16.0, 0.0, 0.0 / 16.0, 7.0 / 16.0, 3.0 / 16.0, 3.0 / 16.0)  // Small rock front left
            )
            NestVariant.CHORUS -> Shapes.box(1.0 / 16.0, 0.0, 1.0 / 16.0, 15.0 / 16.0, 4.0 / 16.0, 15.0 / 16.0)
        }
    }


    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.WATERLOGGED)
    }

    override fun neighborChanged(
            state: BlockState,
            level: Level,
            pos: BlockPos,
            block: Block,
            fromPos: BlockPos,
            isMoving: Boolean
    ) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving)

        // Check if the block below is air
        val blockBelowPos = pos.below()
        if (fromPos == blockBelowPos && level.getBlockState(blockBelowPos).isAir) {
            // Remove the block without dropping items (loot table handles drops)
            level.destroyBlock(pos, false)
        }
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

        val entity = level.getBlockEntity(blockPos) as? NestBlockEntity ?: return InteractionResult.FAIL

        val egg = entity.getItem().get(CobblemonItemComponents.EGG_DATA)?.egg

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

    override fun getDestroyProgress(state: BlockState, player: Player, level: BlockGetter, pos: BlockPos): Float {
        // Retrieve the block entity at the given position
        val blockEntity = level.getBlockEntity(pos) as? NestBlockEntity ?: return super.getDestroyProgress(state, player, level, pos)

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

    // todo we want destroying to take longer if it is not their nest
    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val entity = level.getBlockEntity(pos) as? NestBlockEntity

        // todo we do silk touch now
        /*entity?.let {
            if (!player.isCreative) {
                val itemEntity = when (this.variant) {
                    NestVariant.BASIC -> ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.BASE_NEST.defaultInstance)
                    NestVariant.WATER -> ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.WATER_NEST.defaultInstance)
                    NestVariant.CAVE -> ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.CAVE_NEST.defaultInstance)
                    NestVariant.NETHER -> ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.NETHER_NEST.defaultInstance)
                }
                level.addFreshEntity(itemEntity)
            }
            it.dropItem( level, pos, state)
        }*/

        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState) = NestBlockEntity(pos, state)

    override fun getRenderShape(blockState: BlockState) = RenderShape.MODEL

    override fun canPlaceLiquid(
        player: Player?,
        level: BlockGetter,
        pos: BlockPos,
        state: BlockState,
        fluid: Fluid
    ): Boolean {
        return (variant == NestVariant.WATER || variant == NestVariant.SEAGRASS || variant == NestVariant.WARM_OCEAN) && super.canPlaceLiquid(player, level, pos, state, fluid)
    }

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            Fluids.WATER.getSource(false)
        } else super.getFluidState(state)
    }

    override fun setPlacedBy(
            level: Level,
            pos: BlockPos,
            state: BlockState,
            placer: LivingEntity?,
            stack: ItemStack
    ) {
        val blockEntity = level.getBlockEntity(pos) as? NestBlockEntity
        if (placer is Player) {
            blockEntity?.ownerUUID = placer.uuid
        }
        super.setPlacedBy(level, pos, state, placer, stack)
    }


    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState {
        if (variant == NestVariant.WATER || variant == NestVariant.SEAGRASS || variant == NestVariant.WARM_OCEAN) {
            return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, ctx.level.getFluidState(ctx.clickedPos).type == Fluids.WATER)
        }
        return defaultBlockState()
    }

    override fun <T : BlockEntity?> getTicker(level: Level, blockState: BlockState, blockEntityType: BlockEntityType<T>): BlockEntityTicker<T>? {
        return createTickerHelper(blockEntityType, CobblemonBlockEntities.NEST, NestBlockEntity.TICKER)
    }


    override fun codec(): MapCodec<NestBlock> {
        return RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                    Codec.STRING.fieldOf("variant").forGetter { block -> block.variant.id }, // Serializes the variant
                    Codec.BOOL.optionalFieldOf("waterlogged", false).forGetter { block ->
                        block.defaultBlockState().getValue(BlockStateProperties.WATERLOGGED)
                    }
            ).apply(instance) { variant, waterlogged ->
                val block = NestBlock(NestVariant.valueOf(variant.uppercase()), Properties.of().noOcclusion())
                block.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, waterlogged)
                block
            }
        }
    }



}