/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer

import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot

interface RecipeViewerRecipeHandler<E: AbstractContainerMenu> {
    fun getInputSlots(menu: E): List<Slot> //Returns crafting slots
    fun getInventorySlots(menu: E): List<Slot>

    fun getMenuClass(): Class<E>
    fun getMenuType(): MenuType<E>
}