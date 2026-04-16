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
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class WallpapersScrollingWidget(
    pX: Int, val pY: Int,
    val pcGui: PCGUI,
    val storageWidget: StorageWidget
) : ScrollingWidget<WallpaperEntry>(
    width = WIDTH,
    height = HEIGHT,
    left = pX,
    top = 0,
    slotHeight = SLOT_HEIGHT + SLOT_PADDING
) {
    companion object {
        const val WIDTH = 68
        const val HEIGHT = 146
        const val SLOT_WIDTH = 56
        const val SLOT_HEIGHT = 50
        const val SLOT_PADDING = 4

        val previewOverlayResource = cobblemonResource("textures/gui/pc/pc_screen_overlay_preview.png")
        val previewOverlayNewResource = cobblemonResource("textures/gui/pc/pc_screen_overlay_preview_new.png")
        val backgroundResource = cobblemonResource("textures/gui/pc/wallpaper_scroll_background.png")
    }

    init {
        this.visible = false
        this.y = pY
        createEntries()
    }

    override fun scrollBarX(): Int = x + width - 3

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        blitk(
            context = context,
            texture = backgroundResource,
            x = x,
            y = y - 1,
            width = width,
            height = height + 2
        )

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pc.wallpaper").bold(),
            x = x + 23,
            y = y - 18,
            centered = true,
            shadow = true
        )

        context.enableScissor(x, y, x + width, y + height)
        super.extractWidgetRenderState(context, mouseX, mouseY, delta)
        context.disableScissor()
    }

    private fun createEntries() {
        clearEntries()
        for (wallpaper in PCBoxWallpaperRepository.availableWallpapers) {
            val altWallpaper = PCBoxWallpaperRepository.allWallpapers.find { it.first == wallpaper }?.second
            addEntry(WallpaperEntry(wallpaper, altWallpaper, pcGui.unseenWallpapers.contains(wallpaper)))
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

    inner class WallpaperEntry(val wallpaper: Identifier, var altWallpaper: Identifier?, var isNew: Boolean) : Slot<WallpaperEntry>() {
        override fun extractContent(
            guiGraphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float
        ) {
            val resource = if (Minecraft.getInstance().hasShiftDown()) altWallpaper ?: wallpaper else wallpaper
            val previewLeft = left + 1
            val previewTop = y + SLOT_PADDING + 1
            val previewRight = left + width - 1
            val previewBottom = y + SLOT_PADDING + SLOT_HEIGHT - 1

            guiGraphics.enableScissor(previewLeft, previewTop, previewRight, previewBottom)
            blitk(
                context = guiGraphics,
                texture = resource,
                x = previewLeft,
                y = previewTop,
                width = width - 2,
                height = SLOT_HEIGHT - 2,
            )
            guiGraphics.disableScissor()

            blitk(
                context = guiGraphics,
                texture = if (isNew) previewOverlayNewResource else previewOverlayResource,
                x = left,
                y = y + SLOT_PADDING,
                width = width,
                height = SLOT_HEIGHT,
                vOffset = if (hovering) SLOT_HEIGHT else 0,
                textureHeight = SLOT_HEIGHT * 2
            )
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (this@WallpapersScrollingWidget.visible && isMouseOver(event.x, event.y)) {
                val showAlt = Minecraft.getInstance().hasShiftDown()
                val appliedWallpaper = if (showAlt) altWallpaper ?: wallpaper else wallpaper
                RequestChangePCBoxWallpaperPacket(pcGui.pc.uuid, storageWidget.box, wallpaper, if (showAlt) altWallpaper else null).sendToServer()
                pcGui.pc.boxes[storageWidget.box].wallpaper = appliedWallpaper
                pcGui.unseenWallpapers.remove(wallpaper)
                isNew = false
                minecraft.soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.PC_CLICK, 1.0F))
                return true
            }
            return false
        }

        override fun getNarration(): Component {
            return wallpaper.toString().text()
        }
    }
}
