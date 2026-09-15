/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.container

import net.minecraft.world.Container
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class MolangSlot(
    container: Container,
    index: Int,
    x: Int,
    y: Int,
    private val collectionConfig: CollectionConfig
) : Slot(container, index, x, y) {

    override fun mayPlace(stack: ItemStack): Boolean {
        return collectionConfig.isItemAllowed(stack) && container.canPlaceItem(containerSlot, stack)
    }

    override fun getMaxStackSize(stack: ItemStack): Int = container.getMaxStackSize(stack)
}
