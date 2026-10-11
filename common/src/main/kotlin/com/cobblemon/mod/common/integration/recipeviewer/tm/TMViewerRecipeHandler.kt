/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer.tm

import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.block.entity.TMMachineBlockEntity
import com.cobblemon.mod.common.block.tmmachine.TMMachineMenu
import com.cobblemon.mod.common.client.gui.tmmachine.TMMachineScreen
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import com.cobblemon.mod.common.net.messages.client.ui.SetActiveTMPacket
import com.cobblemon.mod.common.net.messages.client.ui.SetTMMachineContainerDataPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot

class TMViewerRecipeHandler: RecipeViewerRecipeHandler<TMViewerRecipe, TMMachineMenu> {
    override fun getInputSlots(menu: TMMachineMenu): List<Slot> {
        return menu.slots.filter { it.index in 37..40 }
    }

    override fun getInventorySlots(menu: TMMachineMenu): List<Slot> {
        return menu.slots.filter { it.index in 0..35}
    }

    override fun getMenuClass(): Class<TMMachineMenu> = TMMachineMenu::class.java
    override fun getMenuType(): MenuType<TMMachineMenu> = CobblemonMenuType.TM_MACHINE

    override fun isApplicable(recipe: TMViewerRecipe): Boolean {
        return recipe.recipeKnown()
    }

    override fun additionalHandle(recipe: TMViewerRecipe, menu: TMMachineMenu, screen: AbstractContainerScreen<TMMachineMenu>?) {
        (screen as? TMMachineScreen)?.let {
            it.setScreenFromMode(TMMachineScreen.TM_BURN_MODE)
            it.sortType = ElementalTypes.get(recipe.originalTM.type)
            it.setMoveList()
        }

        if (menu.currentlySelectedTM != recipe.originalTM) {
            CobblemonNetwork.sendToServer(SetActiveTMPacket(recipe.originalTM))
        }
    }
}