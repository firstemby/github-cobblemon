/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCALE
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SIZE
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.gui.GuiGraphics
import org.joml.Quaternionf
import org.joml.Vector3f

abstract class PokemonSlotWidget {
    companion object {
        private val slotResource = cobblemonResource("textures/gui/pokedex/pokedex_slot.png")
        private val slotHighlight = cobblemonResource("textures/gui/pokedex/slot_select.png")
        private val caughtIcon = cobblemonResource("textures/gui/pokedex/caught_icon_small.png")
        private val unknownIcon = cobblemonResource("textures/gui/pokedex/pokedex_slot_unknown.png")
        private val unimplementedIcon = cobblemonResource("textures/gui/pokedex/pokedex_slot_unimplemented.png")

        fun render(
            context: GuiGraphics,
            x: Int,
            y: Int,
            isHovered: Boolean,
            species: Species,
            renderablePokemon: RenderablePokemon?,
            discoveryLevel: PokedexEntryProgress,
        ) {
            val shouldDrawMon = renderablePokemon != null

            // FIXME: This may not work properly when accounting for custom pokemon with the same dex number
            val pokemonNumber = species.nationalPokedexNumber.toString().padStart(4, '0')
            val speciesNumber = pokemonNumber.text()

            val matrices = context.pose()

            blitk(
                matrixStack = matrices,
                texture = slotResource,
                x = x,
                y = y,
                width = SCROLL_SLOT_SIZE,
                height = SCROLL_SLOT_SIZE,
            )

            if (isHovered) {
                blitk(
                    matrixStack = matrices,
                    texture = slotHighlight,
                    x = x,
                    y = y,
                    width = SCROLL_SLOT_SIZE,
                    height = SCROLL_SLOT_SIZE,
                    vOffset = SCROLL_SLOT_SIZE,
                    textureHeight = SCROLL_SLOT_SIZE * 2,
                )
            }

            if (shouldDrawMon && discoveryLevel != PokedexEntryProgress.UNREGISTERED) {
                context.enableScissor(
                    x + 1,
                    y + 1,
                    x + SCROLL_SLOT_SIZE - 1,
                    y + SCROLL_SLOT_SIZE - 2,
                )

                matrices.pushPose()
                matrices.translate(x + SCROLL_SLOT_SIZE / 2.0, y + 1.0, 0.0)
                matrices.scale(2.5F, 2.5F, 1F)

                val state = FloatingState()
                state.currentAspects = renderablePokemon.aspects

                drawProfilePokemon(
                    renderablePokemon = renderablePokemon,
                    matrixStack = matrices,
                    rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                    state = state,
                    partialTicks = 0F,
                    scale = 4.5F
                )

                matrices.popPose()
                context.disableScissor()
            } else {
                blitk(
                    matrixStack = matrices,
                    texture = unknownIcon,
                    x = x + 8.5,
                    y = y + 9,
                    width = 8,
                    height = 10
                )

                if (!species.implemented) {
                    blitk(
                        matrixStack = matrices,
                        texture = unimplementedIcon,
                        x = (x + 14) / SCALE,
                        y = (y + 15.5) / SCALE,
                        width = 7,
                        height = 7,
                        scale = SCALE
                    )
                }
            }

            // Ensure elements are not hidden behind Pokémon render
            matrices.pushPose()
            matrices.translate(0.0, 0.0, 100.0)

            drawScaledText(
                context = context,
                text = speciesNumber,
                x = x + 1.5,
                y = y + 2.5,
                shadow = true,
                scale = SCALE
            )

            if (discoveryLevel == PokedexEntryProgress.OWNED) {
                blitk(
                    matrixStack = matrices,
                    texture = caughtIcon,
                    x = (x + 18) / SCALE,
                    y = (y + 1.5) / SCALE,
                    width = 11,
                    height = 11,
                    scale = SCALE
                )
            }

            matrices.popPose()
        }
    }
}