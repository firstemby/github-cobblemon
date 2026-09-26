/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

open class NestBlock(val variant: NestVariant, properties: Properties) : BaseEntityBlock(properties),
    SimpleWaterloggedBlock {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(BlockStateProperties.WATERLOGGED, false)
        )
    }

    enum class NestVariant(val id: String) : StringRepresentable {
        BASIC("basic_nest");

        override fun getSerializedName() = id
    }

    override fun getShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: net.minecraft.world.phys.shapes.CollisionContext
    ): VoxelShape {
        return Shapes.box(0.1, 0.0, 0.1, 0.9, 0.45, 0.9)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        checkAndBreakIfSubmerged(state, level, pos)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.WATERLOGGED)
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        checkAndBreakIfSubmerged(state, level, pos)
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
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
            level.destroyBlock(pos, false)
        }

        checkAndBreakIfSubmerged(state, level, pos)
    }

    private fun checkAndBreakIfSubmerged(state: BlockState, levelAccessor: LevelAccessor, pos: BlockPos) {
        val fluidState = levelAccessor.getFluidState(pos)

        if ((fluidState.type == Fluids.WATER || fluidState.isSource) && !canBeWaterlogged()) {
            if (levelAccessor is Level) {
                levelAccessor.destroyBlock(pos, true)
            }
        }
    }

    private fun canBeWaterlogged(): Boolean = false

    override fun useWithoutItem(
        blockState: BlockState,
        level: Level,
        blockPos: BlockPos,
        player: Player,
        blockHitResult: BlockHitResult
    ): InteractionResult {
        // Ensure this logic only runs on the server
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.FAIL

        val entity = level.getBlockEntity(blockPos) as? NestBlockEntity ?: return InteractionResult.FAIL

        val egg = entity.getItem().get(CobblemonItemComponents.EGG_DATA)

        // if the player interacting with the nest is not the owner of the nest we do not want them taking the egg or hatching
        if (egg != null && entity.ownerUUID != player.uuid && entity.ownerUUID != null) {
            return InteractionResult.FAIL
        }

        // if the person interacting is not the owner of one of the parents
        if (egg != null && (player.uuid != egg.parent1OwnerUUID.orElse(null) || player.uuid != egg.parent2OwnerUUID.orElse(null)) && !egg.isWild) {
            return InteractionResult.FAIL
        }

        // we should probably not let eggs get taken if its parents are in a battle so it can't be cheesed.... todo (should we have it be a config?)
        if (egg != null && egg.isWild && level is ServerLevel) {
            val parentsBattling = listOfNotNull(entity.parents.first, entity.parents.second)
                .any { (level.getEntity(it) as? PokemonEntity)?.isBattling == true }
            // fail player interaction if either parent is battling
            if (parentsBattling)
                return InteractionResult.FAIL
        }

        return entity.onUse(blockState, level, blockPos, serverPlayer, InteractionHand.MAIN_HAND, blockHitResult)
    }

    override fun getDestroyProgress(state: BlockState, player: Player, level: BlockGetter, pos: BlockPos): Float {
        // Retrieve the block entity at the given position
        val blockEntity =
            level.getBlockEntity(pos) as? NestBlockEntity ?: return super.getDestroyProgress(state, player, level, pos)

        // Check if the player is the owner of the nest
        val isOwner = blockEntity.ownerUUID == player.uuid

        // Check if there's an egg present in the nest
        val hasEgg = !blockEntity.getItem(0).isEmpty

        return if (!isOwner && hasEgg) {
            // Reduce destroy speed making it 5 times harder to break
            super.getDestroyProgress(state, player, level, pos) * 0.2f
        } else {
            super.getDestroyProgress(state, player, level, pos)
        }
    }

    // todo we want destroying to take longer if it is not their nest
    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val entity = level.getBlockEntity(pos) as? NestBlockEntity
        if (entity != null && !level.isClientSide && !entity.getItem(0).isEmpty) {
            (player as? ServerPlayer)?.let { entity.notifyEggTheftIfVisible(it) }
        }

        return super.playerWillDestroy(level, pos, state, player)
    }

    // if the nest is removed / destroyed with a wild egg inside break the egg (this is so we can prevent cheese where players could break a nest to get to the egg) todo not fully sold though so we should think of other ways to prevent da cheese
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block) && !level.isClientSide) {
            val egg = (level.getBlockEntity(pos) as? NestBlockEntity)?.getItem()?.get(CobblemonItemComponents.EGG_DATA)
            // only break the egg if it is wild (is this going to be confusing to players? idk)
            if (egg != null && egg.isWild) {
                Cobblemon.eggStoreManager.remove(egg.eggId)
                // this is the closest I can get to egg breaking particles but it bothers me it isn't using the textures on the actual egg
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(CobblemonBlocks.EGG.defaultBlockState()))
                // todo play a egg breaking sound here STERREZO PLZZZ
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState) = NestBlockEntity(pos, state)

    override fun getRenderShape(blockState: BlockState) = RenderShape.MODEL

    override fun canPlaceLiquid(
        player: Player?,
        level: BlockGetter,
        pos: BlockPos,
        state: BlockState,
        fluid: Fluid
    ): Boolean = false

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


    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val fluidState = ctx.level.getFluidState(ctx.clickedPos)

        // If the nest variant can be waterlogged, set it accordingly
        if (canBeWaterlogged()) {
            return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, fluidState.type == Fluids.WATER)
        }

        // Prevent placement if the block is submerged in water
        if (!canBeWaterlogged() && fluidState.type == Fluids.WATER) {
            return null
        }

        return defaultBlockState()
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        blockState: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
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
