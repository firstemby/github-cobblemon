/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted.renderer

import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetInstance
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import com.bedrockk.molang.runtime.MoLangRuntime
import net.minecraft.client.gui.GuiGraphics
import org.joml.Quaternionf
import org.joml.Vector3f
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders a 3D Pokémon model in a scripted GUI.
 *
 * Properties:
 * - `species` — Pokémon species name (e.g., "bulbasaur", "pikachu")
 * - `aspects` — comma-separated aspect list (e.g., "shiny,female") (optional)
 * - `scale` — render scale (default 2.5)
 * - `rotation_y` — Y-axis rotation in degrees (default 35)
 * - `rotation_x` — X-axis rotation in degrees (default 13)
 */
object PokemonRenderRenderer : WidgetRenderer {

    private val stateCache = ConcurrentHashMap<String, FloatingState>()

    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
        val speciesProp = widget.definition.getProperty("species")
        if (speciesProp == null) {
            com.cobblemon.mod.common.Cobblemon.LOGGER.info("[PokemonRenderRenderer] No 'species' property on widget '${widget.definition.id}'")
            return
        }
        val speciesName = speciesProp.resolveString(runtime)
        com.cobblemon.mod.common.Cobblemon.LOGGER.info("[PokemonRenderRenderer] widget='${widget.definition.id}' speciesName='$speciesName' visible=${widget.visible}")
        if (speciesName.isEmpty() || speciesName == "0" || speciesName == "0.0") return
        val species = PokemonSpecies.getByName(speciesName)
        if (species == null) {
            com.cobblemon.mod.common.Cobblemon.LOGGER.info("[PokemonRenderRenderer] Species not found: '$speciesName'")
            return
        }
        val aspectsStr = widget.definition.getProperty("aspects")?.resolveString(runtime) ?: ""
        val aspects = if (aspectsStr.isNotEmpty()) aspectsStr.split(",").map { it.trim() }.toSet() else emptySet()
        val scale = widget.definition.getProperty("scale")?.resolveFloat(runtime) ?: 2.5f
        val rotY = widget.definition.getProperty("rotation_y")?.resolveFloat(runtime) ?: 35f
        val rotX = widget.definition.getProperty("rotation_x")?.resolveFloat(runtime) ?: 13f

        val renderablePokemon = RenderablePokemon(species, aspects)

        // Get or create animation state keyed by widget ID
        val state = stateCache.getOrPut(widget.definition.id) { FloatingState() }
        state.updatePartialTicks(delta)

        val x = widget.absoluteX
        val y = widget.absoluteY
        val w = widget.computedWidth
        val h = widget.computedHeight

        val matrices = guiGraphics.pose()
        matrices.pushPose()

        guiGraphics.enableScissor(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt())

        matrices.translate((x + w * 0.5).toDouble(), y.toDouble(), 0.0)
        matrices.scale(scale, scale, scale)
        matrices.pushPose()

        drawProfilePokemon(
            renderablePokemon = renderablePokemon,
            matrixStack = matrices,
            rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(rotX, rotY, 0f)),
            state = state,
            partialTicks = delta
        )

        matrices.popPose()
        matrices.popPose()
        guiGraphics.disableScissor()
    }

    fun clearCache() {
        stateCache.clear()
    }
}
