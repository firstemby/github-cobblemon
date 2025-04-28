/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.CandlePokeCakeBlock.Companion.CANDLE_COLOR
import com.cobblemon.mod.common.block.entity.PokeCakeBlockEntity
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionHand.MAIN_HAND
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.BlockHitResult

class PokeCakeBlock(settings: Properties): CakeBlock(settings) {
    override fun codec(): MapCodec<out BaseEntityBlock?>? {
        return simpleCodec(::PokeCakeBlock)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return PokeCakeBlockEntity(pos, state)
    }

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)

        val blockEntity = level.getBlockEntity(pos) as? PokeCakeBlockEntity
        blockEntity?.initializeFromItemStack(stack)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult? {
        if (level.isClientSide) {
            if (eat(level, pos, player).consumesAction()) {
                return InteractionResult.SUCCESS
            }

            if (player.getItemInHand(MAIN_HAND).isEmpty) {
                return InteractionResult.CONSUME
            }
        }

        return eat(level, pos, player)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult? {
        val item = stack.item
        val block = state.block as? PokeCakeBlock

        if (stack.`is`(ItemTags.CANDLES) && block?.getBites(level, pos) == 0) {
            val candleBlock = byItem(item)
            if (candleBlock is CandleBlock) {
                stack.shrink(1)
                level.playSound(null, pos, SoundEvents.CAKE_ADD_CANDLE, SoundSource.BLOCKS, 1.0f, 1.0f)

                val oldCookingComponent = block.getFoodColourComponent(level, pos)

                val newBlockState = CobblemonBlocks.CANDLE_POKE_CAKE.defaultBlockState()
                    .setValue(CANDLE_COLOR, (item as BlockItem).block.defaultMapColor().id)
                level.setBlockAndUpdate(pos, newBlockState)
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos)

                oldCookingComponent?.let {
                    (newBlockState.block as CakeBlock).setFoodColourComponent(level, pos, it)
                }

                player.awardStat(Stats.ITEM_USED[item])
                return ItemInteractionResult.SUCCESS
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }

    fun eat(level: LevelAccessor, pos: BlockPos, player: Player): InteractionResult {
        if (!player.canEat(false)) {
            return InteractionResult.PASS
        }

        player.getFoodData().eat(2, 0.1F)
        level.gameEvent(player, GameEvent.EAT, pos)

        var bites = getBites(level, pos)
        if (bites < 6) {
            setBites(level, pos, bites + 1)
        } else {
            level.removeBlock(pos, false)
            level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos)
        }

        return InteractionResult.SUCCESS
    }
}
