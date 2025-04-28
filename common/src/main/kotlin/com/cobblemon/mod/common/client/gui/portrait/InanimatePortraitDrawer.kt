/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.portrait

import com.cobblemon.mod.common.api.gui.drawPosablePortrait
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.GraphicsStatus

/**
 * The simplest implementation, the Pokemon is drawn staying still.
 *
 * Meant for client with [GraphicsStatus.FAST].
 */
class InanimatePortraitDrawer : PortraitDrawer {

    private val state = FloatingState()

    override fun draw(pokemon: Pokemon, poseStack: PoseStack, partialTicks: Float, isSelected: Boolean, index: Int) {
        state.currentAspects = pokemon.aspects
        drawPosablePortrait(
            identifier = pokemon.species.resourceIdentifier,
            matrixStack = poseStack,
            partialTicks = 0F,
            contextScale = pokemon.form.baseScale,
            state = this.state
        )
    }

}