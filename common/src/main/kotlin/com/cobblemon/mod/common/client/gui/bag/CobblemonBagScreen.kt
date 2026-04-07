package com.cobblemon.mod.common.client.gui.bag

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.gui.cookingpot.CookingPotScreen
import com.cobblemon.mod.common.item.bag.CobblemonBagMenu
import com.cobblemon.mod.common.util.cobblemonResource
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

@Environment(EnvType.CLIENT)
class CobblemonBagScreen(
    menu: CobblemonBagMenu,
    playerInventory: Inventory,
    title: Component
): AbstractContainerScreen<CobblemonBagMenu>(
    menu,
    playerInventory,
    Component.translatable("cobblemon.container.bag")
) {

    companion object {
        private const val BACKGROUND_HEIGHT = 197
        private const val BACKGROUND_WIDTH = 184

        private val BACKGROUND = cobblemonResource("textures/gui/bag/bag_base.png")
    }

    init {
        this.imageWidth = BACKGROUND_WIDTH
        this.imageHeight = BACKGROUND_HEIGHT
    }

    override fun renderBg(context: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        blitk(
            matrixStack = context.pose(),
            texture = BACKGROUND,
            x = (width - BACKGROUND_WIDTH) / 2, y = (height - BACKGROUND_HEIGHT) / 2,
            width = BACKGROUND_WIDTH, height = BACKGROUND_HEIGHT
        )
    }
}