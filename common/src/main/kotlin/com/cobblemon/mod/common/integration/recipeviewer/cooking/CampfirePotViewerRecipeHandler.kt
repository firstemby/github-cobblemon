/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer.cooking

import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot

class CampfirePotViewerRecipeHandler: RecipeViewerRecipeHandler<CookingPotMenu> {
    override fun getInputSlots(menu: CookingPotMenu): List<Slot> {
        return menu.slots.filter { it.index in CampfireBlockEntity.CRAFTING_GRID_SLOTS }
    }

    override fun getInventorySlots(menu: CookingPotMenu): List<Slot> {
        return menu.slots.filter { it.index in CampfireBlockEntity.PLAYER_INVENTORY_SLOTS || it.index in CampfireBlockEntity.PLAYER_HOTBAR_SLOTS }
    }

    override fun getMenuClass(): Class<CookingPotMenu> = CookingPotMenu::class.java
    override fun getMenuType(): MenuType<CookingPotMenu> = CobblemonMenuType.COOKING_POT
}