/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.emi

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import dev.emi.emi.api.widget.Bounds
import dev.emi.emi.api.widget.Widget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

class CobblemonEmiSimpleWidget<T: RecipeViewerRecipe>(val provider: CobblemonEmiProvider<T>, val recipe: T): Widget() {
    override fun getBounds(): Bounds {
        val original = provider.originalProvider
        return Bounds(0, 0, original.getWidth(), original.getHeight())
    }

    override fun render(draw: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val realDelta = Minecraft.getInstance().timer.realtimeDeltaTicks
        recipe.renderRecipe( draw, mouseX, mouseY, realDelta)
    }
}