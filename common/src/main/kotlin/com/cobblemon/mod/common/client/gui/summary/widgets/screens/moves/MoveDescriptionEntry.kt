/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves

import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class MoveDescriptionEntry(
    var line : MutableComponent
) :
    ObjectSelectionList.Entry<MoveDescriptionEntry>() {

    override fun extractContent(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, isHovered: Boolean, partialTicks: Float) {
        drawScaledText(
            context = context,
            text = line,
            x = x + 82,
            y = y,
            scale = 0.5F,
            shadow = true
        )
    }

    override fun getNarration(): Component {
        return line
    }
}
