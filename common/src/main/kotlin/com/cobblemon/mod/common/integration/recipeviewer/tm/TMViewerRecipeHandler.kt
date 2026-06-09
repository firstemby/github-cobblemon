package com.cobblemon.mod.common.integration.recipeviewer.tm

import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.block.entity.TMMachineBlockEntity
import com.cobblemon.mod.common.block.tmmachine.TMMachineMenu
import com.cobblemon.mod.common.client.gui.tmmachine.TMMachineScreen
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import com.cobblemon.mod.common.net.messages.client.ui.SetActiveTMPacket
import com.cobblemon.mod.common.net.messages.client.ui.SetTMMachineContainerDataPacket
import net.minecraft.client.Minecraft
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

    override fun additionalHandle(recipe: TMViewerRecipe) {
        val tMMachineScreen = Minecraft.getInstance().screen as? TMMachineScreen ?: return
        val burnActive = tMMachineScreen.menu.containerData?.get(TMMachineBlockEntity.BURN_ACTIVE_INDEX) ?: 0
        if (burnActive == 1) CobblemonNetwork.sendToServer(SetTMMachineContainerDataPacket(TMMachineBlockEntity.BURN_ACTIVE_INDEX, 0))
        CobblemonNetwork.sendToServer(SetActiveTMPacket(recipe.originalTM))
    }
}