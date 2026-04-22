/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.block.photoprinter.PhotoPrinterMenu
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.block.state.BlockState

class PhotoPrinterBlockEntity(pos: BlockPos, state: BlockState) : BaseContainerBlockEntity(CobblemonBlockEntities.PHOTO_PRINTER, pos, state) {
    companion object {
        const val INPUT_SLOT = 0
        const val RESULT_SLOT = 1
        const val SLOT_COUNT = 2

        private const val SELECTED_PHOTO_TAG = "selectedPhotoId"
    }

    private var items: NonNullList<ItemStack> = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY)
    var selectedPhotoId: String? = null

    val containerData: ContainerData = object : ContainerData {
        override fun getCount(): Int = 3

        override fun get(index: Int): Int {
            return when (index) {
                0 -> blockPos.x
                1 -> blockPos.y
                2 -> blockPos.z
                else -> 0
            }
        }

        override fun set(index: Int, value: Int) = Unit
    }

    override fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu {
        return PhotoPrinterMenu(containerId, inventory, this, containerData)
    }

    override fun getDefaultName(): Component = Component.translatable("cobblemon.container.photo_printer")

    override fun getDisplayName(): Component = Component.translatable("block.cobblemon.photo_printer")

    override fun getItems(): NonNullList<ItemStack> = items

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items = items
    }

    override fun getContainerSize(): Int = SLOT_COUNT

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = slot == INPUT_SLOT

    override fun saveAdditional(compound: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(compound, registries)
        ContainerHelper.saveAllItems(compound, items, registries)
        selectedPhotoId?.let { compound.putString(SELECTED_PHOTO_TAG, it) }
    }

    override fun loadAdditional(compound: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(compound, registries)
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY)
        ContainerHelper.loadAllItems(compound, items, registries)
        selectedPhotoId = compound.getString(SELECTED_PHOTO_TAG).takeIf { it.isNotEmpty() }
    }

    override fun stillValid(player: Player): Boolean {
        return super.stillValid(player)
    }
}
