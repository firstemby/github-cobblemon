package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.breeding.BreedingLogicManager
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.components.EggComponent
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.*
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class IncubatorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonBlockEntities.INCUBATOR, pos, state) {

    companion object {
       val TICKER = BlockEntityTicker<IncubatorBlockEntity> { world, pos, state, blockEntity ->
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
                if (egg.timeToHatch > 0F) { egg.timeToHatch -= 1.25F }

                // Sync to client every 10 seconds (200 ticks)
                if (egg.timeToHatch % 200F == 0F) {
                    blockEntity.setEgg(egg) // Update component and synchronize
                    blockEntity.setChanged()
                }
            }
        }
    }

    var eggComponent: EggComponent? = null
        private set

    /**
     * Updates the egg in the incubator when interacted with by a player.
     */
    fun updateEgg(player: Player, hand: InteractionHand): ItemInteractionResult {
        val heldStack = player.getItemInHand(hand)

        // Check for EggComponent in the player's held item
        val eggComponent = heldStack.get(CobblemonItemComponents.EGG_DATA)

        return when {
            eggComponent != null && this.eggComponent == null -> {
                this.eggComponent = eggComponent
                if (!player.isCreative) {
                    heldStack.shrink(1)
                }
                setChanged()
                ItemInteractionResult.SUCCESS
            }

            this.eggComponent != null -> {
                val eggStack = ItemStack(Items.AIR).apply {
                    set(CobblemonItemComponents.EGG_DATA, this@IncubatorBlockEntity.eggComponent!!)
                }
                player.addItem(eggStack)
                this.eggComponent = null
                setChanged()
                ItemInteractionResult.SUCCESS
            }

            else -> ItemInteractionResult.FAIL
        }
    }

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
     * Retrieves the EggComponent stored in the incubator.
     */
    fun getEgg(): Egg? = eggComponent?.egg

    override fun saveAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(tag, registryLookup)
        eggComponent?.let {
            CobblemonItemComponents.EGG_DATA.codec()
                ?.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, it)
                ?.result()
                ?.ifPresent { encoded -> tag.put("EggComponent", encoded) }
        }
    }

    override fun loadAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(tag, registryLookup)
        if (tag.contains("EggComponent")) {
            eggComponent = CobblemonItemComponents.EGG_DATA.codec()
                ?.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("EggComponent"))
                ?.result()
                ?.orElse(null)
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        val tag = saveWithoutMetadata(registryLookup)
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
            return if (currentEgg.timeToHatch <= 0F) {
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

        // If party has space, add Pokémon to the party
        if (playerParty.getFirstAvailablePosition() != null) {
            playerParty.add(hatchedPokemon)
            player.displayClientMessage("The Pokémon was added to your party!".green(), true)
        }

        // If party is full but PC has space, add to PC and recall Pokémon
        else if (playerPC.add(hatchedPokemon)) {
            player.displayClientMessage("The Pokémon was added to your PC storage.".green(), true)
        }

        // Clear the egg from the nest and update the block entity
        setEgg(null)
        setChanged()
        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
    }
}
