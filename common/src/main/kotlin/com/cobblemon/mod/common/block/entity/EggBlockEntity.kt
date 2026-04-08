/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.item.components.EggComponent
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class EggBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(CobblemonBlockEntities.EGG, pos, state) {
    var eggComponent: EggComponent? = null

    // Component data codec
    companion object {
        val CODEC: Codec<EggBlockEntity> = RecordCodecBuilder.create { instance ->
            instance.group(
                EggComponent.CODEC.optionalFieldOf("egg", null).forGetter(EggBlockEntity::eggComponent)
            ).apply(instance) { egg ->
                EggBlockEntity(BlockPos(0, 0, 0), Blocks.AIR.defaultBlockState()).apply {
                    setEgg(egg)
                }
            }
        }
    }

    /**
     * Sets the EggComponent and synchronizes with the client.
     */
    fun setEgg(egg: EggComponent?) {
        this.eggComponent = egg
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    /**
     * Save the EggComponent data manually to NBT.
     */
    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        eggComponent?.let { egg ->
            EggComponent.CODEC.encodeStart(NbtOps.INSTANCE, egg)
                .result()
                ?.ifPresent { encoded -> tag.put("EggComponent", encoded) }
        }
    }

    fun dropEgg(
        state: BlockState,
        world: Level,
        pos: BlockPos
    ) {
        val currentEgg = eggComponent ?: return

        // Log before clearing
        println("Dropping egg: ${currentEgg.identifier}, Variant: ${currentEgg.variant} at $pos")

        // Create an ItemStack with the EggComponent
        val eggItemStack = CobblemonItems.POKEMON_EGG.defaultInstance.apply {
            set(CobblemonItemComponents.EGG_DATA, currentEgg)
        }

        // Debug log to verify component data
        val component = eggItemStack.get(CobblemonItemComponents.EGG_DATA)
        println("EggComponent on dropped ItemStack: $component")

        // Spawn the egg as an entity
        val itemEntity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, eggItemStack)
        world.addFreshEntity(itemEntity)

        // Remove egg block
        world.removeBlockEntity(pos)
        world.removeBlock(pos, false)
        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
    }

    fun onUse(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: ServerPlayer,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        val currentEgg = eggComponent

        if (currentEgg != null) {
            dropEgg(state, world, pos)
            return InteractionResult.SUCCESS
        }

        return InteractionResult.FAIL
    }

    /**
     * Load the EggComponent data manually from NBT.
     */
    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("EggComponent")) {
            val egg = EggComponent.CODEC.parse(NbtOps.INSTANCE, tag.get("EggComponent"))
                .result()
                ?.orElse(null)
            eggComponent = egg
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        val tag = saveWithoutMetadata(registryLookup)
        return tag
    }
}
