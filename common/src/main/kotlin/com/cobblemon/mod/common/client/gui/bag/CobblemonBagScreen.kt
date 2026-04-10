/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.bag

import com.cobblemon.mod.common.CobblemonNetwork.sendToServer
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.item.bag.CobblemonBagCategories
import com.cobblemon.mod.common.item.bag.CobblemonBagMenu
import com.cobblemon.mod.common.net.messages.client.bag.UpdateBagMenuDataPacket
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
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

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
        private val SCROLL_TRACK = cobblemonResource("textures/gui/bag/scrollbar_track.png")
        private val SCROLL_SLIDE = cobblemonResource("textures/gui/bag/scrollbar_slide.png")
    }

    val categoriesWidgets = mutableListOf<BagCategoryButton>()
    lateinit var allItemsCategoryWidget: BagCategoryButton
    lateinit var leftCategoryPageButton: BagCategoryPageButton
    lateinit var rightCategoryPageButton: BagCategoryPageButton
    var currentCategoryPage = 0
    var lastCategory = menu.currentlySelectedCategory
    var lastScroll = menu.scroll
    var scrolling = false

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
                            menu.changeCategory(i)
                            playSound(CobblemonSounds.GUI_CLICK)
                        }
                    }.also { addRenderableWidget(it) }
                )
            } else break
        }

        allItemsCategoryWidget = BagCategoryButton(leftPos + 145, topPos, this, -1) {
            if (menu.currentlySelectedCategory != -1) {
                menu.changeCategory(-1)
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

        //Scroll
        val scrollYOffset = if (menu.maxScroll == 0) {
            if (menu.scroll == 0) 0 else 53
        } else ((min(menu.scroll, menu.maxScroll).toFloat() / menu.maxScroll) * 53).toInt()
        val trackHeight = 71 - scrollYOffset - 13

        if (trackHeight > 0) {
            blitk(
                matrixStack = context.pose(),
                texture = SCROLL_TRACK,
                x = leftPos + 161, y = topPos + 49 + (71 - trackHeight),
                width = 9, height = trackHeight,
                textureHeight = 71,
                vOffset = 71 - trackHeight
            )
        }

        blitk(
            matrixStack = context.pose(),
            texture = SCROLL_SLIDE,
            x = leftPos + 161, y = topPos + 49 + scrollYOffset,
            width = 9, height = 17
        )

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
                        menu.changeCategory(adjustedIndex)
                    }.also { addRenderableWidget(it) }
                )
            } else break
        }
        leftCategoryPageButton.enabled = currentCategoryPage > 0
        rightCategoryPageButton.enabled = currentCategoryPage < (CobblemonBagCategories.categories.size - 1) / 4
    }

    fun updateScrollValue(mouseY: Double) {
        val adjustedMouseY = mouseY - topPos - 49.0
        if (adjustedMouseY < 0) {
            menu.scroll = 0
        } else if (adjustedMouseY > 71) {
            menu.scroll = menu.maxScroll
        } else {
            menu.scroll = ((adjustedMouseY / 71.0) * (menu.maxScroll)).roundToInt()
        }
        menu.setupSlotsBasedOnContent()
    }

    override fun slotClicked(slot: Slot?, slotId: Int, mouseButton: Int, type: ClickType) {
        if (slotId in CobblemonBagMenu.BAG_SLOTS && (lastCategory != menu.currentlySelectedCategory || lastScroll != menu.scroll)) {
            lastCategory = menu.currentlySelectedCategory
            lastScroll = menu.scroll
            sendToServer(UpdateBagMenuDataPacket(menu.currentlySelectedCategory, menu.scroll)) // Synchronize this data
        }
        super.slotClicked(slot, slotId, mouseButton, type)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY < 0 && menu.scroll < menu.maxScroll) {
            menu.scroll += 1
        } else if (scrollY > 0 && menu.scroll > 0) {
            menu.scroll -= 1
        }
        menu.setupSlotsBasedOnContent()
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX in (leftPos + 160.0)..(leftPos + 171.0) && mouseY in (topPos + 49.0)..(topPos + 120.0)) {
            scrolling = true
            updateScrollValue(mouseY)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        scrolling = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (scrolling) {
            updateScrollValue(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    fun playSound(soundEvent: SoundEvent) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
    }
}