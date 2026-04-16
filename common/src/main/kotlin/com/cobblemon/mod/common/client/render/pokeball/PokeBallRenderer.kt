/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokeball

import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.client.entity.EmptyPokeBallClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.pose.ModelPartTransformation
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth

class PokeBallRenderState : EntityRenderState() {
    var entity: EmptyPokeBallEntity? = null
    var poseState: EmptyPokeBallClientDelegate? = null
    var ballName: Identifier? = null
    var aspects: Set<String> = emptySet()
    var texture: Identifier = Identifier.parse("minecraft:textures/misc/white.png")
    var yaw: Float = 0F
    var partialTicks: Float = 0F
}

class PokeBallRenderer(context: EntityRendererProvider.Context) : EntityRenderer<EmptyPokeBallEntity, PokeBallRenderState>(context) {
    override fun createRenderState() = PokeBallRenderState()

    override fun extractRenderState(entity: EmptyPokeBallEntity, renderState: PokeBallRenderState, partialTick: Float) {
        super.extractRenderState(entity, renderState, partialTick)

        val state = entity.delegate as? EmptyPokeBallClientDelegate ?: return
        renderState.entityType = CobblemonEntities.EMPTY_POKEBALL
        renderState.entity = entity
        renderState.poseState = state
        renderState.ballName = entity.pokeBall.name
        renderState.aspects = entity.aspects
        renderState.texture = VaryingModelRepository.getTexture(entity.pokeBall.name, state)
        renderState.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.yRot)
        renderState.partialTicks = partialTick
        renderState.shadowRadius = 0F
    }

    override fun submit(
        renderState: PokeBallRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        val entity = renderState.entity ?: return
        val state = renderState.poseState ?: return
        val ballName = renderState.ballName ?: return
        val model = VaryingModelRepository.getPoser(ballName, state)
        val renderContext = RenderContext().also {
            it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
            it.put(RenderContext.ENTITY, entity)
            it.put(RenderContext.POSABLE_STATE, state)
            it.put(RenderContext.ASPECTS, renderState.aspects)
        }

        state.currentAspects = renderState.aspects
        state.updatePartialTicks(renderState.partialTicks)
        model.context = renderContext
        model.applyAnimations(entity, state, 0F, 0F, renderState.ageInTicks, 0F, 0F)
        val partSnapshot = model.relevantPartsByName.values.distinct().map(ModelPartTransformation::derive)

        poseStack.pushPose()
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.yaw))
        poseStack.scale(0.7F, -0.7F, -0.7F)
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(renderState.texture)) { pose, consumer ->
            val renderStack = PoseStack()
            renderStack.last().set(pose)
            partSnapshot.forEach(ModelPartTransformation::set)
            model.render(renderContext, renderStack, consumer, renderState.lightCoords, OverlayTexture.NO_OVERLAY, -0x1)
            model.setDefault()
            model.resetLayerContext()
        }
        poseStack.popPose()
    }
}
