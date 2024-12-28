/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.block.HealingMachineBlock.Companion
import com.cobblemon.mod.common.block.HealingMachineBlock.Companion.CHARGE_LEVEL
import com.cobblemon.mod.common.block.entity.DisplayCaseBlockEntity
import com.cobblemon.mod.common.block.entity.EggBlockEntity
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.client.gui.PartyOverlay.Companion.state
import com.cobblemon.mod.common.item.PokemonEggItem
import com.cobblemon.mod.common.item.components.EggComponent
import com.mojang.serialization.Dynamic
import com.mojang.serialization.MapCodec
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.phys.BlockHitResult

class EggBlock(properties: Properties) : BaseEntityBlock(properties) {
    override fun setPlacedBy(
            level: Level,
            blockPos: BlockPos,
            blockState: BlockState,
            placer: LivingEntity?,
            itemStack: ItemStack
    ) {
        val entity = level.getBlockEntity(blockPos) as? EggBlockEntity

        // Retrieve the EGG_COMPONENT from the item stack and set it in the block entity
        val eggComponent = itemStack.get(CobblemonItemComponents.EGG_DATA)
        eggComponent?.let { component ->
            entity?.setEggData(component.egg) // Pass the Egg data to the block entity
            println("Set egg in EggBlockEntity: ${component.egg?.species}")
        }

        super.setPlacedBy(level, blockPos, blockState, placer, itemStack)
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

        val entity = level.getBlockEntity(blockPos) as? EggBlockEntity
        return entity?.onUse(blockState, level, blockPos, serverPlayer, InteractionHand.MAIN_HAND, blockHitResult)
            ?: InteractionResult.FAIL
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val entity = level.getBlockEntity(pos) as? EggBlockEntity

        if (entity != null && !player.isCreative) {
            entity.dropEgg(state, level, pos)
        }

        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun codec(): MapCodec<out BaseEntityBlock> {
        TODO("Not yet implemented")
    }

    override fun getRenderShape(blockState: BlockState): RenderShape {
        return RenderShape.ENTITYBLOCK_ANIMATED
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState) = EggBlockEntity(pos, state)
}