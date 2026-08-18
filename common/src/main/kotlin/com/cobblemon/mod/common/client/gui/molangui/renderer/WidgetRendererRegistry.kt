/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui.renderer

import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.bedrockk.molang.runtime.MoLangRuntime
import net.minecraft.client.gui.GuiGraphics

object WidgetRendererRegistry {
    private val renderers = mutableMapOf<WidgetType, WidgetRenderer>()

    fun register(type: WidgetType, renderer: WidgetRenderer) {
        renderers[type] = renderer
    }

    fun get(type: WidgetType): WidgetRenderer? = renderers[type]

    fun registerDefaults() {
        register(WidgetType.PANEL, PanelRenderer)
        register(WidgetType.LABEL, LabelRenderer)
        register(WidgetType.IMAGE, ImageRenderer)
        register(WidgetType.FILL, FillRenderer)
        register(WidgetType.BUTTON, ButtonRenderer)
        register(WidgetType.PROGRESS_BAR, ProgressBarRenderer)
        register(WidgetType.LIST, ListRenderer)
        register(WidgetType.NUMBER_INPUT, NumberInputRenderer)
        register(WidgetType.ITEM_SLOT, ItemSlotRenderer)
        register(WidgetType.SCROLL_PANEL, PanelRenderer)
        register(WidgetType.POKEMON_RENDER, PokemonRenderRenderer)
        register(WidgetType.SLOT_GRID, NoOpRenderer)
    }
}

private object NoOpRenderer : WidgetRenderer {
    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
    }
}
