/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.breeding.BreedingLogicManager
import com.cobblemon.mod.common.item.components.EggComponent
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentType
import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.Level
import net.minecraft.world.entity.item.ItemEntity

class NestBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(CobblemonBlockEntities.NEST, pos, state) {
    var eggComponent: EggComponent? = null
        private set
    var renderState: BlockEntityRenderState? = null

    /**
     * Sets the EggComponent and synchronizes it.
     */
    fun setEgg(egg: Egg) {
        this.eggComponent = EggComponent(egg)
        setChanged() // Mark the block entity as changed.
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS) // Notify the client.
        //println("Egg data synchronized to client for NestBlockEntity at $blockPos: $egg")
    }


    /**
     * Retrieves the EggComponent's Egg data.
     */
    fun getEgg(): Egg? {
        return eggComponent?.egg
    }

    /**
     * Save the EggComponent data manually to NBT.
     */
    override fun saveAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(tag, registryLookup)
        //println("Saving NestBlockEntity at $blockPos with eggComponent: $eggComponent")
        eggComponent?.let {
            CobblemonItemComponents.EGG_DATA.codec()
                    ?.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, it)
                    ?.result()
                    ?.ifPresent { encoded -> tag.put("EggComponent", encoded) }
        }
    }

    /**
     * Load the EggComponent data manually from NBT.
     */
    override fun loadAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(tag, registryLookup)
        //println("Loading NestBlockEntity at $blockPos. EggComponent loaded: $eggComponent")
        if (tag.contains("EggComponent")) {
            eggComponent = CobblemonItemComponents.EGG_DATA.codec()
                    ?.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("EggComponent"))
                    ?.result()
                    ?.orElse(null)
            //println("Client loaded EggComponent: ${eggComponent?.egg?.species}")
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        //println("getUpdatePacket called for NestBlockEntity at $blockPos with egg: $eggComponent")
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        val tag = saveWithoutMetadata(registryLookup)
        //println("getUpdateTag called for NestBlockEntity at $blockPos with tag: $tag")
        return tag
    }



    fun dropEgg(
            state: BlockState,
            world: Level,
            pos: BlockPos
    ) {
        val currentEgg = getEgg() ?: return

        // Create an ItemStack with the EggComponent
        val eggItemStack = CobblemonItems.POKEMON_EGG.defaultInstance.apply {
            set(CobblemonItemComponents.EGG_DATA, EggComponent(currentEgg))
        }

        // Spawn the egg as an entity
        val itemEntity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, eggItemStack)
        world.addFreshEntity(itemEntity)

        // Clear the egg from the nest
        eggComponent = null
        setChanged()
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
        val currentEgg = getEgg()

        // If the nest has an egg
        if (currentEgg != null) {
            return if (currentEgg.timeToHatch == 0) {
                hatchPokemon(state, world, pos, player) // Hatch if ready
                InteractionResult.SUCCESS
            } else {
                dropEgg(state, world, pos) // Drop if not hatched yet
                InteractionResult.SUCCESS
            }
        }

        // If player is holding an egg, place it in the nest
        val playerStack = player.getItemInHand(hand)
        val eggComponent = playerStack.get(CobblemonItemComponents.EGG_DATA)

        if (eggComponent != null) {
            setEgg(eggComponent.egg)
            if (!player.isCreative) {
                playerStack.shrink(1) // Consume the egg if not in creative mode
            }
            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f)
            return InteractionResult.SUCCESS
        }

        return InteractionResult.FAIL
    }


    fun hatchPokemon(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: ServerPlayer
    ) {
        /*egg?.let { eggData ->
            if (!world.isClientSide) {
                // Retrieve the two parent Pokémon using their UUIDs
                val parentOne = Cobblemon.storage.getPokemon(eggData.parentOneUUID)
                val parentTwo = Cobblemon.storage.getPokemon(eggData.parentTwoUUID)

                if (parentOne != null && parentTwo != null) {
                    // Generate the hatched Pokémon using BreedingLogicManager
                    val breedingResult = BreedingLogicManager.breed(parentOne, parentTwo)
                    val hatchedPokemon = breedingResult.pokemon?.generatePokemon()

                    hatchedPokemon?.let { newPokemon ->
                        val player = Cobblemon.storage.getPartyOwner(this)?.let { world.getPlayerByUUID(it) }
                        player?.let {
                            val party = Cobblemon.storage.getParty(it.uuid)
                            party.add(newPokemon)
                        }

                        world.playSound(null, pos, SoundEvents.CHICKEN_EGG, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f)
                    }
                }

                egg = null
                setChanged()
                world.sendBlockUpdated(pos, blockState, blockState, Block.UPDATE_CLIENTS)
            }
        }*/


    }

    companion object {
        val TICKER = BlockEntityTicker<NestBlockEntity> { world, pos, state, blockEntity ->
            //println("Ticking NestBlockEntity at $pos. Egg state: ${blockEntity.getEgg()}")

            // Retrieve the Egg from the EggComponent
            val egg = blockEntity.getEgg() ?: return@BlockEntityTicker

            if (egg.timeToHatch <= 0) {
                // Try to find a nearby ServerPlayer to pass to hatchPokemon
                val nearestPlayer = world.getNearestPlayer(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 10.0, false)

                if (nearestPlayer is ServerPlayer) {
                    blockEntity.hatchPokemon(state, world, pos, nearestPlayer)
                } else {
                    // Handle edge case if no ServerPlayer is nearby
                    //println("No ServerPlayer found near NestBlockEntity at $pos to hatch the egg.")
                }

                blockEntity.setChanged()
                return@BlockEntityTicker
            }

            // Decrease hatch time if the egg still has time left
            egg.timeToHatch--

            // Sync to client every 10 seconds (200 ticks)
            if (egg.timeToHatch % 200 == 0) {
                blockEntity.setEgg(egg) // Update component and synchronize
                blockEntity.setChanged()
            }
        }
    }



}