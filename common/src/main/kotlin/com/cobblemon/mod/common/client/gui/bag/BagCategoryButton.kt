/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.bag

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.item.bag.CobblemonBagCategories
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class BagCategoryButton(
    x: Int,
    y: Int,
    val parent: CobblemonBagScreen,
    val category: Int,
    val action: OnPress
): Button(x, y, WIDTH, HEIGHT, Component.empty(), action, DEFAULT_NARRATION), CobblemonRenderable {
    companion object {
        private const val WIDTH = 32
        private const val HEIGHT = 31

        private val CATEGORY_SLOT = cobblemonResource("textures/gui/bag/bag_tab.png")
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        blitk(
            matrixStack = context.pose(),
            texture = CATEGORY_SLOT,
            x = x, y = y,
            width = WIDTH, height = HEIGHT,
            textureHeight = 64,
            vOffset = if (parent.menu.currentlySelectedCategory == category) 32 else 0
        )

        blitk(
            matrixStack = context.pose(),
            texture = if (category == -1) ResourceLocation("minecraft", "textures/item/bundle.png") else CobblemonBagCategories.categories[category].icon,
            x = x + 8, y = y + 9,
            width = 16, height = 16
        )

        if (isHovered) {
            context.renderTooltip(Minecraft.getInstance().font, if (category == -1) lang("all_items") else CobblemonBagCategories.categories[category].categoryName.asTranslated(), mouseX, mouseY)
        }
    }

    override fun playDownSound(handler: SoundManager) {}
}