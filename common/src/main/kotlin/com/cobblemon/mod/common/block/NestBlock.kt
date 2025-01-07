/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlockEntities
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
import net.minecraft.world.entity.item.ItemEntity
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

class NestBlock(val variant: NestVariant, properties: Properties) : BaseEntityBlock(properties), SimpleWaterloggedBlock {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(BlockStateProperties.WATERLOGGED, false)
        )
    }

    enum class NestVariant(val id: String) : StringRepresentable {
        CAVE("cave_nest"),
        NETHER("nether_nest"),
        WATER("water_nest"),
        BASIC("basic_nest");

        override fun getSerializedName() = id
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.WATERLOGGED)
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
        return entity.onUse(blockState, level, blockPos, serverPlayer, InteractionHand.MAIN_HAND, blockHitResult)
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val entity = level.getBlockEntity(pos) as? NestBlockEntity

        entity?.let {
            if (!player.isCreative) {
                val itemEntity = when (this.variant) {
                    NestVariant.BASIC -> ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.BASE_NEST.defaultInstance)
                    NestVariant.WATER -> ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.WATER_NEST.defaultInstance)
                    NestVariant.CAVE -> ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.CAVE_NEST.defaultInstance)
                    NestVariant.NETHER -> ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.NETHER_NEST.defaultInstance)
                }
                level.addFreshEntity(itemEntity)
            }
            it.dropEgg(state, level, pos)
        }
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
        return variant == NestVariant.WATER && super.canPlaceLiquid(player, level, pos, state, fluid)
    }

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            Fluids.WATER.getSource(false)
        } else super.getFluidState(state)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState {
        if (variant == NestVariant.WATER) {
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