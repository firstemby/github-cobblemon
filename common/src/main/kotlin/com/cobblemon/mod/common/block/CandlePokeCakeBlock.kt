/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.CandlePokeCakeBlockEntity
import com.cobblemon.mod.common.client.render.block.CakeBlockEntityRenderer.Companion.TOP_LAYER_HEIGHT
import com.cobblemon.mod.common.util.toEquipmentSlot
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractCandleBlock
import net.minecraft.world.level.block.AbstractCandleBlock.extinguish
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks.*
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class CandlePokeCakeBlock(settings: Properties) : CakeBlock(settings) {

    companion object {
        val LIT = BlockStateProperties.LIT
        val CANDLE_COLOR = IntegerProperty.create("candle_color", 0, MapColor.MATERIAL_COLORS.size - 1)

        fun getCandleByMapColorId(id: Int): CandleBlock {
            return when (id) {
                MapColor.SAND.id -> CANDLE
                MapColor.WOOL.id -> WHITE_CANDLE
                MapColor.COLOR_ORANGE.id -> ORANGE_CANDLE
                MapColor.COLOR_MAGENTA.id -> MAGENTA_CANDLE
                MapColor.COLOR_LIGHT_BLUE.id -> LIGHT_BLUE_CANDLE
                MapColor.COLOR_YELLOW.id -> YELLOW_CANDLE
                MapColor.COLOR_LIGHT_GREEN.id -> LIME_CANDLE
                MapColor.COLOR_PINK.id -> PINK_CANDLE
                MapColor.COLOR_GRAY.id -> GRAY_CANDLE
                MapColor.COLOR_LIGHT_GRAY.id -> LIGHT_GRAY_CANDLE
                MapColor.COLOR_CYAN.id -> CYAN_CANDLE
                MapColor.COLOR_PURPLE.id -> PURPLE_CANDLE
                MapColor.COLOR_BLUE.id -> BLUE_CANDLE
                MapColor.COLOR_BROWN.id -> BROWN_CANDLE
                MapColor.COLOR_GREEN.id -> GREEN_CANDLE
                MapColor.COLOR_RED.id -> RED_CANDLE
                MapColor.COLOR_BLACK.id -> BLACK_CANDLE
                else -> CANDLE
            } as CandleBlock
        }

        val CANDLE_SHAPE: VoxelShape = box(7.0, 8.0, 7.0, 9.0, 15.0, 9.0)
        val SHAPE: VoxelShape = Shapes.or(SHAPE_BY_BITE[0], CANDLE_SHAPE)
        val PARTICLE_OFFSET = Vec3(0.5, 0.5 + TOP_LAYER_HEIGHT, 0.5)
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(LIT, false)
            .setValue(CANDLE_COLOR, MapColor.SAND.id)
        )
    }

    override fun codec(): MapCodec<out BaseEntityBlock?> {
        return simpleCodec(::CandlePokeCakeBlock)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return CandlePokeCakeBlockEntity(pos, state)
    }
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(LIT, CANDLE_COLOR)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult {
        return if (!stack.`is`(Items.FLINT_AND_STEEL) && !stack.`is`(Items.FIRE_CHARGE)) {
            if (candleHit(hitResult) && stack.isEmpty && state.getValue(LIT) == true) {
                extinguish(player, state, level, pos)
                level.addParticle(ParticleTypes.SMOKE, PARTICLE_OFFSET.x + pos.x, pos.y + PARTICLE_OFFSET.y, pos.z + PARTICLE_OFFSET.z, 0.0, 0.1, 0.0)
                ItemInteractionResult.sidedSuccess(level.isClientSide)
            } else {
                super.useItemOn(stack, state, level, pos, player, hand, hitResult)
            }
        } else if (state.getValue(LIT) == false) {
            level.playSound(player, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, level.random.nextFloat() * 0.4f + 0.8f)
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), UPDATE_ALL_IMMEDIATE)
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos)

            player.getItemInHand(hand).hurtAndBreak(1, player, hand.toEquipmentSlot())
            ItemInteractionResult.sidedSuccess(level.isClientSide)
        } else {
            ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        val oldCookingComponent = getFoodColourComponent(level, pos)
        val candle = getCandleByMapColorId(state.getValue(CANDLE_COLOR))

        val newBlockState = CobblemonBlocks.POKE_CAKE.defaultBlockState()
        level.setBlockAndUpdate(pos, newBlockState)
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos)

        val pokeCakeBlock = newBlockState.block as PokeCakeBlock
        oldCookingComponent?.let {
            pokeCakeBlock.setFoodColourComponent(level, pos, it)
        }

        val interactionResult = pokeCakeBlock.eat(level, pos, player)
        if (interactionResult.consumesAction()) {
            popResource(level, pos, ItemStack(candle.asItem()))
        }

        return interactionResult
    }

    private fun candleHit(hit: BlockHitResult): Boolean {
        return hit.location.y - hit.blockPos.y.toDouble() > TOP_LAYER_HEIGHT
    }

    override fun animateTick(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        random: RandomSource
    ) {
        if (state.getValue(LIT)) {
            AbstractCandleBlock.addParticlesAndSound(level, Vec3(PARTICLE_OFFSET.x + pos.x, PARTICLE_OFFSET.y + pos.y, PARTICLE_OFFSET.z + pos.z), random)
        }
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = SHAPE
}