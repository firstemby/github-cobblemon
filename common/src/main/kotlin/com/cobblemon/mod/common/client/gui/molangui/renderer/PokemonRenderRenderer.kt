/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui.renderer

import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
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

object PokemonRenderRenderer : WidgetRenderer {

    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
        val speciesProp = widget.definition.getProperty("species")
        if (speciesProp == null) return
        val speciesName = speciesProp.resolveString(runtime)
        if (speciesName.isEmpty() || speciesName == "0" || speciesName == "0.0") return
        val species = PokemonSpecies.getByName(speciesName)
        if (species == null) return
        val aspectsStr = widget.definition.getProperty("aspects")?.resolveString(runtime) ?: ""
        val aspects = if (aspectsStr.isNotEmpty()) aspectsStr.split(",").map { it.trim() }.toSet() else emptySet()
        val scale = widget.definition.getProperty("scale")?.resolveFloat(runtime) ?: 2.5f
        val rotY = widget.definition.getProperty("rotation_y")?.resolveFloat(runtime) ?: 35f
        val rotX = widget.definition.getProperty("rotation_x")?.resolveFloat(runtime) ?: 13f

        val renderablePokemon = RenderablePokemon(species, aspects)

        val state = (widget.rendererState as? FloatingState) ?: FloatingState().also { widget.rendererState = it }
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
}
