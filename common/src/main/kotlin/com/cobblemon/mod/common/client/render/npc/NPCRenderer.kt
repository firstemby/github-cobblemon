/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.npc

import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.PosableRenderStateBridge
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.npc.PosableNPCModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import kotlin.math.min

class NPCRenderState : LivingEntityRenderState(), PosableRenderStateBridge {
    var entity: NPCEntity? = null
    var poseState: NPCClientDelegate? = null
    var resourceIdentifier: Identifier? = null
    var aspects: Set<String> = emptySet()
    var texture: Identifier = Identifier.parse("minecraft:textures/misc/white.png")
    var renderScale: Float = 1F
    var modelScale: Float = 1F
    var shadowSize: Float = 0.5F

    override val renderEntity: NPCEntity?
        get() = entity

    override val renderPosableState: PosableState?
        get() = poseState
}

class NPCRenderer(context: Context) : LivingEntityRenderer<NPCEntity, NPCRenderState, PosableNPCModel>(context, PosableNPCModel(), 0.5f) {
    private val heldItemRenderer = HeldItemRenderer()

    override fun createRenderState() = NPCRenderState()

    override fun extractRenderState(entity: NPCEntity, reusedState: NPCRenderState, partialTick: Float) {
        super.extractRenderState(entity, reusedState, partialTick)

        val clientDelegate = entity.delegate as? NPCClientDelegate ?: return
        val aspects = entity.aspects
        reusedState.entity = entity
        reusedState.poseState = clientDelegate
        reusedState.resourceIdentifier = entity.resourceIdentifier
        reusedState.aspects = aspects
        reusedState.texture = VaryingModelRepository.getTexture(entity.resourceIdentifier, clientDelegate)
        reusedState.renderScale = entity.renderScale
        reusedState.modelScale = entity.npc.modelScale
        reusedState.shadowSize =
            min(
                entity.boundingBox.maxX - entity.boundingBox.minX,
                entity.boundingBox.maxZ - entity.boundingBox.minZ
            ).toFloat() / 1.5F
        reusedState.ageInTicks = clientDelegate.animationSeconds * 20F

        clientDelegate.currentAspects = aspects
        clientDelegate.updatePartialTicks(partialTick)
    }

    override fun getTextureLocation(state: NPCRenderState): Identifier {
        return state.texture
    }

    override fun getShadowRadius(state: NPCRenderState): Float {
        return state.shadowSize
    }

    override fun shouldShowName(entity: NPCEntity, distanceToCameraSq: Double): Boolean {
        if (entity.hideNameTag) {
            return false
        }
        return super.shouldShowName(entity, distanceToCameraSq)
    }

    override fun submit(state: NPCRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, cameraRenderState: CameraRenderState) {
        val entity = state.entity ?: return
        if (entity.isInvisible) {
            return
        }

        val poseState = state.poseState ?: return
        val resourceIdentifier = state.resourceIdentifier ?: return
        val modelNow = VaryingModelRepository.getPoser(resourceIdentifier, poseState)
        val renderContext = RenderContext().also {
            it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
            it.put(RenderContext.ENTITY, entity)
            it.put(RenderContext.POSABLE_STATE, poseState)
            it.put(RenderContext.ASPECTS, state.aspects)
            it.put(RenderContext.TEXTURE, state.texture)
        }
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()

        poseState.currentModel = modelNow
        poseState.currentAspects = state.aspects
        modelNow.context = renderContext
        modelNow.applyAnimations(
            entity,
            poseState,
            state.walkAnimationPos,
            state.walkAnimationSpeed,
            state.ageInTicks,
            state.yRot,
            state.xRot
        )

        poseStack.pushPose()
        if (state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) {
            state.bedOrientation?.let { bedOrientation ->
                val eyeHeight = state.eyeHeight - 0.1F
                poseStack.translate(
                    -bedOrientation.stepX.toFloat() * eyeHeight,
                    0F,
                    -bedOrientation.stepZ.toFloat() * eyeHeight
                )
            }
        }

        poseStack.scale(state.scale, state.scale, state.scale)
        setupRotations(state, poseStack, state.bodyRot, state.scale)
        poseStack.scale(-1F, -1F, 1F)
        poseStack.scale(state.renderScale * state.modelScale, state.renderScale * state.modelScale, state.renderScale * state.modelScale)
        poseStack.translate(0F, -1.501F, 0F)
        poseStack.translate(0F, 1.5F, 0F)

        modelNow.withLayerContext(bufferSource, poseState, VaryingModelRepository.getLayers(resourceIdentifier, poseState)) {
            val buffer = bufferSource.getBuffer(RenderTypes.entityCutout(state.texture))
            modelNow.render(
                renderContext,
                poseStack,
                buffer,
                state.lightCoords,
                OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(state.hasRedOverlay)),
                -0x1
            )
        }

        if (state.deathTime < 1F) {
            heldItemRenderer.renderOnModel(
                entity.mainHandItem,
                poseState,
                poseStack,
                collector,
                state.lightCoords,
                state.outlineColor,
                false,
                entity
            )
        }

        modelNow.setDefault()
        modelNow.red = 1F
        modelNow.green = 1F
        modelNow.blue = 1F
        modelNow.resetLayerContext()
        poseStack.popPose()
        bufferSource.endBatch()
        super.submit(state, poseStack, collector, cameraRenderState)
    }
}
