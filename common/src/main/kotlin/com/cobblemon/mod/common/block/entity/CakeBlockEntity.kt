/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.item.components.BaitEffectsComponent
import com.cobblemon.mod.common.item.components.FlavourComponent
import com.cobblemon.mod.common.item.components.FoodColourComponent
import com.cobblemon.mod.common.util.DataKeys
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

open class CakeBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : BlockEntity(type, pos, state) {
    open val maxBites: Int
        get() = 6

    var flavourComponent: FlavourComponent? = null
    var baitEffectsComponent: BaitEffectsComponent? = null
    var foodColourComponent: FoodColourComponent? = null
    var bites: Int = 0

    fun initializeFromItemStack(itemStack: ItemStack) {
        flavourComponent = itemStack.get(CobblemonItemComponents.FLAVOUR)
        baitEffectsComponent = itemStack.get(CobblemonItemComponents.BAIT_EFFECTS)
        foodColourComponent = itemStack.get(CobblemonItemComponents.FOOD_COLOUR)
    }

    override fun saveAdditional(
        tag: CompoundTag,
        registries: HolderLookup.Provider
    ) {
        super.saveAdditional(tag, registries)

        tag.putInt(DataKeys.CAKE_BITES, bites)
        flavourComponent?.let { component ->
            CobblemonItemComponents.FLAVOUR.codec()!!
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .ifPresent { encodedTag ->
                   tag.put(DataKeys.CAKE_FLAVOUR, encodedTag)
                }
        }
        baitEffectsComponent?.let { component ->
            CobblemonItemComponents.BAIT_EFFECTS.codec()!!
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .ifPresent { encodedTag ->
                    tag.put(DataKeys.CAKE_BAIT_EFFECTS, encodedTag)
                }
        }
        foodColourComponent?.let { component ->
            CobblemonItemComponents.FOOD_COLOUR.codec()!!
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .ifPresent { encodedTag ->
                    tag.put(DataKeys.CAKE_FOOD_COLOUR, encodedTag)
                }
        }
    }

    override fun loadAdditional(
        tag: CompoundTag,
        registries: HolderLookup.Provider
    ) {
        super.loadAdditional(tag, registries)

        bites = tag.getInt(DataKeys.CAKE_BITES)
        if (tag.contains(DataKeys.CAKE_FLAVOUR)) {
            CobblemonItemComponents.FLAVOUR.codec()
                ?.parse(NbtOps.INSTANCE, tag.get(DataKeys.CAKE_FLAVOUR))
                ?.result()
                ?.ifPresent { component ->
                    flavourComponent = component
                }
        }
        if (tag.contains(DataKeys.CAKE_BAIT_EFFECTS)) {
            CobblemonItemComponents.BAIT_EFFECTS.codec()
                ?.parse(NbtOps.INSTANCE, tag.get(DataKeys.CAKE_BAIT_EFFECTS))
                ?.result()
                ?.ifPresent { component ->
                    baitEffectsComponent = component
                }
        }
        if (tag.contains(DataKeys.CAKE_FOOD_COLOUR)) {
            CobblemonItemComponents.FOOD_COLOUR.codec()
                ?.parse(NbtOps.INSTANCE, tag.get(DataKeys.CAKE_FOOD_COLOUR))
                ?.result()
                ?.ifPresent { component ->
                    foodColourComponent = component
                }
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener?>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        saveAdditional(tag, registries)
        return tag
    }

    fun toItemStack(): ItemStack {
        val stack = ItemStack(this.blockState.block)
        if (stack.item == CobblemonItems.LURE_CAKE && baitEffectsComponent != null) {
            stack.set(CobblemonItemComponents.BAIT_EFFECTS, baitEffectsComponent)
        }
        if (flavourComponent != null) {
            stack.set(CobblemonItemComponents.FLAVOUR, flavourComponent)
        }
        if (foodColourComponent != null) {
            stack.set(CobblemonItemComponents.FOOD_COLOUR, foodColourComponent)
        }
        return stack
    }
}