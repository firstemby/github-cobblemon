/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pc

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.pc.WallpapersScrollingWidget.WallpaperEntry
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.gui.PCBoxWallpaperRepository
import com.cobblemon.mod.common.net.messages.server.storage.pc.RequestChangePCBoxWallpaperPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class WallpapersScrollingWidget(
    pX: Int, val pY: Int,
    val pcGui: PCGUI,
    val storageWidget: StorageWidget
) : ScrollingWidget<WallpaperEntry>(
    width = WIDTH,
    height = HEIGHT,
    left = pX,
    top = 0,
    slotHeight = SLOT_HEIGHT + SLOT_PADDING,
    scrollBarWidth = SCROLL_BAR_WIDTH
) {
    companion object {
        const val WIDTH = 67
        const val HEIGHT = 147
        const val SLOT_WIDTH = 58
        const val SLOT_HEIGHT = 53
        const val SLOT_PADDING = 3
        const val SCROLL_BAR_WIDTH = 3
        val ENTRY_HOVERED = cobblemonResource("textures/gui/pc/wallpaper_entry_hover.png")
        val BACKGROUND = cobblemonResource("textures/gui/pc/wallpaper_widget_background.png")
    }

    init {
        this.visible = false
        this.y = pY
        createEntries()
    }

    override fun renderWidget(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        blitk(
            matrixStack = context.pose(),
            texture = BACKGROUND,
            x = x - 1,
            y = y - 1,
            width = width + 1,
            height = height + 2
        )

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.wallpapers").bold(),
            x = x + 23,
            y = y - 18.5,
            centered = true,
            shadow = true
        )

        super.renderWidget(context, mouseX, mouseY, delta)
    }

    private fun createEntries() {
        clearEntries()
        for (wallpaper in PCBoxWallpaperRepository.wallpapers) {
            addEntry(WallpaperEntry(wallpaper))
        }
    }

    override fun getRowLeft(): Int {
        return this.left + SLOT_PADDING
    }

    override fun getRowWidth(): Int {
        return SLOT_WIDTH
    }

    override fun getRowRight(): Int {
        return this.rowLeft + SLOT_WIDTH
    }

    inner class WallpaperEntry(val wallpaper: ResourceLocation) : Slot<WallpaperEntry>() {
        override fun render(
            guiGraphics: GuiGraphics,
            index: Int,
            top: Int,
            left: Int,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float
        ) {
            val matrices = guiGraphics.pose()
            blitk(
                matrixStack = matrices,
                texture = wallpaper,
                x = left,
                y = top,
                width = width,
                height = height,
            )

            if (hovering) {
                blitk(
                    matrixStack = matrices,
                    texture = ENTRY_HOVERED,
                    x = left,
                    y = top,
                    width = width,
                    height = height
                )
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (this@WallpapersScrollingWidget.visible && isMouseOver(mouseX, mouseY)) {
                RequestChangePCBoxWallpaperPacket(pcGui.pc.uuid, storageWidget.box, wallpaper).sendToServer()
                pcGui.pc.boxes[storageWidget.box].wallpaper = wallpaper
                minecraft.soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.PC_CLICK, 1.0F))
                return true
            }
            return false
        }

        override fun getNarration(): Component? {
            return wallpaper.toString().text()
        }
    }
}