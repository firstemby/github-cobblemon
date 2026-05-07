/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.entity

import com.cobblemon.mod.common.api.fishing.PokeRods
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class PokeBobberRenderState : EntityRenderState() {
    var lineOriginOffset: Vec3 = Vec3.ZERO
    var lineColor = -0x1000000
    var randomPitch = 0F
    var randomYaw = 0F
    var spinAngle = 0F
    var baitOffsetX = 0.0
    var baitOffsetY = 0.0
    val ballItemState = ItemStackRenderState()
    val baitItemState = ItemStackRenderState()
}

class PokeBobberEntityRenderer(context: EntityRendererProvider.Context) : EntityRenderer<PokeRodFishingBobberEntity, PokeBobberRenderState>(context) {
    companion object {
        private val TEXTURE = cobblemonResource("textures/item/fishing/bobber_hook.png")
        private val LAYER = RenderTypes.entityCutout(TEXTURE)
    }

    private val itemModelResolver: ItemModelResolver = context.itemModelResolver

    override fun createRenderState() = PokeBobberRenderState()

    override fun shouldRender(entity: PokeRodFishingBobberEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean {
        return super.shouldRender(entity, frustum, x, y, z)
    }

    override fun affectedByCulling(entity: PokeRodFishingBobberEntity) = false

    override fun extractRenderState(entity: PokeRodFishingBobberEntity, renderState: PokeBobberRenderState, partialTick: Float) {
        super.extractRenderState(entity, renderState, partialTick)

        renderState.ballItemState.clear()
        renderState.baitItemState.clear()

        if (entity.tickCount <= 1) {
            entity.randomYaw = (-180 + Math.random() * 360).toFloat()
            entity.randomPitch = (-40 + Math.random() * 80).toFloat()
        }

        if (entity.state == PokeRodFishingBobberEntity.State.BOBBING && !entity.onGround()) {
            entity.randomPitch = Mth.lerp(0.04F, entity.randomPitch, 0F)
        }

        if (!entity.onGround()) {
            val stopRotationAge = 220
            val ageFactor = if (entity.tickCount < stopRotationAge) {
                if (entity.inOpenWater) {
                    1 + entity.tickCount / 15.0
                } else {
                    1 + entity.tickCount / 100.0
                }
            } else {
                Double.POSITIVE_INFINITY
            }
            val adjustedAgeFactor = if (ageFactor > 0) ageFactor else 1.0
            if (entity.tickCount < stopRotationAge) {
                entity.lastSpinAngle = (((entity.tickCount + partialTick) * 20 / adjustedAgeFactor) % 360).toFloat()
            }
        }

        renderState.randomPitch = entity.randomPitch
        renderState.randomYaw = entity.randomYaw
        renderState.spinAngle = entity.lastSpinAngle

        val player = entity.playerOwner
        renderState.lineOriginOffset = Vec3.ZERO
        if (player != null) {
            val rodId = entity.pokeRodId ?: Identifier.tryParse(entity.entityData.get(PokeRodFishingBobberEntity.POKEROD_ID))
            val armOffset = if (rodId != null) getArmOffset(player, rodId) else if (player.mainArm == HumanoidArm.RIGHT) 1 else -1
            val handSwingProgress = player.getAttackAnim(partialTick)
            val swingAngle = Mth.sin(Mth.sqrt(handSwingProgress) * Math.PI)
            val lineOrigin = getPlayerHandPos(player, armOffset, swingAngle, partialTick)
            val bobberPos = entity.getPosition(partialTick).add(0.0, 0.25, 0.0)
            renderState.lineOriginOffset = lineOrigin.subtract(bobberPos)
        }

        val rodId = entity.pokeRodId ?: Identifier.tryParse(entity.entityData.get(PokeRodFishingBobberEntity.POKEROD_ID))
        val rod = rodId?.let(PokeRods::getPokeRod)
        val pokeBall = rod?.let { PokeBalls.getPokeBall(it.pokeBallId) } ?: PokeBalls.POKE_BALL
        val ballStack = pokeBall.item.defaultInstance.copy()
        ballStack.set(DataComponents.ITEM_MODEL, pokeBall.model3d.withPath { path -> path.removePrefix("item/") })
        itemModelResolver.updateForTopItem(renderState.ballItemState, ballStack, ItemDisplayContext.GROUND, entity.level(), null, 0)

        val baitStack = entity.entityData.get(PokeRodFishingBobberEntity.POKEBOBBER_BAIT).copy()
        val berryAdjust = adjustBerry(baitStack)
        renderState.baitOffsetX = berryAdjust.first
        renderState.baitOffsetY = berryAdjust.second
        if (!baitStack.isEmpty) {
            itemModelResolver.updateForTopItem(renderState.baitItemState, baitStack, ItemDisplayContext.GROUND, entity.level(), null, 1)
        }

        renderState.lineColor = try {
            Color.decode(rod?.lineColor ?: "000000").rgb
        } catch (_: NumberFormatException) {
            -0x1000000
        }
    }

    override fun submit(
        renderState: PokeBobberRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        poseStack.pushPose()
        poseStack.pushPose()
        poseStack.mulPose(Axis.XN.rotationDegrees(renderState.randomPitch))
        poseStack.mulPose(Axis.YN.rotationDegrees(renderState.randomYaw))
        poseStack.mulPose(Axis.YN.rotationDegrees(renderState.spinAngle))
        poseStack.scale(0.7F, 0.7F, 0.7F)

        poseStack.pushPose()
        poseStack.scale(0.5F, 0.5F, 0.5F)
        poseStack.mulPose(cameraRenderState.orientation)
        submitNodeCollector.submitCustomGeometry(poseStack, LAYER) { pose, consumer ->
            renderHook(consumer, pose, renderState.lightCoords)
        }
        poseStack.popPose()

        if (!renderState.ballItemState.isEmpty) {
            renderState.ballItemState.submit(
                poseStack,
                submitNodeCollector,
                renderState.lightCoords,
                OverlayTexture.NO_OVERLAY,
                0
            )
        }

        if (!renderState.baitItemState.isEmpty) {
            poseStack.pushPose()
            poseStack.scale(0.8F, 0.8F, 0.8F)
            poseStack.translate(0.20 + renderState.baitOffsetX, -0.5 + renderState.baitOffsetY, 0.0)
            renderState.baitItemState.submit(
                poseStack,
                submitNodeCollector,
                renderState.lightCoords,
                OverlayTexture.NO_OVERLAY,
                0
            )
            poseStack.popPose()
        }

        poseStack.popPose()

        val deltaX = renderState.lineOriginOffset.x.toFloat()
        val deltaY = renderState.lineOriginOffset.y.toFloat()
        val deltaZ = renderState.lineOriginOffset.z.toFloat()
        val lineWidth = Minecraft.getInstance().window.getAppropriateLineWidth()
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines()) { pose, consumer ->
            for (segment in 0 until 16) {
                val start = percentage(segment, 16)
                val end = percentage(segment + 1, 16)
                renderFishingLine(renderState.lineColor, deltaX, deltaY, deltaZ, consumer, pose, start, end, lineWidth)
                renderFishingLine(renderState.lineColor, deltaX, deltaY, deltaZ, consumer, pose, end, start, lineWidth)
            }
        }

        poseStack.popPose()
        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState)
    }

    private fun getArmOffset(player: net.minecraft.world.entity.player.Player, rodId: Identifier): Int {
        var armOffset = if (player.mainArm == HumanoidArm.RIGHT) 1 else -1
        val heldItem = player.mainHandItem.item
        if (heldItem !is PokerodItem || heldItem.pokeRodId != rodId) {
            armOffset = -armOffset
        }
        return armOffset
    }

    private fun getPlayerHandPos(player: net.minecraft.world.entity.player.Player, armOffset: Int, swingAngle: Float, partialTick: Float): Vec3 {
        return if (entityRenderDispatcher.options.cameraType.isFirstPerson && player == Minecraft.getInstance().player) {
            val fov = entityRenderDispatcher.options.fov().get().toInt()
            val viewBobbingScale = 960.0 / fov.toDouble()
            val camera = entityRenderDispatcher.camera ?: return player.getEyePosition(partialTick)
            camera.getNearPlane(fov.toFloat())
                .getPointOnPlane(armOffset.toFloat() * 0.525F, -0.1F)
                .scale(viewBobbingScale)
                .yRot(swingAngle * 0.5F)
                .xRot(-swingAngle * 0.7F)
                .add(player.getEyePosition(partialTick))
        } else {
            val bodyYawRadians = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * (Math.PI / 180.0)
            val sinBodyYaw = Mth.sin(bodyYawRadians).toDouble()
            val cosBodyYaw = Mth.cos(bodyYawRadians).toDouble()
            val horizontalOffset = armOffset * 0.35
            val crouchOffset = if (player.isCrouching) -0.1875 else 0.0
            player.getEyePosition(partialTick).add(
                -cosBodyYaw * horizontalOffset - sinBodyYaw * 0.8,
                crouchOffset - 0.45,
                -sinBodyYaw * horizontalOffset + cosBodyYaw * 0.8
            )
        }
    }

    private fun adjustBerry(berryBait: ItemStack): Pair<Double, Double> {
        return when (BuiltInRegistries.ITEM.getKey(berryBait.item).toString()) {
            "wacan_berry" -> Pair(0.03, 0.0)
            "cheri_berry" -> Pair(0.03, 0.0)
            else -> Pair(0.0, 0.0)
        }
    }

    private fun renderHook(buffer: VertexConsumer, pose: PoseStack.Pose, light: Int) {
        val scale = 0.75F
        val shiftHook = 0.045F

        vertex(buffer, pose, light, (1.0F - 0.0F) * scale + 0.125F + shiftHook, 0.0F * scale + 0.125F, 0, 1)
        vertex(buffer, pose, light, (1.0F - 1.0F) * scale + 0.125F + shiftHook, 0.0F * scale + 0.125F, 1, 1)
        vertex(buffer, pose, light, (1.0F - 1.0F) * scale + 0.125F + shiftHook, 1.0F * scale + 0.125F, 1, 0)
        vertex(buffer, pose, light, (1.0F - 0.0F) * scale + 0.125F + shiftHook, 1.0F * scale + 0.125F, 0, 0)

        vertex(buffer, pose, light, (1.0F - 0.0F) * scale + 0.125F + shiftHook, 1.0F * scale + 0.125F, 0, 0)
        vertex(buffer, pose, light, (1.0F - 1.0F) * scale + 0.125F + shiftHook, 1.0F * scale + 0.125F, 1, 0)
        vertex(buffer, pose, light, (1.0F - 1.0F) * scale + 0.125F + shiftHook, 0.0F * scale + 0.125F, 1, 1)
        vertex(buffer, pose, light, (1.0F - 0.0F) * scale + 0.125F + shiftHook, 0.0F * scale + 0.125F, 0, 1)
    }

    private fun vertex(buffer: VertexConsumer, pose: PoseStack.Pose, light: Int, x: Float, y: Float, u: Int, v: Int) {
        buffer
            .addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
            .setColor(-1)
            .setUv(u.toFloat(), v.toFloat())
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0F, 1.0F, 0.0F)
    }

    private fun renderFishingLine(
        color: Int,
        deltaX: Float,
        deltaY: Float,
        deltaZ: Float,
        vertexBuffer: VertexConsumer,
        pose: PoseStack.Pose,
        segmentStartFraction: Float,
        segmentEndFraction: Float,
        lineWidth: Float
    ) {
        val startX = deltaX * segmentStartFraction
        var startY = deltaY * (segmentStartFraction * segmentStartFraction + segmentStartFraction) * 0.5F + 0.25F
        val startZ = deltaZ * segmentStartFraction

        var deltaXSegment = deltaX * segmentEndFraction - startX
        var deltaYSegment = deltaY * (segmentEndFraction * segmentEndFraction + segmentEndFraction) * 0.5F + 0.25F - startY
        var deltaZSegment = deltaZ * segmentEndFraction - startZ

        val length = Mth.sqrt(deltaXSegment * deltaXSegment + deltaYSegment * deltaYSegment + deltaZSegment * deltaZSegment)
        deltaXSegment /= length
        deltaYSegment /= length
        deltaZSegment /= length

        val loweringFactor = 1.0F - segmentEndFraction
        val additionalLowering = loweringFactor * 0.1F
        startY -= additionalLowering

        vertexBuffer
            .addVertex(pose, startX, startY, startZ)
            .setColor(color)
            .setNormal(pose, deltaXSegment, deltaYSegment, deltaZSegment)
            .setLineWidth(lineWidth)
    }

    private fun percentage(value: Int, max: Int): Float {
        return value.toFloat() / max.toFloat()
    }
}
