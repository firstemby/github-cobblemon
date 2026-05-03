/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import me.shedaniel.rei.api.client.gui.widgets.Widget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.events.GuiEventListener

class CobblemonReiSimpleWidget<T: RecipeViewerRecipe>(val x: Int, val y: Int, val recipe: T): Widget() {
    override fun children(): List<GuiEventListener> {
        return emptyList()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val realDelta = Minecraft.getInstance().timer.realtimeDeltaTicks
        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        recipe.renderRecipe(guiGraphics, mouseX, mouseY, realDelta)
        matrices.popPose()
    }
}