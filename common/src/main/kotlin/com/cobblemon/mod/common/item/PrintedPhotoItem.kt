/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.block.PrintedPhotoDisplayBlock
import com.cobblemon.mod.common.block.entity.PrintedPhotoDisplayBlockEntity
import com.cobblemon.mod.common.item.components.PrintedPhotoComponent
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.InteractionResult

class PrintedPhotoItem(properties: Item.Properties) : Item(properties) {
    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide) return
        val player = entity as? ServerPlayer ?: return
        if (player.tickCount % 20 != 0) return

        val printedPhoto = PrintedPhotoComponent.get(stack) ?: return
        if (printedPhoto.detectedSpecies.isEmpty()) return

        val pokedex = Cobblemon.playerDataManager.getPokedexData(player.uuid)
        printedPhoto.detectedSpecies.forEach { speciesKey ->
            val speciesId = ResourceLocation.tryParse(speciesKey) ?: return@forEach
            if (pokedex.getKnowledgeForSpecies(speciesId) >= PokedexEntryProgress.ENCOUNTERED) return@forEach
            val species = PokemonSpecies.getByIdentifier(speciesId) ?: return@forEach
            pokedex.encounter(species.create(level = 1))
        }
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: Item.TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)

        val printedPhoto = PrintedPhotoComponent.get(stack) ?: return
        tooltipComponents.add(Component.translatable("item.cobblemon.printed_photo.photo_id", printedPhoto.photoId))
        tooltipComponents.add(Component.translatable("item.cobblemon.printed_photo.quality_score", printedPhoto.qualityScore))
        tooltipComponents.add(Component.literal("Size: ${printedPhoto.printSize}x${printedPhoto.printSize}"))
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val stack = context.itemInHand
        val component = PrintedPhotoComponent.get(stack) ?: return InteractionResult.PASS
        val level = context.level
        val placeContext = BlockPlaceContext(context)
        val displayState = CobblemonBlocks.PRINTED_PHOTO_DISPLAY.getStateForPlacement(placeContext) ?: return InteractionResult.FAIL
        val size = component.printSize.coerceIn(1, 3)
        val rootPos = placeContext.clickedPos
        val axes = PrintedPhotoDisplayBlock.resolveAxes(displayState)

        val plannedPlacements = mutableListOf<Triple<Int, Int, net.minecraft.core.BlockPos>>()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val targetPos = rootPos.relative(axes.right, x).relative(axes.up, y)
                plannedPlacements += Triple(x, y, targetPos)
            }
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        for ((_, _, targetPos) in plannedPlacements) {
            val stateAtTarget = level.getBlockState(targetPos)
            if (!stateAtTarget.isAir && !stateAtTarget.canBeReplaced()) {
                return InteractionResult.FAIL
            }
            val candidateState = displayState.setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                level.getFluidState(targetPos).type == Fluids.WATER
            )
            if (!candidateState.canSurvive(level, targetPos)) {
                return InteractionResult.FAIL
            }
        }

        val placed = mutableListOf<net.minecraft.core.BlockPos>()
        for ((_, _, targetPos) in plannedPlacements) {
            val candidateState = displayState.setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                level.getFluidState(targetPos).type == Fluids.WATER
            )
            if (!level.setBlock(targetPos, candidateState, Block.UPDATE_ALL_IMMEDIATE)) {
                placed.forEach { level.setBlock(it, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE) }
                return InteractionResult.FAIL
            }
            placed += targetPos
        }

        for ((cellX, cellY, targetPos) in plannedPlacements) {
            val blockEntity = level.getBlockEntity(targetPos) as? PrintedPhotoDisplayBlockEntity ?: continue
            blockEntity.setComponent(component.copy(printSize = size))
            blockEntity.setTileData(rootPos = rootPos, gridSize = size, cellX = cellX, cellY = cellY)
        }

        val soundType = displayState.soundType
        level.playSound(
            context.player,
            rootPos,
            soundType.placeSound,
            SoundSource.BLOCKS,
            (soundType.volume + 1.0F) / 2.0F,
            soundType.pitch * 0.8F
        )

        if (context.player?.isCreative != true) {
            stack.shrink(1)
        }

        return InteractionResult.CONSUME
    }
}

