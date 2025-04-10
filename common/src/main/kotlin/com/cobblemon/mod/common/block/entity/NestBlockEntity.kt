/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.*
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.data.ShowdownIdentifiable
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.api.spawning.BestSpawner
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.EntitySpawnResult
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause
import com.cobblemon.mod.common.api.spawning.fishing.NestSpawner
import com.cobblemon.mod.common.api.spawning.nest.NestSpawnCause
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.breeding.BreedingLogicManager
import com.cobblemon.mod.common.client.render.models.blockbench.egg.EggState
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.components.EggComponent
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.net.serverhandling.storage.SendOutPokemonHandler
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.toBlockPos
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
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
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentType
import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.Container
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.ticks.TickPriority
import java.util.*

class NestBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(CobblemonBlockEntities.NEST, pos, state), WorldlyContainer {
    private var eggItem: ItemStack = ItemStack.EMPTY
    var ownerUUID: UUID? = null // player UUID that placed this nest
    val eggState = EggState()
    var nestClaimedBy: Pair<UUID?, UUID?> = Pair(null, null)

    /**
     * Sets the ItemStack and synchronizes it.
     */
    fun setItem(itemStack: ItemStack) {
        this.eggItem = itemStack
        setChanged() // Mark the block entity as changed.
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS) // Notify the client.
    }

    /**
     * Retrieves the ItemStack.
     */
    fun getItem(): ItemStack = eggItem

    /**
     * Drops the ItemStack in the world.
     */
    fun dropItem(world: Level, pos: BlockPos, state: BlockState) {
        if (!eggItem.isEmpty) {
            val itemEntity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, eggItem.copy())
            world.addFreshEntity(itemEntity)
            eggItem = ItemStack.EMPTY
            setChanged()
            world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
        }
    }

    /**
     * Handles interaction with the block.
     */
    fun onUse(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: ServerPlayer,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        val heldItem = player.getItemInHand(hand)

        val eggComponent = eggItem.get(CobblemonItemComponents.EGG_DATA)
        if (!eggItem.isEmpty && eggComponent != null) {
            // Hatch the Pokémon if the time to hatch is 0 or below
            if ((eggComponent.egg?.timeToHatch ?: 0F) <= 0F) {
                eggState.isHatching = true // Trigger hatch animation on the renderer
                //playSound(CobblemonSounds.BREEDING_EGG_HATCH)
                eggState.renderingBlockPos = this.blockPos // Pass block pos to renderer for packet
                setChanged()
                level?.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
                return InteractionResult.SUCCESS
            }

            // Otherwise, drop the egg
            dropItem(world, pos, state)
            eggState.resetHatchingState()
            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f)
            return InteractionResult.SUCCESS
        }

        // Place a new egg if the player is holding one
        if (!heldItem.isEmpty && heldItem.get(CobblemonItemComponents.EGG_DATA) != null) {
            if (!player.isCreative) {
                setItem(heldItem.split(1))
            } else {
                setItem(heldItem.copy())
            }
            eggState.resetHatchingState()
            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f)
            return InteractionResult.SUCCESS
        }

        return InteractionResult.FAIL
    }

    override fun saveAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(tag, registryLookup)
        //if (!eggItem.isEmpty) {
            tag.put("EggItem", eggItem.saveOptional(registryLookup))
        //}
        ownerUUID?.let { tag.putUUID("OwnerUUID", it) }
        tag.putBoolean("IsHatching", eggState.isHatching)
        tag.putBoolean("HatchingTriggered", eggState.hatchingTriggered)
        eggState.renderingBlockPos?.let {
            tag.putIntArray("RenderingBlockPos", listOf(it.x, it.y, it.z))
        }
    }

    override fun loadAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(tag, registryLookup)
        //if (tag.contains("EggItem")) {
            eggItem = ItemStack.parseOptional(registryLookup, tag.getCompound("EggItem"))
        //}
        if (tag.contains("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID")
        }

        eggState.isHatching = tag.getBoolean("IsHatching")
        eggState.hatchingTriggered = tag.getBoolean("HatchingTriggered")
        if (tag.contains("RenderingBlockPos")) {
            val posArray = tag.getIntArray("RenderingBlockPos")
            if (posArray.size == 3) {
                eggState.renderingBlockPos = BlockPos(posArray[0], posArray[1], posArray[2])
            }
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

    /*fun playSound(soundEvent: SoundEvent) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
    }*/

    fun hatchPokemon(
            state: BlockState,
            world: Level,
            pos: BlockPos,
            player: ServerPlayer
    ) {
        val egg = getItem().get(CobblemonItemComponents.EGG_DATA)?.egg ?: return
        if (world !is ServerLevel) return

        val parentOne = egg.parentOne
        val parentTwo = egg.parentTwo

        if (parentOne == null || parentTwo == null) {
            player.displayClientMessage("Hatching failed: Parent Pokémon not found.".red(), true)
            return
        }

        val breedingResult = BreedingLogicManager.breed(parentOne, parentTwo)
        val hatchedPokemon = breedingResult.pokemon?.generatePokemon()

        if (hatchedPokemon == null) {
            player.displayClientMessage("Hatching failed: Unable to generate Pokémon.".red(), true)
            return
        }

        val playerParty = Cobblemon.storage.getParty(player)
        val playerPC = Cobblemon.storage.getPC(player)

        // If PC is full, prevent hatching
        // todo find maybe a better way to check if PC is full
        if (playerPC.getFirstAvailablePosition() == null) {
            player.displayClientMessage("Hatching failed: Your PC is full.".red(), true)
            return
        }

        // Spawn the Pokémon entity at the egg's position
        val spawnedEntity = PokemonEntity(world, hatchedPokemon).apply {
            setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            isNewlyHatched = true
            world.addFreshEntity(this)
        }

        // Trigger cry animation
        afterOnServer(seconds = 1.0f) {
            spawnedEntity.cry()
        }

        // If party has space, add Pokémon to the party
        if (playerParty.getFirstAvailablePosition() != null) {
            playerParty.add(hatchedPokemon)
            player.displayClientMessage("The Pokémon was added to your party!".green(), true)
        }

        // If party is full but PC has space, add to PC and recall Pokémon
        else if (playerPC.add(hatchedPokemon)) {
            player.displayClientMessage("The Pokémon was added to your PC storage.".green(), true)

            // Schedule recall animation
            spawnedEntity.after(seconds = 3.0f) {
                spawnedEntity.recallWithAnimation()
            }

            /*// Remove the Pokémon entity after recall
            spawnedEntity.after(seconds = 4.0f) {
                spawnedEntity.remove(Entity.RemovalReason.DISCARDED)
            }*/
        }

        // Clear the egg from the nest and update the block entity
        setItem(Items.AIR.defaultInstance)
        eggState.resetHatchingState()
        setChanged()
        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
    }




    /**
     * Spawns a Pokémon of the given species around the specified position.
     * @param world The world in which to spawn the Pokémon.
     * @param pos The position around which to spawn the Pokémon.
     * @param species The species of the Pokémon to spawn.
     * @param isMale Whether the Pokémon should be male or female.
     * @return The spawned Pokémon entity, or null if spawning failed.
     *//*
    fun spawnPokemon(
        world: Level,
        pos: BlockPos//,
        //species: PokemonSpecies
    ): Pair<PokemonEntity?,PokemonEntity?> {
        if (world !is ServerLevel) return null to null

        // Generate a random offset within the radius
        val randomOffset = {
            world.random.nextDouble() * SPAWN_RADIUS * 2 - SPAWN_RADIUS
        }
        val spawnPos = pos.offset(randomOffset().toInt(), 0, randomOffset().toInt())

        // todo Use Spawn files
        // todo CHANGE THIS! We probably want a brand new spawner similar to the fishing spawner
        val spawner = BestSpawner.nestSpawner

        val buckets = Cobblemon.bestSpawner.config.buckets

        val chosenBucket = chooseSpawnBucket(buckets)

        val spawnCause = NestSpawnCause(
            spawner = spawner,
            bucket = chosenBucket,
            entity = null
        )

        //
        val result = spawner.run(spawnCause, level as ServerLevel, spawnPos)

        var spawnedPokemon: PokemonEntity? = null
        var partnerPokemon: PokemonEntity? = null
        val resultingSpawn = result?.get()

        if (resultingSpawn is EntitySpawnResult) {
            for (entity in resultingSpawn.entities) {
                // we can query the spawned pokemon here
                spawnedPokemon = (entity as PokemonEntity)

                // spawn a partner pokemon also in a random position
                partnerPokemon = this.spawnPartnerPokemon(world, pos, spawnedPokemon)

                // CRAB IDEA: todo maybe some grass particles? as if they appeared from the grass or forest?


            }
        }

        return spawnedPokemon to partnerPokemon
    }

    fun spawnPartnerPokemon(
        world: ServerLevel,
        pos: BlockPos,
        originalPokemon: PokemonEntity
    ): PokemonEntity? {
        println("Attempting to spawn partner Pokémon...")
        println("Original Pokémon being cloned: ${originalPokemon}")

        val pokemon = originalPokemon.pokemon
        println("Pokemon details before cloning: UUID=${pokemon.uuid}, Ability=${pokemon.ability}, Species=${pokemon.species}, Form=${pokemon.form}")

        // Validate ability before cloning
        if (pokemon.ability.template == Abilities.DUMMY) {
            println("Original Pokémon has DUMMY ability. Rolling a new one before cloning...")
            pokemon.rollAbility()
            println("New ability assigned: ${pokemon.ability}")
        }

        // Perform the clone
        val clonedPokemon = try {
            pokemon.clone()
        } catch (e: IllegalStateException) {
            println("Error cloning Pokémon: ${e.message}")
            return null // Skip spawning if cloning fails
        }

        // Apply further customization to the cloned Pokémon
        clonedPokemon.gender = if (pokemon.gender == Gender.MALE) Gender.FEMALE else Gender.MALE
        println("Cloned Pokémon details: ${clonedPokemon}")

        // Generate a random offset for the partner Pokémon
        val randomOffset = {
            world.random.nextDouble() * SPAWN_RADIUS * 2 - SPAWN_RADIUS
        }
        val spawnPos = pos.offset(randomOffset().toInt(), 0, randomOffset().toInt())

        // Create the partner PokemonEntity
        val partnerPokemonEntity = PokemonEntity(world, clonedPokemon).apply {
            setPos(spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5)
        }

        // Add the partner to the world
        return if (world.addFreshEntity(partnerPokemonEntity)) partnerPokemonEntity else null
    }

    fun chooseSpawnBucket(buckets: List<SpawnBucket>): SpawnBucket {
        val baseIncreases = listOf(2.5F, 1.0F, 0.6F)  // Base increases for the first three buckets beyond the first
        val adjustedWeights = buckets.mapIndexed { index, bucket ->
            if (index == 0) {
                // Placeholder, will be recalculated
                0.0F
            } else {
                val increase = if (index < baseIncreases.size) baseIncreases[index] else baseIncreases.last() + (index - baseIncreases.size + 1) * 0.15F
                bucket.weight + increase
            }
        }.toMutableList()

        // Recalculate the first bucket's weight to ensure the total is 100%
        val totalAdjustedWeight = adjustedWeights.sum() - adjustedWeights[0]  // Corrected to ensure the list contains Floats
        adjustedWeights[0] = 100.0F - totalAdjustedWeight + buckets[0].weight

        // Random selection based on adjusted weights
        val weightSum = adjustedWeights.sum()
        val chosenSum = kotlin.random.Random.nextDouble(weightSum.toDouble()).toFloat()  // Ensure usage of Random from kotlin.random package
        var sum = 0.0F
        adjustedWeights.forEachIndexed { index, weight ->
            sum += weight
            if (sum >= chosenSum) {
                return buckets[index]
            }
        }

        return buckets.first()  // Fallback
    }*/

    // Hopper interaction methods
    override fun getContainerSize(): Int = 1

    override fun isEmpty(): Boolean = eggItem.isEmpty

    override fun getItem(slot: Int): ItemStack = if (slot == 0) eggItem else ItemStack.EMPTY

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        if (slot == 0 && !eggItem.isEmpty) {
            val removed = eggItem.split(amount)
            setChanged()
            return removed
        }
        return ItemStack.EMPTY
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        if (slot == 0) {
            val removed = eggItem
            eggItem = ItemStack.EMPTY
            return removed
        }
        return ItemStack.EMPTY
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot == 0) {
            eggItem = stack
            setChanged()
        }
    }

    override fun setChanged() {
        // Marks the block entity as modified
        level?.blockEntityChanged(worldPosition)

        // Schedules a block update to synchronize with the client
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
    }

    override fun getSlotsForFace(direction: Direction): IntArray {
        return intArrayOf(0)
    }

    /**
     * Allows hoppers to insert items into the container.
     */
    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, direction: Direction?): Boolean {
        // Allow only items with EggComponent to be inserted
        return slot == 0 && stack.get(CobblemonItemComponents.EGG_DATA) != null
    }

    /**
     * Allows hoppers to pull items out of the container.
     */
    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, direction: Direction): Boolean {
        // Allow items to be pulled out only if the slot contains an egg item
        return slot == 0 && !eggItem.isEmpty
    }

    override fun stillValid(player: Player): Boolean = true

    override fun clearContent() {
        eggItem = ItemStack.EMPTY
    }

    companion object {
        private const val RANDOM_SPAWN_CHANCE = 10000 // 1 in 100 chance per random tick
        private const val SPAWN_RADIUS = 5.0 // Radius around the nest to spawn Pokémon

        val TICKER = BlockEntityTicker<NestBlockEntity> { world, pos, state, blockEntity ->
            // Retrieve the Egg from the EggComponent
            val egg = blockEntity.getItem().get(CobblemonItemComponents.EGG_DATA)?.egg

            if (egg != null) {
                /*if (egg.timeToHatch <= 0) {
                    // Try to find a nearby ServerPlayer to pass to hatchPokemon
                    val nearestPlayer = world.getNearestPlayer(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 10.0, false)

                    if (nearestPlayer is ServerPlayer) {
                        // todo Crab Note: I removed this as it was auto-hatching eggs as soon as you got near them
                        //blockEntity.hatchPokemon(state, world, pos, nearestPlayer)
                    }

                    blockEntity.setChanged()
                    return@BlockEntityTicker
                }*/

                // Decrease hatch time if the egg still has time left (faster if in nether)
                val isNether = if (world != null && world.dimension() == Level.NETHER) true else false

                if (egg.timeToHatch > 0F) {
                    egg.timeToHatch = when {
                        isNether -> egg.timeToHatch - Cobblemon.config.netherNestHatchRate
                        else -> egg.timeToHatch - Cobblemon.config.nestHatchRate
                    }
                }

                // Sync to client every 10 seconds (200 ticks)
                if (egg.timeToHatch % 200F == 0F) {
                    //blockEntity.setItem(egg) // Update component and synchronize
                    //blockEntity.setChanged()
                    val test = 1
                }
            } else {
                // Attempt to spawn a random egg in the nest with a small chance
                if (world.random.nextInt(RANDOM_SPAWN_CHANCE) == 0 && false) { // TODO remove the false part and probably remove this entirely later too

                    /*val (parent1, parent2) = blockEntity.spawnPokemon(world, pos)

                    if (parent1 != null && parent2 != null) {
                        val childResult = BreedingLogicManager.breed(parent1.pokemon,parent2.pokemon)

                        // Create an egg with the parents
                        val egg = childResult.pokemon?.species?.let {
                            Egg(
                                    species = it.resourceIdentifier,
                                    parentOne = parent1.pokemon, // Parent one UUID
                                    parentTwo = parent2.pokemon, // Parent two UUID
                                    form = childResult.pokemon.formData.name.lowercase(),
                                    timeToHatch = 200F // 10 seconds hatch time
                            )
                        }*/ // TODO KILL ALL OF THIS CODE ONCE WE HAVE A HABITAT SPAWNER
                        if (egg != null) {
                            blockEntity.setItem(egg.createEggItemStack())
                        }
                        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)

                }
            }
        }
    }
}