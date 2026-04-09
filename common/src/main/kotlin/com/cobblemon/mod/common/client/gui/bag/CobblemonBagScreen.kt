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
import com.cobblemon.mod.common.item.bag.CobblemonBagCategories
import com.cobblemon.mod.common.item.bag.CobblemonBagMenu
import com.cobblemon.mod.common.util.cobblemonResource
import juuxel.adorn.util.Colors
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
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

        private val BUTTON_LEFT = cobblemonResource("textures/gui/bag/button_left.png")
        private val BUTTON_RIGHT = cobblemonResource("textures/gui/bag/button_right.png")
    }

    val categoriesWidgets = mutableListOf<BagCategoryButton>()
    lateinit var allItemsCategoryWidget: BagCategoryButton
    lateinit var leftCategoryPageButton: BagCategoryPageButton
    lateinit var rightCategoryPageButton: BagCategoryPageButton
    var currentCategoryPage = 0

    init {
        this.imageWidth = BACKGROUND_WIDTH
        this.imageHeight = BACKGROUND_HEIGHT + 27
    }

    override fun init() {
        super.init()

        for (i in 0..3) {
            if (i < CobblemonBagCategories.categories.size) {
                categoriesWidgets.add(
                    BagCategoryButton(leftPos + 7 + i * 33, topPos, this, i) {
                        if (menu.currentlySelectedCategory != i) {
                            menu.switchCategory(i)
                            playSound(CobblemonSounds.GUI_CLICK)
                        }
                    }.also { addRenderableWidget(it) }
                )
            } else break
        }

        allItemsCategoryWidget = BagCategoryButton(leftPos + 145, topPos, this, -1) {
            if (menu.currentlySelectedCategory != -1) {
                menu.switchCategory(-1)
                playSound(CobblemonSounds.GUI_CLICK)
            }
        }.also { addRenderableWidget(it) }

        leftCategoryPageButton = BagCategoryPageButton(leftPos + 149, topPos + 32, this, true) {
            if (leftCategoryPageButton.enabled) {
                cycleCategoryPage(false)
                playSound(CobblemonSounds.GUI_CLICK)
            }
        }.also { addRenderableWidget(it) }
        rightCategoryPageButton = BagCategoryPageButton(leftPos + 160, topPos + 32, this, false) {
            if (rightCategoryPageButton.enabled) {
                cycleCategoryPage(true)
                playSound(CobblemonSounds.GUI_CLICK)
            }
        }.also { addRenderableWidget(it) }
    }

    override fun renderBg(context: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        blitk(
            matrixStack = context.pose(),
            texture = BACKGROUND,
            x = leftPos, y = topPos + 27,
            width = BACKGROUND_WIDTH, height = BACKGROUND_HEIGHT
        )
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {}

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(context, mouseX, mouseY, partialTick)

        //Titles
        context.drawString(font, title, leftPos + 11, topPos + 33, Colors.WHITE, false)
        context.drawString(font, playerInventoryTitle, leftPos + 11, topPos + 129, 4210752, false)

        renderTooltip(context, mouseX, mouseY)
    }

    fun cycleCategoryPage(next: Boolean) {
        currentCategoryPage = (currentCategoryPage + if (next) 1 else -1).coerceIn(0, (CobblemonBagCategories.categories.size - 1) / 4)
        categoriesWidgets.forEach {
            removeWidget(it)
        }
        categoriesWidgets.clear()
        for (i in 0..3) {
            val adjustedIndex = i + 4 * currentCategoryPage
            if (adjustedIndex < CobblemonBagCategories.categories.size) {
                categoriesWidgets.add(
                    BagCategoryButton(leftPos + 7 + i * 33, topPos, this, adjustedIndex) {
                        menu.switchCategory(adjustedIndex)
                    }.also { addRenderableWidget(it) }
                )
            } else break
        }
        leftCategoryPageButton.enabled = currentCategoryPage > 0
        rightCategoryPageButton.enabled = currentCategoryPage < (CobblemonBagCategories.categories.size - 1) / 4
    }

    fun playSound(soundEvent: SoundEvent) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
    }
}