/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.trade

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import org.joml.Quaternionf
import org.joml.Vector3f

class ModelWidget(
    pX: Int, pY: Int,
    pWidth: Int, pHeight: Int,
    var pokemon: RenderablePokemon,
    var baseScale: Float = 2.7F,
    var rotationY: Float = 35F,
    var offsetY: Double = 0.0
): SoundlessWidget(pX, pY, pWidth, pHeight, Component.literal("Trade - ModelWidget")) {
    var state = FloatingState()
    private var rotVec = Vector3f(13F, rotationY, 0F)

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        drawProfilePokemon(
            renderablePokemon = pokemon,
            context = context,
            originX = x + width / 2F,
            originY = y + offsetY.toFloat(),
            rotation = Quaternionf().fromEulerXYZDegrees(rotVec),
            state = state,
            partialTicks = delta,
            scale = baseScale * 20F
        )
    }
}
