/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui.component

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.components.AbstractWidget
import org.joml.Vector4i

interface ControllerScreen {
    companion object {
        private val selectionTexture = cobblemonResource("textures/gui/common/selection_box.png")

        const val BASE_SIZE = 3
        const val SHADOW_OFFSET = 1
        const val TOTAL_SIZE = BASE_SIZE + SHADOW_OFFSET
    }

    fun getComponentManager(): ControllerComponentManager

    fun getSelectionPosAndSize(): Vector4i? {
        val manager = getComponentManager()
        val focusedWidget = manager.screen.focused as? AbstractWidget ?: return null

        return Vector4i(focusedWidget.x - 2, focusedWidget.y - 2, focusedWidget.width + 4, focusedWidget.height + 4)
    }

    fun renderSelection(matrices: PoseStack) {
        if (!getComponentManager().active) return
        val positioningData = getSelectionPosAndSize() ?: return

        val posX = positioningData.x
        val posY = positioningData.y

        val width = positioningData.z
        val height = positioningData.w

        blitk( //NW
            matrixStack = matrices,
            texture = selectionTexture,
            x = posX,
            y = posY,
            width = TOTAL_SIZE,
            height = TOTAL_SIZE,
            uOffset = 0,
            vOffset = 0,
            textureWidth = TOTAL_SIZE * 2,
            textureHeight = TOTAL_SIZE * 2
        )

        blitk( //NE
            matrixStack = matrices,
            texture = selectionTexture,
            x = posX + width - BASE_SIZE,
            y = posY,
            width = TOTAL_SIZE,
            height = TOTAL_SIZE,
            uOffset = 4,
            vOffset = 0,
            textureWidth = TOTAL_SIZE * 2,
            textureHeight = TOTAL_SIZE * 2
        )

        blitk( //SW
            matrixStack = matrices,
            texture = selectionTexture,
            x = posX,
            y = posY + height - BASE_SIZE,
            width = TOTAL_SIZE,
            height = TOTAL_SIZE,
            uOffset = 0,
            vOffset = 4,
            textureWidth = TOTAL_SIZE * 2,
            textureHeight = TOTAL_SIZE * 2
        )

        blitk( //SE
            matrixStack = matrices,
            texture = selectionTexture,
            x = posX + width - BASE_SIZE,
            y = posY + height - BASE_SIZE,
            width = TOTAL_SIZE,
            height = TOTAL_SIZE,
            uOffset = 4,
            vOffset = 4,
            textureWidth = TOTAL_SIZE * 2,
            textureHeight = TOTAL_SIZE * 2
        )
    }
}