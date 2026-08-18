/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui.renderer

import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.bedrockk.molang.runtime.MoLangRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object ItemSlotRenderer : WidgetRenderer {
    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
        val itemId = widget.definition.getProperty("item")?.resolveString(runtime) ?: return
        val resourceLocation = ResourceLocation.tryParse(itemId) ?: return
        val item = BuiltInRegistries.ITEM.get(resourceLocation)
        val stack = ItemStack(item)

        val x = widget.absoluteX.toInt()
        val y = widget.absoluteY.toInt()

        guiGraphics.renderItem(stack, x, y)
    }
}
