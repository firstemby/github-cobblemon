/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderState
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.serialization.MapCodec
import java.util.function.Consumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.special.SpecialModelRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3fc

class PokemonItemSpecialModelRenderer : SpecialModelRenderer<RenderablePokemon> {
    private val poseState = FloatingState()
    private var lastFrameTimeNs = Long.MIN_VALUE

    override fun submit(
        renderablePokemon: RenderablePokemon?,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        packedLight: Int,
        packedOverlay: Int,
        hasFoil: Boolean,
        packedColor: Int
    ) {
        renderablePokemon ?: return
        val minecraft = Minecraft.getInstance()
        val renderState = createRenderState(minecraft, renderablePokemon, packedLight) ?: return
        val renderer = minecraft.entityRenderDispatcher.getRenderer(renderState) as? PokemonRenderer ?: return

        poseStack.pushPose()
        poseStack.mulPose(Quaternionf().rotateY(Math.PI.toFloat()))
        poseStack.scale(0.16F, 0.16F, 0.16F)
        renderer.submit(renderState, poseStack, submitNodeCollector, CameraRenderState())
        poseStack.popPose()
    }

    override fun getExtents(consumer: Consumer<Vector3fc>) {
        consumer.accept(Vector3f(-1.0F, -1.0F, -1.0F))
        consumer.accept(Vector3f(1.0F, 1.0F, 1.0F))
    }

    override fun extractArgument(stack: ItemStack): RenderablePokemon? {
        return (stack.item as? PokemonItem)?.asRenderablePokemon(stack)
    }

    private fun createRenderState(
        minecraft: Minecraft,
        renderablePokemon: RenderablePokemon,
        packedLight: Int
    ): PokemonRenderState? {
        val species = renderablePokemon.species.resourceIdentifier
        val pokemonSpecies = PokemonSpecies.getByIdentifier(species) ?: return null
        val currentFrameTimeNs = minecraft.frameTimeNs
        if (this.lastFrameTimeNs != currentFrameTimeNs) {
            this.lastFrameTimeNs = currentFrameTimeNs
            this.poseState.updatePartialTicks(minecraft.deltaTracker.realtimeDeltaTicks)
        }

        this.poseState.currentAspects = renderablePokemon.aspects

        return PokemonRenderState().apply {
            entityType = CobblemonEntities.POKEMON
            x = 0.0
            y = 0.0
            z = 0.0
            ageInTicks = this@PokemonItemSpecialModelRenderer.poseState.animationSeconds * 20.0F
            boundingBoxWidth = 1.0F
            boundingBoxHeight = 1.0F
            eyeHeight = 0.5F
            isInvisible = false
            isInvisibleToPlayer = false
            displayFireAnimation = false
            lightCoords = packedLight
            outlineColor = 0
            shadowRadius = 0.0F
            shadowPieces.clear()
            pokemonEntity = null
            guiPreviewEntity = null
            this.poseState = this@PokemonItemSpecialModelRenderer.poseState
            this.species = species
            aspects = renderablePokemon.aspects
            texture = VaryingModelRepository.getTexture(species, this@PokemonItemSpecialModelRenderer.poseState)
            baseScale = pokemonSpecies.getForm(renderablePokemon.aspects).baseScale
            modelScale = 1.0F
            shadowSize = 0.0F
            heldItem = renderablePokemon.heldItem.copy()
            bodyRot = 0.0F
            yRot = 0.0F
            xRot = 0.0F
            scale = 1.0F
        }
    }

    object Unbaked : SpecialModelRenderer.Unbaked<RenderablePokemon> {
        val MAP_CODEC: MapCodec<Unbaked> = MapCodec.unit(this)

        override fun bake(context: SpecialModelRenderer.BakingContext): SpecialModelRenderer<RenderablePokemon>? {
            return PokemonItemSpecialModelRenderer()
        }

        override fun type(): MapCodec<Unbaked> {
            return MAP_CODEC
        }
    }
}
