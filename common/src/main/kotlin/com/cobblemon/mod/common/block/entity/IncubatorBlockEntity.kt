package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.block.IncubatorBlock
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
import java.util.*

class IncubatorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonBlockEntities.INCUBATOR, pos, state), WorldlyContainer {

    companion object {
       val TICKER = BlockEntityTicker<IncubatorBlockEntity> { world, pos, state, blockEntity ->
            // Retrieve the Egg from the EggComponent
            val egg = blockEntity.getItem(0)?.get(CobblemonItemComponents.EGG_DATA)?.egg

            if (egg != null) {
                if (egg.timeToHatch <= 0) {
                    // todo egg is ready to hatch

                    blockEntity.setChanged()
                    return@BlockEntityTicker
                }

                // Decrease hatch time if the egg still has time left
                if (egg.timeToHatch > 0F) { egg.timeToHatch -= 1.25F }

                // todo is this needed?
                // Sync to client every 10 seconds (200 ticks)
                /*if (egg.timeToHatch % 200F == 0F) {
                    blockEntity.setEgg(egg) // Update component and synchronize
                    blockEntity.setChanged()
                }*/
            }
        }
    }

    private var eggItem: ItemStack = ItemStack.EMPTY
    var ownerUUID: UUID? = null

    override fun saveAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(tag, registryLookup)
        tag.put("EggItem", eggItem.saveOptional(registryLookup))
        ownerUUID?.let { tag.putUUID("OwnerUUID", it) }
    }

    override fun loadAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(tag, registryLookup)
        eggItem = ItemStack.parseOptional(registryLookup, tag.getCompound("EggItem"))

        if (tag.contains("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID")
        }
    }

    fun getComparatorOutput(): Int {
        val eggComponent = eggItem.get(CobblemonItemComponents.EGG_DATA)
        val egg = eggComponent?.egg ?: return 0

        /*val maxHatchTime = 100F // todo replace this with the egg's species expected time to hatch (will need a new algorithm method. Maybe based off of egg cycle + other stuff)
        val currentHatchTime = egg.timeToHatch.coerceAtLeast(0F).coerceAtMost(maxHatchTime)

        // Calculate progress: 0 (ready) to 15
        val progress = ((maxHatchTime - currentHatchTime) / maxHatchTime * 15).toInt()

        return progress*/
        if (egg.timeToHatch <= 0) return 15 else return 0 // send signal based on whether it is ready to hatch or now for now (we can make it more advanced later)
    }


    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        val tag = saveWithoutMetadata(registryLookup)
        return tag
    }


    fun dropItem(world: Level, pos: BlockPos, state: BlockState) {
        if (!eggItem.isEmpty) {
            val itemEntity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, eggItem.copy())
            world.addFreshEntity(itemEntity)
            eggItem = ItemStack.EMPTY
            setChanged()
            world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
        }
    }

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
                hatchPokemon(state, world, pos, player)
                return InteractionResult.SUCCESS
            }

            // Otherwise, drop the egg
            dropItem(world, pos, state)
            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f)
            return InteractionResult.SUCCESS
        }

        // Place a new egg if the player is holding one
        if (!heldItem.isEmpty && heldItem.get(CobblemonItemComponents.EGG_DATA) != null) {
            setItem(0,heldItem.split(1))
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
        val eggItem = getItem(0) // Retrieve the egg item from the container
        val egg = eggItem.get(CobblemonItemComponents.EGG_DATA)?.egg ?: return
        if (world !is ServerLevel) return

        val parentOne = egg.parentOne
        val parentTwo = egg.parentTwo

        // Validate parent Pokémon
        if (parentOne == null || parentTwo == null) {
            player.displayClientMessage("Hatching failed: Parent Pokémon not found.".red(), true)
            return
        }

        // Perform breeding to generate the Pokémon
        val breedingResult = BreedingLogicManager.breed(parentOne, parentTwo)
        val hatchedPokemon = breedingResult.pokemon?.generatePokemon()

        if (hatchedPokemon == null) {
            player.displayClientMessage("Hatching failed: Unable to generate Pokémon.".red(), true)
            return
        }

        val playerParty = Cobblemon.storage.getParty(player)
        val playerPC = Cobblemon.storage.getPC(player)

        // Ensure PC has space for the Pokémon
        if (playerPC.getFirstAvailablePosition() == null) {
            player.displayClientMessage("Hatching failed: Your PC is full.".red(), true)
            return
        }

        // Add Pokémon to the party or PC
        if (playerParty.getFirstAvailablePosition() != null) {
            playerParty.add(hatchedPokemon)
            player.displayClientMessage("The Pokémon was added to your party!".green(), true)
        } else if (playerPC.add(hatchedPokemon)) {
            player.displayClientMessage("The Pokémon was added to your PC storage.".green(), true)
        } else {
            player.displayClientMessage("Hatching failed: Unable to store Pokémon.".red(), true)
            return
        }

        // Clear the egg item and synchronize
        setItem(0, ItemStack.EMPTY)
        setChanged()
        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
    }

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
        val hasEgg = !eggItem.isEmpty

        level?.let {
            if (blockState.getValue(IncubatorBlock.HAS_EGG) != hasEgg) {
                // Update the block state with the new `HAS_EGG` value
                val newState = blockState.setValue(IncubatorBlock.HAS_EGG, hasEgg)
                level?.setBlock(worldPosition, newState, 3)
            }

            it.blockEntityChanged(worldPosition)
            it.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
            it.updateNeighborsAt(worldPosition, blockState.block)
        }
    }

    override fun stillValid(player: Player): Boolean = true

    override fun getSlotsForFace(direction: Direction): IntArray = intArrayOf(0)

    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, direction: Direction?): Boolean {
        return slot == 0 && stack.get(CobblemonItemComponents.EGG_DATA) != null
    }

    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, direction: Direction): Boolean {
        return slot == 0 && !eggItem.isEmpty
    }

    override fun clearContent() {
        eggItem = ItemStack.EMPTY
    }
}
