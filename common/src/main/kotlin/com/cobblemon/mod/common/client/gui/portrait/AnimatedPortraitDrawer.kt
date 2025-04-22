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
import java.util.UUID

/**
 * Base logic for a [PortraitDrawer] that animates a portrait based on conditions.
 */
abstract class AnimatedPortraitDrawer : PortraitDrawer {

    private val state = FloatingState()

    private val stateAtIndex = hashMapOf<Int, Pair<UUID, FloatingState>>()

    override fun draw(pokemon: Pokemon, poseStack: PoseStack, partialTicks: Float, isSelected: Boolean, index: Int) {
        drawPosablePortrait(
            identifier = pokemon.species.resourceIdentifier,
            matrixStack = poseStack,
            partialTicks = this.ticksFor(pokemon, isSelected, partialTicks),
            contextScale = pokemon.form.baseScale,
            state = this.stateFor(pokemon, isSelected, index)
        )
    }

    protected abstract fun shouldAnimate(pokemon: Pokemon, isSelected: Boolean): Boolean

    protected open fun ticksFor(pokemon: Pokemon, isSelected: Boolean, partialTicks: Float): Float {
        return if (this.shouldAnimate(pokemon, isSelected)) partialTicks else 0F
    }

    protected open fun stateFor(pokemon: Pokemon, isSelected: Boolean, index: Int): FloatingState {
        if (!this.shouldAnimate(pokemon, isSelected)) {
            return this.state
        }
        val state = this.stateAtIndex.getOrPut(index) { pokemon.uuid to this.createState(pokemon) }
        if (state.first != pokemon.uuid) {
            val newState = this.createState(pokemon)
            this.stateAtIndex[index] = pokemon.uuid to newState
            return newState
        }
        return state.second
    }

    protected open fun createState(pokemon: Pokemon): FloatingState {
        val state = FloatingState()
        state.currentAspects = pokemon.aspects
        return state
    }



}