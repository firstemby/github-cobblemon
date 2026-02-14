/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.habitat

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class HabitatBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(CobblemonBlockEntities.HABITAT_BLOCK, pos, state) {

    var numberOfPhases: Int = 1
    var randomizePhases: Boolean = true
    val currentPhase = 1
    var levelRange: IntRange = 1..Cobblemon.config.maxPokemonLevel
    var modifiers: PokemonProperties = PokemonProperties()

    var mimicId: ResourceLocation = BuiltInRegistries.BLOCK.getKey(Blocks.STONE)
    val mimickedState: BlockState
        get() = BuiltInRegistries.BLOCK.get(mimicId).defaultBlockState() ?: Blocks.STONE.defaultBlockState()

    var mimicIndex: Int = 0

    fun setMimic(id: ResourceLocation) {
        mimicId = id
        setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
    }

    fun cycleMimic() {
        mimicIndex = (mimicIndex + 1) % mimicCycle.size
        setMimic(mimicCycle[mimicIndex])
    }

    fun onUse(player: Player, hit: BlockHitResult): InteractionResult {
        cycleMimic()
        // TODO replace with Edit GUI
        player.displayClientMessage(Component.literal("Mimicking: $mimicId"), true)
        return InteractionResult.SUCCESS
    }

    override fun saveAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(tag, registryLookup)
        tag.putString("MimicId", mimicId.toString())
    }

    override fun loadAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(tag, registryLookup)
        tag.getString("MimicId").let {
            mimicId = ResourceLocation.tryParse(it) ?: BuiltInRegistries.BLOCK.getKey(Blocks.STONE)
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registryLookup)
    }

    fun updateLevelAndPos(level: Level, pos: BlockPos) {
        val block = this.blockState.block
        if (block is HabitatBlock) {
            block.level = level
            block.pos = pos
        }
    }

    companion object {
        val TICKER = BlockEntityTicker<HabitatBlockEntity> { world, pos, state, blockEntity ->
            if (world is ServerLevel) {
                // Some spawning logic if the spawning style is activated :: tick
            }
        }

        // todo to test with checking how it looks as other blocks
        val mimicCycle: List<ResourceLocation> = listOf(
            ResourceLocation.parse("minecraft:stone"),
            ResourceLocation.parse("minecraft:cobblestone"),
            ResourceLocation.parse("minecraft:granite"),
            ResourceLocation.parse("minecraft:diorite"),
            ResourceLocation.parse("minecraft:andesite"),
            ResourceLocation.parse("minecraft:deepslate"),
            ResourceLocation.parse("minecraft:polished_deepslate"),

            ResourceLocation.parse("minecraft:oak_planks"),
            ResourceLocation.parse("minecraft:spruce_planks"),
            ResourceLocation.parse("minecraft:birch_planks"),
            ResourceLocation.parse("minecraft:jungle_planks"),
            ResourceLocation.parse("minecraft:acacia_planks"),
            ResourceLocation.parse("minecraft:dark_oak_planks"),
            ResourceLocation.parse("minecraft:mangrove_planks"),
            ResourceLocation.parse("minecraft:cherry_planks"),
            ResourceLocation.parse("minecraft:bamboo_planks"),

            ResourceLocation.parse("minecraft:bricks"),
            ResourceLocation.parse("minecraft:mossy_cobblestone"),
            ResourceLocation.parse("minecraft:moss_block"),
            ResourceLocation.parse("minecraft:bookshelf"),
            ResourceLocation.parse("minecraft:loom"),
            ResourceLocation.parse("minecraft:note_block"),

            ResourceLocation.parse("minecraft:coal_block"),
            ResourceLocation.parse("minecraft:iron_block"),
            ResourceLocation.parse("minecraft:gold_block"),
            ResourceLocation.parse("minecraft:diamond_block"),
            ResourceLocation.parse("minecraft:emerald_block"),
            ResourceLocation.parse("minecraft:lapis_block"),
            ResourceLocation.parse("minecraft:redstone_block"),
            ResourceLocation.parse("minecraft:netherite_block"),

            ResourceLocation.parse("minecraft:obsidian"),
            ResourceLocation.parse("minecraft:crying_obsidian"),
            ResourceLocation.parse("minecraft:glowstone"),
            ResourceLocation.parse("minecraft:blue_ice"),
            ResourceLocation.parse("minecraft:packed_ice"),
            ResourceLocation.parse("minecraft:ice"),

            ResourceLocation.parse("minecraft:sandstone"),
            ResourceLocation.parse("minecraft:red_sandstone"),
            ResourceLocation.parse("minecraft:cut_sandstone"),
            ResourceLocation.parse("minecraft:smooth_sandstone"),

            ResourceLocation.parse("minecraft:black_concrete"),
            ResourceLocation.parse("minecraft:white_concrete"),
            ResourceLocation.parse("minecraft:lime_concrete"),
            ResourceLocation.parse("minecraft:cyan_concrete"),
            ResourceLocation.parse("minecraft:purple_concrete"),
            ResourceLocation.parse("minecraft:light_blue_concrete"),
            ResourceLocation.parse("minecraft:yellow_concrete"),
            ResourceLocation.parse("minecraft:red_concrete")
        )
    }
}