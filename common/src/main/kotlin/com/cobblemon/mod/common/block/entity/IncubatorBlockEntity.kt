package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.item.components.EggComponent
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class IncubatorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonBlockEntities.INCUBATOR, pos, state), WorldlyContainer {

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

    override fun clearContent() {
        eggComponent = null
    }

    override fun getContainerSize(): Int = 1

    override fun isEmpty(): Boolean = eggComponent == null

    override fun getItem(slot: Int): ItemStack {
        return ItemStack(Items.AIR).apply {
            eggComponent?.let {
                set(CobblemonItemComponents.EGG_DATA, it)
            }
        }
    }

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val stack = getItem(slot)
        eggComponent = null
        setChanged()
        return stack
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        eggComponent = stack.get(CobblemonItemComponents.EGG_DATA)
        setChanged()
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        val stack = getItem(slot)
        eggComponent = null
        return stack
    }

    override fun stillValid(player: Player): Boolean = true

    override fun getSlotsForFace(side: Direction): IntArray = intArrayOf(0)

    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, dir: Direction?): Boolean {
        return eggComponent == null && stack.get(CobblemonItemComponents.EGG_DATA) != null
    }

    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, dir: Direction): Boolean {
        return eggComponent != null
    }
}
