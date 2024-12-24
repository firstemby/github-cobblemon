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
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.components.EggComponent
import com.cobblemon.mod.common.net.serverhandling.storage.SendOutPokemonHandler
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.toBlockPos
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
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.ticks.TickPriority

class NestBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(CobblemonBlockEntities.NEST, pos, state) {
    var eggComponent: EggComponent? = null
        private set
    var renderState: BlockEntityRenderState? = null

    /**
     * Sets the EggComponent and synchronizes it.
     */
    fun setEgg(egg: Egg?) {
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

        // Log before clearing
        println("Dropping egg: ${currentEgg.species} at $pos")

        // Create an ItemStack with the EggComponent
        val eggItemStack = CobblemonItems.POKEMON_EGG.defaultInstance.apply {
            set(CobblemonItemComponents.EGG_DATA, EggComponent(currentEgg))
        }

        // Debug log to verify component data
        val component = eggItemStack.get(CobblemonItemComponents.EGG_DATA)
        println("EggComponent on dropped ItemStack: $component")

        // Spawn the egg as an entity
        val itemEntity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, eggItemStack)
        world.addFreshEntity(itemEntity)

        // Clear the egg from the nest
        setEgg(null)
        setChanged()
        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS) // Notify client
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
        val egg = getEgg() ?: return
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
            world.addFreshEntity(this)
        }

        // todo WHY WON'T THEY CRY :(
        // Trigger cry animation
        afterOnServer(seconds = 1.0f) {
            spawnedEntity.cry()
        }

        // If party has space, add Pokémon to the party
        if (playerParty.getFirstAvailablePosition() != null){
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
        setEgg(null)
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
     */
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
    }

    companion object {
        private const val RANDOM_SPAWN_CHANCE = 1000 // 1 in 100 chance per random tick
        private const val SPAWN_RADIUS = 5.0 // Radius around the nest to spawn Pokémon

        val TICKER = BlockEntityTicker<NestBlockEntity> { world, pos, state, blockEntity ->
            // Retrieve the Egg from the EggComponent
            val egg = blockEntity.getEgg()

            if (egg != null) {
                if (egg.timeToHatch <= 0) {
                    // Try to find a nearby ServerPlayer to pass to hatchPokemon
                    val nearestPlayer = world.getNearestPlayer(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 10.0, false)

                    if (nearestPlayer is ServerPlayer) {
                        // todo Crab Note: I removed this as it was auto-hatching eggs as soon as you got near them
                        //blockEntity.hatchPokemon(state, world, pos, nearestPlayer)
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
            } else {
                // Attempt to spawn a random egg in the nest with a small chance
                if (world.random.nextInt(RANDOM_SPAWN_CHANCE) == 0) {

                    val (parent1, parent2) = blockEntity.spawnPokemon(world, pos)

                    if (parent1 != null && parent2 != null) {
                        val childResult = BreedingLogicManager.breed(parent1.pokemon,parent2.pokemon)

                        // Create an egg with the parents
                        val egg = childResult.pokemon?.species?.let {
                            Egg(
                                    species = it.resourceIdentifier,
                                    parentOne = parent1.pokemon, // Parent one UUID
                                    parentTwo = parent2.pokemon, // Parent two UUID
                                    timeToHatch = 20 * 10 // 10 seconds hatch time
                            )
                        }
                        if (egg != null) {
                            blockEntity.setEgg(egg)
                        }
                        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
                    }

                }
            }
        }
    }
}